package com.zlth.ivd.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlth.ivd.catalog.TestApplication;
import com.zlth.ivd.catalog.dto.*;
import com.zlth.ivd.catalog.mapper.*;
import com.zlth.ivd.catalog.service.ReagentCatalogService;
import com.zlth.ivd.common.dto.ChangeLogQuery;
import com.zlth.ivd.common.enums.MaterialTypeEnum;
import com.zlth.ivd.common.exception.BizException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Transactional
class CatalogChangeRegressionTest {
    @org.springframework.boot.test.mock.mockito.MockBean com.zlth.ivd.common.service.CatalogDeletionGuard deletionGuard;
    @Autowired ReagentCatalogService service;
    @Autowired ReagentCatalogMapper catalog;
    @Autowired CatalogApprovalLogMapper approvals;
    @Autowired CatalogChangeLogMapper changes;
    @Autowired CatalogDraftMapper drafts;
    @Autowired com.zlth.ivd.catalog.service.FiveCodeMappingService mappings;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach void login() {
        var authentication = new UsernamePasswordAuthenticationToken(
                1L, "", List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")));
        authentication.setDetails("catalog-regression");
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }
    @Test void catalogImportCreatesValidTypesAndRejectsIncompleteRows() {
        String csv = String.join("\n",
                "HIS编码,通用名称,UDI-DI,注册证号,厂家,配送商,物料类型,规格,包装规格,审批状态,检测组别,创建时间,标称测试数",
                "QA-IMPORT-REAGENT,试剂,00123456789012,QA-CERT,厂家,配送商,试剂,100T,盒,已通过,,,100",
                "QA-IMPORT-CONSUMABLE,耗材,,,厂家,配送商,CONSUMABLE,,盒,,,,",
                "QA-IMPORT-UNKNOWN,未知,,,厂家,配送商,UNKNOWN,,,,,,",
                "QA-IMPORT-INCOMPLETE,试剂,,QA-CERT,厂家,配送商,REAGENT,,,,,,");
        var file = new org.springframework.mock.web.MockMultipartFile("file", "catalog.csv", "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var result = new ReagentCatalogController(service).importFile(file).getData();
        assertEquals(4, result.get("total"));
        assertEquals(2, result.get("success"));
        assertEquals(2, result.get("skipped"));
        assertEquals("00123456789012", jdbc.queryForObject("SELECT udi_di FROM reagent_catalog WHERE his_code='QA-IMPORT-REAGENT'", String.class));
        assertEquals(100, jdbc.queryForObject("SELECT tests_per_kit FROM reagent_catalog WHERE his_code='QA-IMPORT-REAGENT'", Integer.class));
        assertEquals("CONSUMABLE", jdbc.queryForObject("SELECT material_type FROM reagent_catalog WHERE his_code='QA-IMPORT-CONSUMABLE'", String.class));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT approval_status FROM reagent_catalog WHERE his_code='QA-IMPORT-REAGENT'", String.class));
    }
    @Test void exportIncludesMoreThanOneListPageWithoutDeletedRows() throws Exception {
        jdbc.update("INSERT INTO reagent_catalog(his_code,generic_name) "
                + "SELECT CONCAT('QA-EXPORT-', X), 'QA export' FROM SYSTEM_RANGE(1, 501)");
        jdbc.update("INSERT INTO reagent_catalog(his_code,generic_name,is_deleted) VALUES('QA-EXPORT-DELETED','QA deleted',1)");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        new ReagentCatalogController(service).export("csv", response);
        var rows = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).lines()
                .filter(line -> line.contains("QA-EXPORT-")).toList();
        assertEquals(501, rows.size());
        assertTrue(rows.stream().anyMatch(line -> line.contains("QA-EXPORT-501,")));
        assertFalse(rows.stream().anyMatch(line -> line.contains("QA-EXPORT-DELETED")));
    }

    @Test void oversizedExportFailsBeforeWritingResponse() {
        jdbc.update("INSERT INTO reagent_catalog(his_code,generic_name) "
                + "SELECT CONCAT('QA-EXPORT-LIMIT-', X), 'QA export' FROM SYSTEM_RANGE(1, 10001)");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var error = assertThrows(BizException.class, () -> new ReagentCatalogController(service).export("csv", response));
        assertEquals(40001, error.getErrorCode());
        assertEquals(0, response.getContentAsByteArray().length);
    }
    private Long create() {
        var request = new CreateReagentRequest();
        request.setHisCode("CAT-QA-" + UUID.randomUUID()); request.setGenericName("测试目录");
        request.setManufacturer("测试厂家"); request.setDistributor("测试配送商");
        request.setMaterialType(MaterialTypeEnum.REAGENT); request.setTestsPerKit(100);
        request.setRegistrationCertNo("QA-CERT"); request.setPricePerUnit(new BigDecimal("100.00"));
        request.setStabilityDaysAfterOpen(30);
        return service.create(request).getId();
    }
    private Long submit(Long id) {
        var request = new SubmitApprovalRequest(); request.setChangeReason("回归测试");
        request.setSubmitterId(1L); request.setSubmitterName("测试申请人"); service.submitApproval(id, request);
        return service.pagePendingApprovals(1, 500).getRecords().stream()
                .filter(row -> row.getReagentId().equals(id)).findFirst().orElseThrow().getId();
    }
    private void approve(Long logId) {
        var review = new ReviewRequest(); review.setReviewerId(2L); review.setReviewerName("测试审批人");
        service.approve(logId, review);
    }

    @Test void approvalRemovesObsoleteFiveCodeMappingOnlyAfterTypeChangeIsApproved() {
        Long id = create();
        approve(submit(id));
        assertNotNull(service.getById(id).getFiveCodeMapping());
        var update = new UpdateReagentRequest();
        update.setMaterialType(MaterialTypeEnum.CONSUMABLE);
        service.update(id, update);
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM five_code_mapping WHERE reagent_id=?", Long.class, id));
        assertEquals(MaterialTypeEnum.REAGENT, catalog.selectById(id).getMaterialType());
        approve(submit(id));
        assertEquals(MaterialTypeEnum.CONSUMABLE, catalog.selectById(id).getMaterialType());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM five_code_mapping WHERE reagent_id=?", Long.class, id));
        assertNull(service.getById(id).getFiveCodeMapping());
    }

    @Test void editPersistsDifferenceButDoesNotApplyUntilApproved() throws Exception {
        Long id = create(); approve(submit(id));
        var request = new UpdateReagentRequest(); request.setPricePerUnit(new BigDecimal("125.00"));
        service.update(id, request);
        assertEquals(0, catalog.selectById(id).getPricePerUnit().compareTo(new BigDecimal("100.00")));
        assertEquals("DRAFT", catalog.selectById(id).getApprovalStatus().getCode());
        assertEquals(0, service.getById(id).getPricePerUnit().compareTo(new BigDecimal("125.00")));
        var history = changes.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.zlth.ivd.catalog.entity.CatalogChangeLog>()
                .eq(com.zlth.ivd.catalog.entity.CatalogChangeLog::getReagentId,id)
                .eq(com.zlth.ivd.catalog.entity.CatalogChangeLog::getChangeType,"UPDATE"));
        var difference = json.readTree(history.get(0).getFieldChanges()).get("pricePerUnit");
        assertEquals(100, difference.get("old").intValue()); assertEquals(125, difference.get("new").intValue());
        assertEquals("catalog-regression", history.get(0).getOperatorName());
        approve(submit(id));
        assertEquals(0, catalog.selectById(id).getPricePerUnit().compareTo(new BigDecimal("125.00")));
    }

