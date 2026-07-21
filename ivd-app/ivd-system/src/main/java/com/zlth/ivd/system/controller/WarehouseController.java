package com.zlth.ivd.system.controller;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.system.service.WarehouseService;
import com.zlth.ivd.system.vo.WarehouseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 库房管理 — M00-3
 *
 * @author IVD Team
 * @since 2026-07-13
 */
@Tag(name = "库房管理", description = "三级库房 CRUD、层级树、科室关联")
@RestController
@RequestMapping("/api/v1/system/warehouses")
@RequiredArgsConstructor
@RequireRole({"SYSTEM_ADMIN", "STOREKEEPER"})
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    @Operation(summary = "分页查询库房列表")
    public Result<PageVO<WarehouseVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer level) {
        return Result.success(warehouseService.list(page, pageSize, keyword, level));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询库房详情")
    public Result<WarehouseVO> getById(@PathVariable Long id) {
        return Result.success(warehouseService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增库房")
    public Result<WarehouseVO> create(@RequestBody Map<String, Object> body) {
        return Result.success(warehouseService.create(
                (String) body.get("name"),
                (String) body.get("code"),
                toInt(body.get("level")),
                toLong(body.get("parentId")),
                toLong(body.get("deptId")),
                (String) body.get("location"),
                (String) body.get("contactPerson"),
                (String) body.get("contactPhone"),
                toInt(body.get("sortOrder"))));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑库房")
    public Result<WarehouseVO> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(warehouseService.update(id,
                (String) body.get("name"),
                (String) body.get("code"),
                toInt(body.get("level")),
                toLong(body.get("parentId")),
                toLong(body.get("deptId")),
                (String) body.get("location"),
                (String) body.get("contactPerson"),
                (String) body.get("contactPhone"),
                toInt(body.get("sortOrder"))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除库房（校验无子库房）")
    public Result<Void> delete(@PathVariable Long id) {
        warehouseService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "启用/停用库房")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        warehouseService.toggleStatus(id);
        return Result.success();
    }

    @GetMapping("/tree")
    @Operation(summary = "库房树（启用状态）")
    public Result<List<WarehouseVO>> tree() {
        return Result.success(warehouseService.tree());
    }

    @GetMapping("/options")
    @Operation(summary = "库房下拉选项（按级别过滤）")
    public Result<List<WarehouseVO>> options(@RequestParam(required = false) Integer level) {
        return Result.success(warehouseService.options(level));
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
