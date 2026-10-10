package com.zlth.ivd.laboratory.controller;
import com.zlth.ivd.common.annotation.*;
import com.zlth.ivd.common.dto.*;
import com.zlth.ivd.laboratory.dto.*;
import com.zlth.ivd.laboratory.service.LabCatalogService;
import com.zlth.ivd.laboratory.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/lab") @RequiredArgsConstructor
@ConditionalOnProperty(name="ivd.isolation.enabled",havingValue="true")
@RequireRole({"SYSTEM_ADMIN","LAB_DIRECTOR","LAB_DOCTOR","LAB_TECH"})
public class LabCatalogController {
    private final LabCatalogService service;
    @GetMapping("/devices/options") public Result<java.util.List<DeviceOptionVO>> deviceOptions(@RequestParam(required=false) String keyword){return Result.success(service.deviceOptions(keyword));}
    @GetMapping("/items") public Result<PageVO<TestItemVO>> items(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword){return Result.success(service.items(page,pageSize,keyword));}
    @GetMapping("/items/{id}") public Result<TestItemVO> item(@PathVariable Long id){return Result.success(service.item(id));}
    @PostMapping("/items") @RequireRole({"SYSTEM_ADMIN","LAB_DIRECTOR"}) @AuditLog(module="检验项目",action="新增")
    public Result<TestItemVO> createItem(@Valid @RequestBody TestItemRequest request){return Result.success(service.createItem(request));}
    @PutMapping("/items/{id}") @RequireRole({"SYSTEM_ADMIN","LAB_DIRECTOR"}) @AuditLog(module="检验项目",action="编辑")
    public Result<TestItemVO> updateItem(@PathVariable Long id,@Valid @RequestBody TestItemRequest request){return Result.success(service.updateItem(id,request));}
    @GetMapping("/intervals") public Result<PageVO<IntervalVO>> intervals(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) Long itemId,@RequestParam(required=false) String kind){return Result.success(service.intervals(page,pageSize,itemId,kind));}
    @GetMapping("/intervals/{id}") public Result<IntervalVO> interval(@PathVariable Long id){return Result.success(service.interval(id));}
    @PostMapping("/intervals") @RequireRole({"SYSTEM_ADMIN","LAB_DIRECTOR"}) @AuditLog(module="检验区间",action="新增")
    public Result<IntervalVO> createInterval(@Valid @RequestBody IntervalRequest request){return Result.success(service.createInterval(request));}
    @PutMapping("/intervals/{id}") @RequireRole({"SYSTEM_ADMIN","LAB_DIRECTOR"}) @AuditLog(module="检验区间",action="编辑")
    public Result<IntervalVO> updateInterval(@PathVariable Long id,@Valid @RequestBody IntervalRequest request){return Result.success(service.updateInterval(id,request));}
    @PostMapping("/intervals/evaluate") @RequireRole({"LAB_DIRECTOR","LAB_DOCTOR","LAB_TECH"})
    public Result<IntervalEvaluationVO> evaluate(@Valid @RequestBody IntervalEvaluationRequest request){return Result.success(service.evaluate(request));}
}