    @Test void pendingDraftIsFrozen() {
        Long id = create(); Long logId = submit(id);
        var request = new UpdateReagentRequest(); request.setGenericName("不允许覆盖");
        assertEquals(40900, assertThrows(BizException.class, () -> service.update(id,request)).getErrorCode());
        assertEquals("测试目录", catalog.selectById(id).getGenericName());
        assertNotEquals("{}", approvals.selectById(logId).getFieldChanges());
    }

    @Test void clearOptionalValueSurvivesApprovalAndOmittedValueIsPreserved() throws Exception {
        Long id = create(); approve(submit(id));
        service.update(id, json.readValue("{\"genericName\":\"名称更新\"}", UpdateReagentRequest.class));
        assertEquals(30, service.getById(id).getStabilityDaysAfterOpen());
        service.update(id, json.readValue("{\"stabilityDaysAfterOpen\":null,\"pricePerUnit\":null,\"leadTimeDays\":null}", UpdateReagentRequest.class));
        assertNull(service.getById(id).getStabilityDaysAfterOpen()); assertNull(service.getById(id).getPricePerUnit());
        assertEquals(7, service.getById(id).getLeadTimeDays());
        approve(submit(id));
        assertNull(catalog.selectById(id).getStabilityDaysAfterOpen()); assertNull(catalog.selectById(id).getPricePerUnit());
    }

