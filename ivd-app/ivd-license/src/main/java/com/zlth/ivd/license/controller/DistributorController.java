package com.zlth.ivd.license.controller;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.util.ExportImportUtil;
import com.zlth.ivd.license.dto.CreateDistributorRequest;
import com.zlth.ivd.license.dto.UpdateDistributorRequest;
import com.zlth.ivd.license.service.DistributorService;
import com.zlth.ivd.license.vo.DistributorVO;
import com.zlth.ivd.common.annotation.RequireRole;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 配送商管理 Controller
 *
 * @author IVD Team
 * @since 2026-07-09
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/distributor")
@RequiredArgsConstructor
public class DistributorController {

    private final DistributorService distributorService;

    /** 分页查询 */
    @GetMapping
    public Result<PageVO<DistributorVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(distributorService.list(page, pageSize, keyword));
    }

    /** 明细 */
    @GetMapping("/{id}")
    public Result<DistributorVO> detail(@PathVariable Long id) {
        return Result.success(distributorService.getById(id));
    }

    /** 新增 */
    @PostMapping
    @RequireRole("SYSTEM_ADMIN")
    public Result<DistributorVO> create(@Valid @RequestBody CreateDistributorRequest request) {
        return Result.success(distributorService.create(request));
    }

    /** 编辑 */
    @PutMapping("/{id}")
    @RequireRole("SYSTEM_ADMIN")
    public Result<DistributorVO> update(@PathVariable Long id, @Valid @RequestBody UpdateDistributorRequest request) {
        return Result.success(distributorService.update(id, request));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    @RequireRole("SYSTEM_ADMIN")
    public Result<Void> delete(@PathVariable Long id) {
        distributorService.delete(id);
        return Result.success();
    }

    /** 启用/停用 */
    @PutMapping("/{id}/toggle")
    @RequireRole("SYSTEM_ADMIN")
    public Result<Void> toggleActive(@PathVariable Long id) {
        distributorService.toggleActive(id);
        return Result.success();
    }

    /** 下拉选项 */
    @GetMapping("/options")
    public Result<List<DistributorVO>> options(@RequestParam(required = false) String keyword) {
        return Result.success(distributorService.options(keyword));
    }

    private static final List<String> EXPORT_HEADERS = List.of(
            "配送商名称", "简称", "联系人", "联系电话", "邮箱", "地址", "状态", "创建时间"
    );

    /** 导出配送商（xlsx / csv） */
    @GetMapping("/export")
    public void export(@RequestParam(defaultValue = "xlsx") String format, HttpServletResponse response) {
        List<DistributorVO> list = distributorService.exportAll();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DistributorVO vo : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("配送商名称", vo.getName());
            row.put("简称", vo.getShortName() != null ? vo.getShortName() : "");
            row.put("联系人", vo.getContactPerson() != null ? vo.getContactPerson() : "");
            row.put("联系电话", vo.getContactPhone() != null ? vo.getContactPhone() : "");
            row.put("邮箱", vo.getContactEmail() != null ? vo.getContactEmail() : "");
            row.put("地址", vo.getAddress() != null ? vo.getAddress() : "");
            row.put("状态", Boolean.TRUE.equals(vo.getIsActive()) ? "启用" : "停用");
            row.put("创建时间", vo.getCreatedAt() != null ? vo.getCreatedAt().toString() : "");
            rows.add(row);
        }
        if ("csv".equalsIgnoreCase(format)) {
            ExportImportUtil.exportCsv(response, "配送商列表", EXPORT_HEADERS, rows);
        } else {
            ExportImportUtil.exportExcel(response, "配送商列表", EXPORT_HEADERS, rows);
        }
    }

    /** 批量导入配送商 */
    @PostMapping("/import")
    @RequireRole("SYSTEM_ADMIN")
    public Result<Map<String, Object>> importFile(@RequestParam("file") MultipartFile file) {
        List<Map<String, Object>> rawRows = ExportImportUtil.importFromFile(file, EXPORT_HEADERS);
        List<CreateDistributorRequest> requests = new ArrayList<>();
        for (Map<String, Object> row : rawRows) {
            CreateDistributorRequest req = new CreateDistributorRequest();
            req.setName(objToStr(row.get("配送商名称")));
            req.setShortName(objToStr(row.get("简称")));
            req.setContactPerson(objToStr(row.get("联系人")));
            req.setContactPhone(objToStr(row.get("联系电话")));
            req.setContactEmail(objToStr(row.get("邮箱")));
            req.setAddress(objToStr(row.get("地址")));
            if (req.getName() != null && !req.getName().isBlank()) {
                requests.add(req);
            }
        }
        int count = distributorService.importBatch(requests);
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
