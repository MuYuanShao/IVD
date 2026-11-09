package com.zlth.ivd.server.service;

import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.license.dto.*;
import com.zlth.ivd.license.service.LicenseExpiryPolicy;
import io.minio.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class LicenseG1IntegrationTest extends ResourceBoundaryIntegrationTest {
    private void references() {
        bootstrap.execute("CREATE TABLE supplier(id BIGINT PRIMARY KEY,institution_id BIGINT,supplier_code VARCHAR(64),name VARCHAR(100),short_name VARCHAR(100),contact_person VARCHAR(100),contact_phone VARCHAR(100),contact_email VARCHAR(100),address VARCHAR(100),is_active BOOLEAN,created_at TIMESTAMP,updated_at TIMESTAMP)");
        bootstrap.update("INSERT INTO supplier(id,institution_id,name,is_active) VALUES(1,1,'QA owned',TRUE),(2,2,'QA foreign',TRUE)");
        bootstrap.execute("CREATE TABLE license_change_log(id BIGINT PRIMARY KEY,institution_id BIGINT,license_id BIGINT,change_type VARCHAR(32),old_expire_date DATE,new_expire_date DATE,operator_id BIGINT,operator_name VARCHAR(64),note VARCHAR(4000),created_at TIMESTAMP)");
        bootstrap.execute("CREATE TABLE reagent_supplier_rel(id BIGINT PRIMARY KEY,institution_id BIGINT,reagent_id BIGINT,supplier_id BIGINT,is_default BOOLEAN,created_at TIMESTAMP)");
    }
    @Test void createValidatesSupplierInTheSelectedInstitution() {
        references();
        var request=new CreateLicenseRequest();request.setCertNo("QA-CREATE");request.setLicenseType("OPERATION");
        request.setIssueDate(LocalDate.now());request.setExpireDate(LocalDate.now().plusYears(1));
        as(admin,()->{
            for(Long supplier:java.util.List.of(2L,999L)){
                request.setSupplierId(supplier);
                assertEquals(40400,assertThrows(BizException.class,()->licenses.create(request)).getErrorCode());
            }
            request.setSupplierId(1L);
            assertEquals(1L,licenses.create(request).getSupplierId());
            return null;
        });
        assertEquals(3L,bootstrap.queryForObject("SELECT COUNT(*) FROM supplier_license",Long.class));
        assertEquals(1L,bootstrap.queryForObject("SELECT COUNT(*) FROM license_change_log",Long.class));
    }
    private ApproveLicenseRenewalRequest renewal() {
        var request=new ApproveLicenseRenewalRequest();request.setCertNo("QA-RENEW");
        request.setIssueDate(LocalDate.now());request.setExpireDate(LocalDate.now().plusYears(1));
        request.setAttachmentKey("known.pdf");request.setReason("QA checked renewal");
        return request;
    }
    @Test void renewalStorageFailureAndUnlistedProofDoNotUnfreeze() throws Exception {
        references();bootstrap.update("UPDATE supplier_license SET status='FROZEN' WHERE id=501");
        var request=renewal();request.setAttachmentKey("not-listed.pdf");
        as(admin,()->{denied(40001,()->licenses.approveRenewal(501L,request));return null;});
        verifyNoInteractions(minio);
        request.setAttachmentKey("known.pdf");
        when(minio.statObject(any(StatObjectArgs.class))).thenThrow(new java.io.IOException("QA unavailable"));
        as(admin,()->{denied(40001,()->licenses.approveRenewal(501L,request));return null;});
        assertEquals("FROZEN",bootstrap.queryForObject("SELECT status FROM supplier_license WHERE id=501",String.class));
        assertEquals(0L,bootstrap.queryForObject("SELECT COUNT(*) FROM license_change_log",Long.class));
    }
    @Test void approvedProofCannotBeDeletedOrReplacedButRemainsReadableMetadata() throws Exception {
        references();
        as(admin,()->licenses.approveRenewal(501L,renewal()));
        String before=bootstrap.queryForObject("SELECT attachments FROM supplier_license WHERE id=501",String.class);
        as(admin,()->{
            denied(40900,()->licenses.deleteAttachment(501L,"known.pdf",1000L));
            var file=new org.springframework.mock.web.MockMultipartFile("file","new.pdf","application/pdf",new byte[]{1});
            denied(40900,()->licenses.replaceAttachment(501L,"known.pdf",file,1000L));
            assertTrue(licenses.getDetail(501L).getAttachments().get(0).getProtectedEvidence());
            return null;
        });
        assertEquals(before,bootstrap.queryForObject("SELECT attachments FROM supplier_license WHERE id=501",String.class));
        verify(minio,never()).removeObject(any(RemoveObjectArgs.class));
    }
    @Test void relatedCatalogsUseOwnedRelationsAndServiceVisibility() {
        references();
        bootstrap.update("INSERT INTO reagent_supplier_rel(id,institution_id,reagent_id,supplier_id,is_default) VALUES(1,1,1,1,TRUE),(2,1,3,1,FALSE),(3,2,3,2,TRUE)");
        var catalog=context.getBean(com.zlth.ivd.catalog.service.ReagentCatalogService.class);
        var owned=new com.zlth.ivd.catalog.vo.ReagentCatalogVO();owned.setId(1L);owned.setGenericName("QA owned catalog");
        when(catalog.getById(1L)).thenReturn(owned);
        when(catalog.getById(3L)).thenThrow(new BizException(40400,"hidden"));
        as(admin,()->{
            var related=licenses.getDetail(501L).getRelatedReagents();
            assertEquals(1,related.size());assertEquals(1L,related.get(0).getReagentId());
            return null;
        });
    }
    @Test void expiryPolicyDistinguishesAttentionUrgencyAndFrozenRenewalState() {
        var today=LocalDate.now();
        assertEquals("ATTENTION",LicenseExpiryPolicy.reminderLevel(null,today.plusDays(90),today));
        assertEquals("EXPIRING",LicenseExpiryPolicy.reminderLevel(null,today.plusDays(30),today));
        assertEquals("URGENT",LicenseExpiryPolicy.reminderLevel(null,today.plusDays(7),today));
        assertEquals("OVERDUE",LicenseExpiryPolicy.reminderLevel(null,today,today));
        assertEquals(com.zlth.ivd.common.enums.LicenseStatusEnum.ACTIVE,LicenseExpiryPolicy.status(null,today.plusDays(31),today));
        assertEquals(com.zlth.ivd.common.enums.LicenseStatusEnum.EXPIRED,
                LicenseExpiryPolicy.status(com.zlth.ivd.common.enums.LicenseStatusEnum.EXPIRED,today.plusDays(31),today));
    }
}