    @Test void rejectsNegativePrice() {
        Long id = create(); var request = new UpdateReagentRequest(); request.setPricePerUnit(new BigDecimal("-1"));
        assertThrows(BizException.class, () -> service.update(id, request));
    }
    @Test void equivalentPriceDoesNotCreateSpuriousChange() {
        Long id = create(); approve(submit(id));
        var request = new UpdateReagentRequest(); request.setPricePerUnit(new BigDecimal("100"));
        service.update(id, request);
        assertEquals("APPROVED", catalog.selectById(id).getApprovalStatus().getCode());
        assertNull(drafts.selectById(id));
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void failedHistoryInsertRollsBackDraftAndStatus() {
        Long id = create(); approve(submit(id));
        var authentication = new UsernamePasswordAuthenticationToken(1L,"",List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")));
        authentication.setDetails("x".repeat(101)); SecurityContextHolder.getContext().setAuthentication(authentication);
        var request = new UpdateReagentRequest(); request.setPricePerUnit(new BigDecimal("150"));
        assertThrows(RuntimeException.class, () -> service.update(id,request));
        assertEquals("APPROVED", catalog.selectById(id).getApprovalStatus().getCode());
        assertEquals(0, catalog.selectById(id).getPricePerUnit().compareTo(new BigDecimal("100.00")));
        assertNull(drafts.selectById(id));
    }
    @Test void rejectsBlankRequiredField() {
        Long id = create(); var request = new UpdateReagentRequest(); request.setManufacturer(" ");
        assertThrows(BizException.class, () -> service.update(id, request));
    }
    @Test void rejectsClearingRequiredTestCount() {
        Long id = create(); var request = new UpdateReagentRequest(); request.setTestsPerKit(null);
        assertThrows(BizException.class, () -> service.update(id, request));
    }
    @Test void corruptLegacyApprovalCannotSilentlyApprove() {
        Long id = create(); Long logId = submit(id);
        var log = approvals.selectById(logId); log.setFieldChanges("{}"); approvals.updateById(log);
        assertThrows(BizException.class, () -> approve(logId));
        assertEquals("PENDING", catalog.selectById(id).getApprovalStatus().getCode());
    }
    @Test void deletionRequiresApprovalAndRechecksReferencesBeforeTakingEffect() {
        Long id = create();
        approve(submit(id));
        service.delete(id);
        assertNotNull(catalog.selectById(id));
        assertNotNull(mappings.getByReagentId(id));
        var pending = service.pagePendingApprovals(1, 500).getRecords().stream()
                .filter(row -> row.getReagentId().equals(id)).findFirst().orElseThrow();
        assertEquals("DELETE", pending.getChangeType());
        org.mockito.Mockito.doThrow(new BizException(40900, "存在新库存")).when(deletionGuard).requireDeletable(id);
        assertThrows(BizException.class, () -> approve(pending.getId()));
        assertNotNull(catalog.selectById(id));
        assertEquals("PENDING", approvals.selectById(pending.getId()).getReviewStatus());
        org.mockito.Mockito.doNothing().when(deletionGuard).requireDeletable(id);
        approve(pending.getId());
        assertNull(catalog.selectById(id));
        assertNull(mappings.getByReagentId(id));
    }
    @Test void rejectedDeletionKeepsApprovedDirectoryAndMapping() {
        Long id = create();
        approve(submit(id));
        service.delete(id);
        var pending = service.pagePendingApprovals(1, 500).getRecords().stream()
                .filter(row -> row.getReagentId().equals(id)).findFirst().orElseThrow();
        var review = new ReviewRequest();
        review.setReviewerId(2L);
        review.setReviewerName("测试审批人");
        review.setComment("保留目录");
        service.reject(pending.getId(), review);
        assertEquals("APPROVED", catalog.selectById(id).getApprovalStatus().getCode());
        assertNotNull(mappings.getByReagentId(id));
    }

    @Test void fiveCodeValuesChangeOnlyWhenTheDraftIsApproved() {
        Long id = create();
        approve(submit(id));
        var originalMapping = mappings.getByReagentId(id);
        var originalCatalog = catalog.selectById(id);
        var update = new UpdateReagentRequest();
        update.setUdiDi("00123456789012");
        update.setGtin("00698765432109");
        update.setInsuranceCode("QA-INS-NEW");
        update.setRegistrationCertNo("QA-CERT-NEW");
        service.update(id, update);
        Long approval = submit(id);
        var before = mappings.getByReagentId(id);
        assertEquals("QA-CERT", before.getNmpaRegNo());
        assertEquals(originalMapping.getUdiDi(), before.getUdiDi());
        assertEquals(originalMapping.getGtin(), before.getGtin());
        assertEquals(originalMapping.getInsuranceCode(), before.getInsuranceCode());
        assertEquals("QA-CERT", catalog.selectById(id).getRegistrationCertNo());
        assertEquals(originalCatalog.getUdiDi(), catalog.selectById(id).getUdiDi());

        approve(approval);
        var live = catalog.selectById(id);
        var mapped = mappings.getByReagentId(id);
        assertEquals("00123456789012", live.getUdiDi());
        assertEquals("00698765432109", live.getGtin());
        assertEquals("QA-INS-NEW", live.getInsuranceCode());
        assertEquals("QA-CERT-NEW", live.getRegistrationCertNo());
        assertEquals(live.getHisCode(), mapped.getHisCode());
        assertEquals(live.getUdiDi(), mapped.getUdiDi());
        assertEquals(live.getGtin(), mapped.getGtin());
        assertEquals(live.getInsuranceCode(), mapped.getInsuranceCode());
        assertEquals(live.getRegistrationCertNo(), mapped.getNmpaRegNo());
    }

    @Test void snapshotWithCorrectIdButAlteredContentCannotBeApproved() throws Exception {
        Long id = create();
        approve(submit(id));
        var update = new UpdateReagentRequest();
        update.setGenericName("已提交的目录名称");
        service.update(id, update);
        Long logId = submit(id);
        var log = approvals.selectById(logId);
        var snapshot = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(log.getFieldChanges());
        assertEquals(id.toString(), snapshot.path("id").asText());
        snapshot.put("genericName", "被篡改的目录名称");
        log.setFieldChanges(json.writeValueAsString(snapshot));
        approvals.updateById(log);
        String originalDraft = drafts.selectById(id).getSnapshot();

        assertEquals(40900, assertThrows(BizException.class, () -> approve(logId)).getErrorCode());
        assertEquals("PENDING", approvals.selectById(logId).getReviewStatus());
        assertEquals("PENDING", catalog.selectById(id).getApprovalStatus().getCode());
        assertEquals("测试目录", catalog.selectById(id).getGenericName());
        assertEquals(originalDraft, drafts.selectById(id).getSnapshot());
        assertEquals("QA-CERT", mappings.getByReagentId(id).getNmpaRegNo());
    }
    @Test void historyIncludesNewEditsAndLegacyApprovals() {
        Long id = create(); submit(id);
        var query = new ChangeLogQuery(); query.setKeyword(catalog.selectById(id).getHisCode());
        query.setEndDate(java.time.LocalDate.now().toString());
        var history = service.pageChangeLogs(query);
        assertEquals(2, history.getTotal()); assertEquals(2, history.getRecords().size());
    }

    @Test void boundedMergePrefixIsNotSilentlyTruncatedByGlobalFiveHundredRowLimit() {
        Long id = create(); var entity = catalog.selectById(id);
        for (int n = 0; n < 510; n++) {
            var row = new com.zlth.ivd.catalog.entity.CatalogChangeLog();
            row.setReagentId(id); row.setReagentName(entity.getGenericName()); row.setHisCode(entity.getHisCode());
            row.setChangeType("UPDATE"); row.setFieldChanges("{}"); row.setOperatorName("QA");
            row.setCreatedAt(java.time.LocalDateTime.now()); changes.insert(row);
        }
        var query = new ChangeLogQuery(); query.setKeyword(entity.getHisCode()); query.setPageSize(600);
        var result = service.pageChangeLogs(query);
        assertEquals(511,result.getTotal()); assertEquals(511,result.getRecords().size());
    }
}
