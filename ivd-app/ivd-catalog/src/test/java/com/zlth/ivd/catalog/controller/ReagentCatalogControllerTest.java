package com.zlth.ivd.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlth.ivd.catalog.TestApplication;
import com.zlth.ivd.catalog.dto.CreateReagentRequest;
import com.zlth.ivd.catalog.dto.ReviewRequest;
import com.zlth.ivd.catalog.dto.SubmitApprovalRequest;
import com.zlth.ivd.catalog.dto.UpdateReagentRequest;
import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.catalog.mapper.ReagentCatalogMapper;
import com.zlth.ivd.common.enums.MaterialTypeEnum;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 试剂目录 Controller MockMvc 集成测试
 * <p>
 * 使用 H2 内存数据库，覆盖 CRUD + 审批全流程。
 *
 * @author IVD Team
 * @since 2026-07-03
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReagentCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReagentCatalogMapper catalogMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private static Long createdReagentId;
    private static Long approvalLogId;

    @BeforeEach
    void authenticateAsSystemAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "catalog-test-admin",
                        null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    // ==================== 1. 分页查询 ====================

    @Test
    @Order(1)
    @DisplayName("1. 分页列表查询 — 返回 PageVO 结构")
    void shouldPageList() throws Exception {
        mockMvc.perform(get("/api/v1/catalog")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    // ==================== 2. 创建试剂 ====================

    @Test
    @Order(2)
    @DisplayName("2. 创建试剂成功 — 返回 DRAFT 状态 + 五码映射自动创建")
    void shouldCreateReagent() throws Exception {
        CreateReagentRequest req = new CreateReagentRequest();
        req.setHisCode("IVD-BIO-001");
        req.setGenericName("葡萄糖测定试剂盒");
        req.setMaterialType(MaterialTypeEnum.REAGENT);
        req.setManufacturer("罗氏诊断");
        req.setDistributor("测试配送商");
        req.setSpecification("R1:60mL×1 R2:20mL×1");
        req.setUnit("盒");
        req.setUdiDi("01234567890123");
        req.setRegistrationCertNo("国械注准20253123456");
        req.setTestsPerKit(800);
        req.setPricePerUnit(new BigDecimal("1250.00"));
        req.setLeadTimeDays(14);

        String resp = mockMvc.perform(post("/api/v1/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.approvalStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.materialTypeLabel").value("试剂"))
                .andExpect(jsonPath("$.data.fiveCodeMapping").exists())
                .andReturn().getResponse().getContentAsString();

        createdReagentId = objectMapper.readTree(resp).get("data").get("id").asLong();
    }

    // ==================== 3. 重复 hisCode 校验 ====================

    @Test
    @Order(3)
    @DisplayName("3. 重复 hisCode → 40001")
    void shouldRejectDuplicateHisCode() throws Exception {
        CreateReagentRequest req = new CreateReagentRequest();
        req.setHisCode("IVD-BIO-001");  // same as above
        req.setGenericName("重复试剂");
        req.setMaterialType(MaterialTypeEnum.REAGENT);
        req.setManufacturer("测试厂商");
        req.setDistributor("测试配送商");
        req.setUdiDi("11111111111111");
        req.setRegistrationCertNo("国械注准20259999999");
        req.setTestsPerKit(100);

        mockMvc.perform(post("/api/v1/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ==================== 4. 试剂缺少注册证号 → 40003 ====================

    @Test
    @Order(4)
    @DisplayName("4. 试剂缺少注册证号 → 40003")
    void shouldRejectMissingRegistrationCert() throws Exception {
        CreateReagentRequest req = new CreateReagentRequest();
        req.setHisCode("IVD-BIO-002");
        req.setGenericName("缺失注册证试剂");
        req.setMaterialType(MaterialTypeEnum.REAGENT);
        req.setManufacturer("测试厂商");
        req.setDistributor("测试配送商");
        req.setUdiDi("11111111111111");
        req.setTestsPerKit(100);
        // registrationCertNo 未设置，REAGENT 类型要求必填

        mockMvc.perform(post("/api/v1/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40003));
    }

    // ==================== 5. 物料类型过滤 ====================

    @Test
    @Order(5)
    @DisplayName("5. 按物料类型过滤")
    void shouldFilterByMaterialType() throws Exception {
        mockMvc.perform(get("/api/v1/catalog")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .param("materialType", "REAGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(greaterThan(0)));
    }

    // ==================== 6. 搜索文本 LIKE 匹配 ====================

    @Test
    @Order(6)
    @DisplayName("6. 搜索文本 LIKE 匹配")
    void shouldSearchByText() throws Exception {
        mockMvc.perform(get("/api/v1/catalog")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .param("genericName", "葡萄糖"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(greaterThan(0)));
    }

    // ==================== 7. 查询单个详情 ====================

    @Test
    @Order(7)
    @DisplayName("7. 查询单个试剂详情（含五码）")
    void shouldGetById() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/{id}", createdReagentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.hisCode").value("IVD-BIO-001"))
                .andExpect(jsonPath("$.data.fiveCodeMapping").exists());
    }

    // ==================== 8. 编辑试剂 ====================

    @Test
    @Order(8)
    @DisplayName("8. 编辑试剂成功")
    void shouldUpdateReagent() throws Exception {
        UpdateReagentRequest req = new UpdateReagentRequest();
        req.setGenericName("葡萄糖测定试剂盒（更新版）");
        req.setPricePerUnit(new BigDecimal("1380.00"));

        mockMvc.perform(put("/api/v1/catalog/{id}", createdReagentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.genericName").value("葡萄糖测定试剂盒（更新版）"));
    }

    // ==================== 9. 提交审批 ====================

    @Test
    @Order(9)
    @DisplayName("9. 提交审批 → 状态变 PENDING")
    void shouldSubmitApproval() throws Exception {
        SubmitApprovalRequest req = new SubmitApprovalRequest();
        req.setChangeReason("更新名称和价格");
        req.setSubmitterId(1001L);
        req.setSubmitterName("张三");

        String resp = mockMvc.perform(post("/api/v1/catalog/{id}/approval", createdReagentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        // 验证试剂状态变 PENDING
        mockMvc.perform(get("/api/v1/catalog/{id}", createdReagentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"));

        // 获取审批日志 ID 用于后续测试
        String pendingResp = mockMvc.perform(get("/api/v1/catalog/approval/pending")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].reviewStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        approvalLogId = objectMapper.readTree(pendingResp).get("data").get("records").get(0).get("id").asLong();
    }

    // ==================== 10. 重复提交审批 → 40901 ====================

    @Test
    @Order(10)
    @DisplayName("10. 重复提交审批 → 40901")
    void shouldRejectDuplicateApproval() throws Exception {
        SubmitApprovalRequest req = new SubmitApprovalRequest();
        req.setChangeReason("再次提交");
        req.setSubmitterId(1001L);
        req.setSubmitterName("张三");

        mockMvc.perform(post("/api/v1/catalog/{id}/approval", createdReagentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40901));
    }

    // ==================== 11. 待审批列表 ====================

    @Test
    @Order(11)
    @DisplayName("11. 待审批列表查询")
    void shouldListPendingApprovals() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/approval/pending")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(greaterThan(0)));
    }

    // ==================== 12. 审核通过 ====================

    @Test
    @Order(12)
    @DisplayName("12. 审批通过 → APPROVED")
    void shouldApprove() throws Exception {
        ReviewRequest req = new ReviewRequest();
        req.setReviewerId(2001L);
        req.setReviewerName("李主任");
        req.setComment("同意变更");

        mockMvc.perform(post("/api/v1/catalog/approval/{logId}/approve", approvalLogId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证试剂状态变 APPROVED
        mockMvc.perform(get("/api/v1/catalog/{id}", createdReagentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));
    }

    // ==================== 13. 审批通过后不可重复操作 ====================

    @Test
    @Order(13)
    @DisplayName("13. 已处理的审批 → 40005")
    void shouldRejectAlreadyProcessed() throws Exception {
        ReviewRequest req = new ReviewRequest();
        req.setReviewerId(2001L);
        req.setReviewerName("李主任");
        req.setComment("重复操作");

        mockMvc.perform(post("/api/v1/catalog/approval/{logId}/approve", approvalLogId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40005));
    }

    // ==================== 14. 驳回流程 ====================
    // 先创建第二个试剂，走提交→驳回流程

    private static Long reagent2Id;
    private static Long log2Id;

    @Test
    @Order(14)
    @DisplayName("14. 驳回流程 → REJECTED")
    void shouldRejectApproval() throws Exception {
        // 创建第二个试剂
        CreateReagentRequest createReq = new CreateReagentRequest();
        createReq.setHisCode("IVD-IMM-001");
        createReq.setGenericName("甲胎蛋白检测试剂盒");
        createReq.setMaterialType(MaterialTypeEnum.REAGENT);
        createReq.setManufacturer("雅培");
        createReq.setDistributor("测试配送商");
        createReq.setUdiDi("09876543210987");
        createReq.setRegistrationCertNo("国械注准20258888888");
        createReq.setTestsPerKit(500);

        String createResp = mockMvc.perform(post("/api/v1/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        reagent2Id = objectMapper.readTree(createResp).get("data").get("id").asLong();

        // 提交审批
        SubmitApprovalRequest submitReq = new SubmitApprovalRequest();
        submitReq.setChangeReason("新增试剂");
        submitReq.setSubmitterId(1002L);
        submitReq.setSubmitterName("王五");

        mockMvc.perform(post("/api/v1/catalog/{id}/approval", reagent2Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 获取审批 log ID
        String pendingResp = mockMvc.perform(get("/api/v1/catalog/approval/pending")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andReturn().getResponse().getContentAsString();
        var records = objectMapper.readTree(pendingResp).get("data").get("records");
        for (var r : records) {
            if (r.get("reagentId").asLong() == reagent2Id) {
                log2Id = r.get("id").asLong();
                break;
            }
        }

        // 驳回（含意见）
        ReviewRequest rejectReq = new ReviewRequest();
        rejectReq.setReviewerId(2001L);
        rejectReq.setReviewerName("李主任");
        rejectReq.setComment("注册证号格式有误，请核实后重新提交");

        mockMvc.perform(post("/api/v1/catalog/approval/{logId}/reject", log2Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证试剂状态变 REJECTED
        mockMvc.perform(get("/api/v1/catalog/{id}", reagent2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("REJECTED"));
    }

    // ==================== 15. 驳回无意见 → 40006 ====================
    // (注意：此时 reagent2 已是 REJECTED，需要重新提交并在 PENDING 状态驳回)
    // 为简化，我们直接测试刚提交的状态

    @Test
    @Order(15)
    @DisplayName("15. 驳回无意见 → 40006")
    void shouldRejectWithoutComment() throws Exception {
        // 创建第三个试剂并提交
        CreateReagentRequest createReq = new CreateReagentRequest();
        createReq.setHisCode("IVD-MIC-001");
        createReq.setGenericName("C反应蛋白检测试剂盒");
        createReq.setMaterialType(MaterialTypeEnum.REAGENT);
        createReq.setManufacturer("西门子");
        createReq.setDistributor("测试配送商");
        createReq.setUdiDi("11111111111111");
        createReq.setRegistrationCertNo("国械注准20257777777");
        createReq.setTestsPerKit(600);

        String resp = mockMvc.perform(post("/api/v1/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long r3Id = objectMapper.readTree(resp).get("data").get("id").asLong();

        SubmitApprovalRequest submitReq = new SubmitApprovalRequest();
        submitReq.setChangeReason("测试驳回");
        submitReq.setSubmitterId(1003L);
        submitReq.setSubmitterName("赵六");
        mockMvc.perform(post("/api/v1/catalog/{id}/approval", r3Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isOk());

        // 获取 log ID
        String pendingResp = mockMvc.perform(get("/api/v1/catalog/approval/pending")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andReturn().getResponse().getContentAsString();
        Long l3Id = null;
        for (var r : objectMapper.readTree(pendingResp).get("data").get("records")) {
            if (r.get("reagentId").asLong() == r3Id) {
                l3Id = r.get("id").asLong();
                break;
            }
        }

        // 驳回无意见
        ReviewRequest rejectReq = new ReviewRequest();
        rejectReq.setReviewerId(2001L);
        rejectReq.setReviewerName("李主任");
        rejectReq.setComment("");  // 空意见

        mockMvc.perform(post("/api/v1/catalog/approval/{logId}/reject", l3Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40006));
    }

    // ==================== 16. 软删除 ====================

    @Test
    @Order(16)
    @DisplayName("16. 软删除试剂")
    void shouldSoftDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/catalog/{id}", reagent2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证已删除（查询返回404）
        mockMvc.perform(get("/api/v1/catalog/{id}", reagent2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40007));
    }

    // ==================== 17. UDI 待补全列表 ====================

    @Test
    @Order(17)
    @DisplayName("17. UDI 待补全列表查询")
    void shouldListUdiBacklog() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/udi-backlog")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    // ==================== 18. 审批历史 ====================

    @Test
    @Order(18)
    @DisplayName("18. 审批历史查询")
    void shouldListApprovalHistory() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/approval/history")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.records").isArray());
    }
}
