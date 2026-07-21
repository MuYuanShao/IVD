package com.zlth.ivd.license.controller;

import com.zlth.ivd.common.annotation.AuditLog;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.util.ExportImportUtil;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.license.dto.CreateLicenseRequest;
import com.zlth.ivd.license.dto.LicenseQuery;
import com.zlth.ivd.license.dto.UpdateLicenseRequest;
import com.zlth.ivd.license.service.LicenseService;
import com.zlth.ivd.license.vo.LicenseDetailVO;
import com.zlth.ivd.license.vo.LicenseVO;
import com.zlth.ivd.common.annotation.RequireRole;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 证照管理 Controller
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/license")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;

    /** 分页查询证照 */
    @GetMapping
    public Result<PageVO<LicenseVO>> list(LicenseQuery query) {
        return Result.success(licenseService.listLicenses(query));
    }

    /** 证照明细 */
    @GetMapping("/{id}")
    public Result<LicenseDetailVO> detail(@PathVariable Long id) {
        return Result.success(licenseService.getDetail(id));
    }

    /** 新增证照 */
    @PostMapping
    @AuditLog(module = "证照管理", action = "新增证照")
    @RequireRole("SYSTEM_ADMIN")
    public Result<LicenseVO> create(@Valid @RequestBody CreateLicenseRequest request) {
        return Result.success(licenseService.create(request));
    }

    /** 编辑证照 */
    @PutMapping("/{id}")
    @AuditLog(module = "证照管理", action = "编辑证照")
    @RequireRole("SYSTEM_ADMIN")
    public Result<LicenseVO> update(@PathVariable Long id, @Valid @RequestBody UpdateLicenseRequest request) {
        return Result.success(licenseService.update(id, request));
    }

    /** 统计卡片 */
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        return Result.success(licenseService.getStats());
    }

    /** 供应商列表（供下拉选项） */
    @GetMapping("/suppliers")
    public Result<List<Map<String, Object>>> suppliers() {
        return Result.success(licenseService.getSuppliers());
    }

    /** 效期预警 */
    @GetMapping("/expiry-alerts")
    public Result<List<LicenseVO>> expiryAlerts(@RequestParam(defaultValue = "90") int days) {
        return Result.success(licenseService.getExpiryAlerts(days));
    }

    /** 上传附件 */
    @PostMapping("/{id}/upload")
    @AuditLog(module = "证照管理", action = "上传附件")
    @RequireRole("SYSTEM_ADMIN")
    public Result<String> uploadAttachment(@PathVariable Long id,
                                           @RequestParam("file") MultipartFile file) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        return Result.success(licenseService.uploadAttachment(id, file, operatorId));
    }

    /** 下载/预览附件 */
    @GetMapping("/{id}/file/{objectKey}")
    public void downloadAttachment(@PathVariable Long id,
                                   @PathVariable String objectKey,
                                   HttpServletResponse response) {
        licenseService.downloadAttachment(id, objectKey, response);
    }

    private static final List<String> EXPORT_HEADERS = List.of(
            "供应商", "证照类型", "证号", "发证机关", "发证日期", "有效期至", "状态", "剩余天数"
    );

    /** 导出证照（xlsx / csv） */
    @GetMapping("/export")
    public void export(@RequestParam(defaultValue = "xlsx") String format, HttpServletResponse response) {
        List<LicenseVO> list = licenseService.exportAll();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LicenseVO vo : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("供应商", vo.getSupplierName() != null ? vo.getSupplierName() : "");
            row.put("证照类型", vo.getLicenseTypeLabel() != null ? vo.getLicenseTypeLabel() : "");
            row.put("证号", vo.getCertNo() != null ? vo.getCertNo() : "");
            row.put("发证机关", vo.getIssuingAuthority() != null ? vo.getIssuingAuthority() : "");
            row.put("发证日期", vo.getIssueDate() != null ? vo.getIssueDate().toString() : "");
            row.put("有效期至", vo.getExpireDate() != null ? vo.getExpireDate().toString() : "");
            row.put("状态", vo.getStatusLabel() != null ? vo.getStatusLabel() : "");
            row.put("剩余天数", vo.getRemainingDays() != null ? vo.getRemainingDays().toString() : "");
            rows.add(row);
        }
        if ("csv".equalsIgnoreCase(format)) {
            ExportImportUtil.exportCsv(response, "证照列表", EXPORT_HEADERS, rows);
        } else {
            ExportImportUtil.exportExcel(response, "证照列表", EXPORT_HEADERS, rows);
        }
    }

    /** 批量导入证照 */
    @PostMapping("/import")
    @AuditLog(module = "证照管理", action = "批量导入证照")
    @RequireRole("SYSTEM_ADMIN")
    public Result<Map<String, Object>> importFile(@RequestParam("file") MultipartFile file) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        String operatorName = SecurityUtils.getCurrentUsername();
        List<Map<String, Object>> rawRows = ExportImportUtil.importFromFile(file, EXPORT_HEADERS);
        List<CreateLicenseRequest> requests = new ArrayList<>();
        for (Map<String, Object> row : rawRows) {
            CreateLicenseRequest req = new CreateLicenseRequest();
            req.setLicenseType(objToStr(row.get("证照类型")));
            req.setCertNo(objToStr(row.get("证号")));
            req.setIssuingAuthority(objToStr(row.get("发证机关")));
            req.setOperatorId(operatorId);
            req.setOperatorName(operatorName);
            if (req.getCertNo() != null && !req.getCertNo().isBlank()) {
                requests.add(req);
            }
        }
        int count = licenseService.importBatch(requests);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rawRows.size());
        result.put("success", count);
        result.put("skipped", rawRows.size() - count);
        return Result.success(result);
    }

    private String objToStr(Object o) {
        return o != null ? o.toString().trim() : null;
    }
}
