package com.zlth.ivd.inventory.controller;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.inventory.service.RemainingBatchService;
import com.zlth.ivd.inventory.vo.RemainingBatchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory/remaining-batches")
@RequireRole({"SYSTEM_ADMIN","STOREKEEPER","FINANCE","LAB_DIRECTOR","LAB_DOCTOR","LAB_TECH","DEPT_ADMIN"})
public class RemainingBatchController {
    private final RemainingBatchService service;
    @GetMapping public Result<PageVO<RemainingBatchVO>> list(
            @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) String keyword,
            @RequestParam(required=false) Integer level) {
        return Result.success(service.list(page,pageSize,keyword,level));
    }
}
