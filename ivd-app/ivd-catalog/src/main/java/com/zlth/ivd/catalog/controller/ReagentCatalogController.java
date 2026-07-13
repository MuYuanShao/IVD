package com.zlth.ivd.catalog.controller;

import com.zlth.ivd.catalog.dto.CreateReagentRequest;
import com.zlth.ivd.catalog.dto.ReagentCatalogQuery;
import com.zlth.ivd.catalog.dto.ReviewRequest;
import com.zlth.ivd.catalog.dto.SubmitApprovalRequest;
import com.zlth.ivd.catalog.dto.UpdateReagentRequest;
import com.zlth.ivd.catalog.entity.CatalogApprovalLog;
import com.zlth.ivd.catalog.entity.UdiBacklog;
import com.zlth.ivd.catalog.service.ReagentCatalogService;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.catalog.vo.ReagentCatalogVO;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.util.ExportImportUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * M01 试剂目录管理 Controller
 * <p>
 * REST URL: /api/v1/catalog
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class ReagentCatalogController {

    private final ReagentCatalogService catalogService;

    // ──────────────────────────────────────────────
    //  查询
    // ──────────────────────────────────────────────

    /** 分页查询试剂目录列表（多条件组合） */
    @GetMapping
    public Result<PageVO<ReagentCatalogVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String hisCode,
            @RequestParam(required = false) String genericName,
            @RequestParam(required = false) String udiDi,
            @RequestParam(required = false) String registrationCertNo,
            @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false) String distributor,
            @RequestParam(required = false) String materialType,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String testGroups) {

        ReagentCatalogQuery query = new ReagentCatalogQuery();
        query.setHisCode(hisCode);
        query.setGenericName(genericName);
        query.setUdiDi(udiDi);
        query.setRegistrationCertNo(registrationCertNo);
        query.setManufacturer(manufacturer);
        query.setDistributor(distributor);
        query.setMaterialType(materialType);
        query.setApprovalStatus(approvalStatus);
        query.setTestGroups(testGroups);

        log.debug("试剂目录分页查询: page={}, pageSize={}", page, pageSize);
        return Result.success(catalogService.pageList(query, page, pageSize));
    }

    /** 查询单个试剂详情（含五码映射） */
    @GetMapping("/{id}")
    public Result<ReagentCatalogVO> getById(@PathVariable Long id) {
        log.debug("查询试剂详情: id={}", id);
        return Result.success(catalogService.getById(id));
    }

    // ──────────────────────────────────────────────
    //  新增 / 编辑 / 删除
    // ──────────────────────────────────────────────

    /** 新增试剂 */
    @PostMapping
    public Result<ReagentCatalogVO> create(@Valid @RequestBody CreateReagentRequest request) {
        log.info("新增试剂: hisCode={}, genericName={}, materialType={}",
                request.getHisCode(), request.getGenericName(),
                request.getMaterialType() != null ? request.getMaterialType().getCode() : null);
        return Result.success(catalogService.create(request));
    }

    /** 编辑试剂 */
    @PutMapping("/{id}")
    public Result<ReagentCatalogVO> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateReagentRequest request) {
        log.info("编辑试剂: id={}", id);
        return Result.success(catalogService.update(id, request));
    }

    /** 软删除试剂 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("删除试剂: id={}", id);
        catalogService.delete(id);
        return Result.success();
    }

    // ──────────────────────────────────────────────
    //  审批
    // ──────────────────────────────────────────────

    /** 提交审批 */
    @PostMapping("/{id}/approval")
    public Result<Void> submitApproval(@PathVariable Long id,
                                        @Valid @RequestBody SubmitApprovalRequest request) {
        // 优先从认证上下文获取操作人身份
        Long authUserId = SecurityUtils.getCurrentUserIdOrNull();
        if (authUserId != null) {
            request.setSubmitterId(authUserId);
            request.setSubmitterName(SecurityUtils.getCurrentUsername());
        }
        log.info("提交审批: reagentId={}, submitterId={}", id, request.getSubmitterId());
        catalogService.submitApproval(id, request);
        return Result.success();
    }

    /** 审批通过 */
    @PostMapping("/approval/{logId}/approve")
    public Result<Void> approve(@PathVariable Long logId,
                                 @Valid @RequestBody ReviewRequest request) {
        Long authUserId = SecurityUtils.getCurrentUserIdOrNull();
        if (authUserId != null) {
            request.setReviewerId(authUserId);
            request.setReviewerName(SecurityUtils.getCurrentUsername());
        }
        log.info("审批通过: logId={}, reviewerId={}", logId, request.getReviewerId());
        catalogService.approve(logId, request);
        return Result.success();
    }

    /** 审批驳回 */
    @PostMapping("/approval/{logId}/reject")
    public Result<Void> reject(@PathVariable Long logId,
                                @Valid @RequestBody ReviewRequest request) {
        Long authUserId = SecurityUtils.getCurrentUserIdOrNull();
        if (authUserId != null) {
            request.setReviewerId(authUserId);
            request.setReviewerName(SecurityUtils.getCurrentUsername());
        }
        log.info("审批驳回: logId={}, reviewerId={}", logId, request.getReviewerId());
        catalogService.reject(logId, request);
        return Result.success();
    }

    /** 分页查询待审批列表 */
    @GetMapping("/approval/pending")
    public Result<PageVO<CatalogApprovalLog>> pendingApprovals(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.debug("查询待审批列表: page={}", page);
        return Result.success(catalogService.pagePendingApprovals(page, pageSize));
    }

    /** 分页查询审批历史 */
    @GetMapping("/approval/history")
    public Result<PageVO<CatalogApprovalLog>> approvalHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.debug("查询审批历史: page={}", page);
        return Result.success(catalogService.pageApprovalHistory(page, pageSize));
    }

    // ──────────────────────────────────────────────
    //  UDI 待补全
    // ──────────────────────────────────────────────

    /** 分页查询 UDI 待补全列表 */
    @GetMapping("/udi-backlog")
    public Result<PageVO<UdiBacklog>> udiBacklog(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String impactLevel) {
        log.debug("查询UDI待补全列表: page={}, impactLevel={}", page, impactLevel);
        return Result.success(catalogService.pageUdiBacklog(page, pageSize, impactLevel));
    }

    // ──────────────────────────────────────────────
    //  导入导出
    // ──────────────────────────────────────────────

    private static final List<String> CATALOG_EXPORT_HEADERS = List.of(
            "HIS编码", "通用名称", "UDI-DI", "注册证号", "厂家", "配送商", "物料类型", "规格", "包装规格",
            "最小库存", "审批状态", "检测组别", "创建时间"
    );

    /** 导出试剂目录（xlsx / csv） */
    @GetMapping("/export")
    public void export(@RequestParam(defaultValue = "xlsx") String format, HttpServletResponse response) {
        PageVO<ReagentCatalogVO> result = catalogService.pageList(new ReagentCatalogQuery(), 1, 10000);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ReagentCatalogVO vo : result.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("HIS编码", vo.getHisCode() != null ? vo.getHisCode() : "");
            row.put("通用名称", vo.getGenericName() != null ? vo.getGenericName() : "");
            row.put("UDI-DI", vo.getUdiDi() != null ? vo.getUdiDi() : "");
            row.put("注册证号", vo.getRegistrationCertNo() != null ? vo.getRegistrationCertNo() : "");
            row.put("厂家", vo.getManufacturer() != null ? vo.getManufacturer() : "");
            row.put("配送商", vo.getDistributor() != null ? vo.getDistributor() : "");
            row.put("物料类型", vo.getMaterialTypeLabel() != null ? vo.getMaterialTypeLabel() : "");
            row.put("规格", vo.getSpecification() != null ? vo.getSpecification() : "");
            row.put("包装规格", vo.getUnit() != null ? vo.getUnit() : "");
            row.put("最小库存", vo.getMinStockLevel() != null ? vo.getMinStockLevel().toString() : "");
            row.put("审批状态", vo.getApprovalStatusLabel() != null ? vo.getApprovalStatusLabel() : "");
            row.put("检测组别", vo.getTestGroups() != null ? vo.getTestGroups() : "");
            row.put("创建时间", vo.getCreatedAt() != null ? vo.getCreatedAt().toString() : "");
            rows.add(row);
        }
        if ("csv".equalsIgnoreCase(format)) {
            ExportImportUtil.exportCsv(response, "试剂目录", CATALOG_EXPORT_HEADERS, rows);
        } else {
            ExportImportUtil.exportExcel(response, "试剂目录", CATALOG_EXPORT_HEADERS, rows);
        }
    }

    /** 批量导入试剂目录 */
    @PostMapping("/import")
    public Result<Map<String, Object>> importFile(@RequestParam("file") MultipartFile file) {
        List<Map<String, Object>> rawRows = ExportImportUtil.importFromFile(file, CATALOG_EXPORT_HEADERS);
        int success = 0, skipped = 0;
        for (Map<String, Object> row : rawRows) {
            try {
                CreateReagentRequest req = new CreateReagentRequest();
                req.setHisCode(objToStr(row.get("HIS编码")));
                req.setGenericName(objToStr(row.get("通用名称")));
                req.setUdiDi(objToStr(row.get("UDI-DI")));
                req.setRegistrationCertNo(objToStr(row.get("注册证号")));
                req.setManufacturer(objToStr(row.get("厂家")));
                req.setDistributor(objToStr(row.get("配送商")));
                req.setSpecification(objToStr(row.get("规格")));
                req.setUnit(objToStr(row.get("包装规格")));
                String minStock = objToStr(row.get("最小库存"));
                if (minStock != null && !minStock.isBlank()) {
                    req.setMinStockLevel(new java.math.BigDecimal(minStock));
                }
                req.setTestGroups(objToStr(row.get("检测组别")));
                if (req.getGenericName() != null && !req.getGenericName().isBlank()) {
                    catalogService.create(req);
                    success++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.warn("导入试剂失败: {}", e.getMessage());
                skipped++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rawRows.size());
        result.put("success", success);
        result.put("skipped", skipped);
        return Result.success(result);
    }

    private String objToStr(Object o) {
        return o != null ? o.toString().trim() : null;
    }
}
