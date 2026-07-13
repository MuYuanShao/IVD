package com.zlth.ivd.license.controller;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.util.ExportImportUtil;
import com.zlth.ivd.license.dto.CreateManufacturerRequest;
import com.zlth.ivd.license.dto.UpdateManufacturerRequest;
import com.zlth.ivd.license.service.ManufacturerService;
import com.zlth.ivd.license.vo.ManufacturerVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 厂家管理 Controller
 *
 * @author IVD Team
 * @since 2026-07-09
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/manufacturer")
@RequiredArgsConstructor
public class ManufacturerController {

    private final ManufacturerService manufacturerService;

    /** 分页查询 */
    @GetMapping
    public Result<PageVO<ManufacturerVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(manufacturerService.list(page, pageSize, keyword));
    }

    /** 明细 */
    @GetMapping("/{id}")
    public Result<ManufacturerVO> detail(@PathVariable Long id) {
        return Result.success(manufacturerService.getById(id));
    }

    /** 新增 */
    @PostMapping
    public Result<ManufacturerVO> create(@Valid @RequestBody CreateManufacturerRequest request) {
        return Result.success(manufacturerService.create(request));
    }

    /** 编辑 */
    @PutMapping("/{id}")
    public Result<ManufacturerVO> update(@PathVariable Long id, @Valid @RequestBody UpdateManufacturerRequest request) {
        return Result.success(manufacturerService.update(id, request));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        manufacturerService.delete(id);
        return Result.success();
    }

    /** 启用/停用 */
    @PutMapping("/{id}/toggle")
    public Result<Void> toggleActive(@PathVariable Long id) {
        manufacturerService.toggleActive(id);
        return Result.success();
    }

    /** 下拉选项 */
    @GetMapping("/options")
    public Result<List<ManufacturerVO>> options(@RequestParam(required = false) String keyword) {
        return Result.success(manufacturerService.options(keyword));
    }

    private static final List<String> EXPORT_HEADERS = List.of(
            "厂家名称", "简称", "联系人", "联系电话", "邮箱", "地址", "状态", "创建时间"
    );

    /** 导出厂家（xlsx / csv） */
    @GetMapping("/export")
    public void export(@RequestParam(defaultValue = "xlsx") String format, HttpServletResponse response) {
        List<ManufacturerVO> list = manufacturerService.exportAll();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ManufacturerVO vo : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("厂家名称", vo.getName());
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
            ExportImportUtil.exportCsv(response, "厂家列表", EXPORT_HEADERS, rows);
        } else {
            ExportImportUtil.exportExcel(response, "厂家列表", EXPORT_HEADERS, rows);
        }
    }

    /** 批量导入厂家 */
    @PostMapping("/import")
    public Result<Map<String, Object>> importFile(@RequestParam("file") MultipartFile file) {
        List<Map<String, Object>> rawRows = ExportImportUtil.importFromFile(file, EXPORT_HEADERS);
        List<CreateManufacturerRequest> requests = new ArrayList<>();
        for (Map<String, Object> row : rawRows) {
            CreateManufacturerRequest req = new CreateManufacturerRequest();
            req.setName(objToStr(row.get("厂家名称")));
            req.setShortName(objToStr(row.get("简称")));
            req.setContactPerson(objToStr(row.get("联系人")));
            req.setContactPhone(objToStr(row.get("联系电话")));
            req.setContactEmail(objToStr(row.get("邮箱")));
            req.setAddress(objToStr(row.get("地址")));
            if (req.getName() != null && !req.getName().isBlank()) {
                requests.add(req);
            }
        }
        int count = manufacturerService.importBatch(requests);
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
