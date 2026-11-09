package com.zlth.ivd.procurement.controller;

import com.zlth.ivd.common.annotation.AuditLog;
import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.procurement.dto.CreatePurchasePlanRequest;
import com.zlth.ivd.procurement.dto.PurchasePlanReviewRequest;
import com.zlth.ivd.procurement.service.PurchasePlanService;
import com.zlth.ivd.procurement.vo.PurchasePlanVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/procurement/plans")
@RequiredArgsConstructor
public class PurchasePlanController {
    private final PurchasePlanService plans;

    @GetMapping
    @RequireRole({"SYSTEM_ADMIN","PURCHASER","STOREKEEPER","LAB_DIRECTOR","FINANCE"})
    public Result<PageVO<PurchasePlanVO>> list(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String status,
            @RequestParam(required=false) String keyword) {
        return Result.success(plans.list(page,pageSize,status,keyword));
    }
    @GetMapping("/{id}")
    @RequireRole({"SYSTEM_ADMIN","PURCHASER","STOREKEEPER","LAB_DIRECTOR","FINANCE"})
    public Result<PurchasePlanVO> get(@PathVariable Long id){return Result.success(plans.get(id));}
    @PostMapping
    @RequireRole({"SYSTEM_ADMIN","PURCHASER","STOREKEEPER"})
    @AuditLog(module="采购计划",action="创建采购计划")
    public Result<PurchasePlanVO> create(@Valid @RequestBody CreatePurchasePlanRequest request){return Result.success(plans.create(request));}
    @PutMapping("/{id}")
    @RequireRole({"SYSTEM_ADMIN","PURCHASER","STOREKEEPER"})
    @AuditLog(module="采购计划",action="编辑采购计划")
    public Result<PurchasePlanVO> update(@PathVariable Long id,@Valid @RequestBody CreatePurchasePlanRequest request){return Result.success(plans.update(id,request));}
    @PostMapping("/{id}/submit")
    @RequireRole({"SYSTEM_ADMIN","PURCHASER","STOREKEEPER"})
    @AuditLog(module="采购计划",action="提交采购计划")
    public Result<PurchasePlanVO> submit(@PathVariable Long id){return Result.success(plans.submit(id));}
    @PostMapping("/{id}/approve")
    @RequireRole({"SYSTEM_ADMIN","LAB_DIRECTOR"})
    @AuditLog(module="采购计划",action="批准采购计划")
    public Result<PurchasePlanVO> approve(@PathVariable Long id,@Valid @RequestBody(required=false) PurchasePlanReviewRequest request){
        return Result.success(plans.approve(id,request==null?null:request.getComment()));
    }
    @PostMapping("/{id}/reject")
    @RequireRole({"SYSTEM_ADMIN","LAB_DIRECTOR"})
    @AuditLog(module="采购计划",action="驳回采购计划")
    public Result<PurchasePlanVO> reject(@PathVariable Long id,@Valid @RequestBody PurchasePlanReviewRequest request){
        return Result.success(plans.reject(id,request.getComment()));
    }
    @PostMapping("/{id}/convert")
    @RequireRole({"SYSTEM_ADMIN","PURCHASER","STOREKEEPER"})
    @AuditLog(module="采购计划",action="批准计划转采购订单")
    public Result<PurchasePlanVO> convert(@PathVariable Long id){return Result.success(plans.convert(id));}
}
