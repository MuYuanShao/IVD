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
    @Autowired ReagentCatalogService service;
    @Autowired ReagentCatalogMapper catalog;
    @Autowired CatalogApprovalLogMapper approvals;
    @Autowired CatalogChangeLogMapper changes;
    @Autowired CatalogDraftMapper drafts;
    @Autowired ObjectMapper json;

    @BeforeEach void login() {
        var authentication = new UsernamePasswordAuthenticationToken(
                1L, "", List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")));
        authentication.setDetails("catalog-regression");
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }
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
