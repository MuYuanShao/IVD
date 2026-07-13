package com.zlth.ivd.system.controller;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.system.service.DeptService;
import com.zlth.ivd.system.vo.DeptVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 科室管理 — M00-2
 *
 * @author IVD Team
 * @since 2026-07-13
 */
@Tag(name = "科室管理", description = "检验科室 CRUD、层级树、关联库房")
@RestController
@RequestMapping("/api/v1/system/depts")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    @GetMapping
    @Operation(summary = "分页查询科室列表")
    public Result<PageVO<DeptVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(deptService.list(page, pageSize, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询科室详情")
    public Result<DeptVO> getById(@PathVariable Long id) {
        return Result.success(deptService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增科室（自动创建关联库房）")
    public Result<DeptVO> create(@RequestBody Map<String, Object> body) {
        return Result.success(deptService.create(
                (String) body.get("deptName"),
                (String) body.get("deptCode"),
                toLong(body.get("parentId")),
                toInt(body.get("sortOrder")),
                (String) body.get("contactPerson"),
                (String) body.get("contactPhone"),
                (String) body.get("remark")));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑科室")
    public Result<DeptVO> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(deptService.update(id,
                (String) body.get("deptName"),
                (String) body.get("deptCode"),
                toLong(body.get("parentId")),
                toInt(body.get("sortOrder")),
                (String) body.get("contactPerson"),
                (String) body.get("contactPhone"),
                (String) body.get("remark")));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除科室（逻辑删除，校验无子科室）")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "启用/停用科室")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        deptService.toggleStatus(id);
        return Result.success();
    }

    @GetMapping("/tree")
    @Operation(summary = "科室树（启用状态）")
    public Result<List<DeptVO>> tree() {
        return Result.success(deptService.tree());
    }

    @GetMapping("/options")
    @Operation(summary = "科室下拉选项")
    public Result<List<DeptVO>> options() {
        return Result.success(deptService.options());
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
