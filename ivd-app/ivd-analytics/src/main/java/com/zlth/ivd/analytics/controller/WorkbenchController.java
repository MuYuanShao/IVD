package com.zlth.ivd.analytics.controller;

import com.zlth.ivd.analytics.service.WorkbenchService;
import com.zlth.ivd.analytics.vo.WorkbenchVO;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics/workbench")
@RequiredArgsConstructor
public class WorkbenchController {
    private final WorkbenchService service;
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public Result<Void> invalidParameter() { return Result.badRequest("分页参数必须为整数"); }
    @GetMapping public Result<WorkbenchVO> overview() { return Result.success(service.overview(SecurityUtils.getCurrentUserId())); }
    @GetMapping("/products") public Result<PageVO<WorkbenchVO.Product>> products(
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="5") int pageSize,@RequestParam(defaultValue="") String keyword) {
        return Result.success(service.products(SecurityUtils.getCurrentUserId(),page,pageSize,keyword));
    }
}
