package com.zlth.ivd.system.controller;

import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.system.dto.CatalogFieldOptionRequest;
import com.zlth.ivd.system.service.CatalogFieldOptionService;
import com.zlth.ivd.system.vo.CatalogFieldOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "试剂目录字段配置", description = "维护试剂目录下拉选项")
@RestController
@RequestMapping("/api/v1/system/catalog-field-options")
@RequiredArgsConstructor
public class CatalogFieldOptionController {
    private final CatalogFieldOptionService optionService;

    @GetMapping
    @Operation(summary = "查询字段配置项")
    public Result<List<CatalogFieldOptionVO>> list(
            @RequestParam(required = false) String fieldCode,
            @RequestParam(defaultValue = "true") boolean includeDisabled) {
        return Result.success(optionService.list(fieldCode, includeDisabled));
    }

    @GetMapping("/options")
    @Operation(summary = "获取试剂目录启用下拉项")
    public Result<Map<String, List<CatalogFieldOptionVO>>> options() {
        return Result.success(optionService.activeOptions());
    }

    @PostMapping
    @RequireRole({"SYSTEM_ADMIN", "LAB_DIRECTOR"})
    @Operation(summary = "新增字段配置项")
    public Result<CatalogFieldOptionVO> create(@Valid @RequestBody CatalogFieldOptionRequest request) {
        return Result.success(optionService.create(request));
    }

    @PutMapping("/{id}")
    @RequireRole({"SYSTEM_ADMIN", "LAB_DIRECTOR"})
    @Operation(summary = "编辑字段配置项")
    public Result<CatalogFieldOptionVO> update(@PathVariable Long id,
                                                @Valid @RequestBody CatalogFieldOptionRequest request) {
        return Result.success(optionService.update(id, request));
    }

    @PutMapping("/{id}/status")
    @RequireRole({"SYSTEM_ADMIN", "LAB_DIRECTOR"})
    @Operation(summary = "启停字段配置项")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        optionService.toggleStatus(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequireRole({"SYSTEM_ADMIN", "LAB_DIRECTOR"})
    @Operation(summary = "删除未使用字段配置项")
    public Result<Void> delete(@PathVariable Long id) {
        optionService.delete(id);
        return Result.success();
    }
}
