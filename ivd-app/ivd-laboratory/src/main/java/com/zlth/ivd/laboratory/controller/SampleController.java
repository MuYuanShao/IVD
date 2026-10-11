package com.zlth.ivd.laboratory.controller;
import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.common.dto.*;
import com.zlth.ivd.laboratory.dto.*;
import com.zlth.ivd.laboratory.service.SampleService;
import com.zlth.ivd.laboratory.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/lab/samples") @RequiredArgsConstructor
@ConditionalOnExpression("${ivd.isolation.enabled:false} && ${ivd.clinical.enabled:false}")
@RequireRole({"LAB_TECH","LAB_DOCTOR","LAB_DIRECTOR"})
public class SampleController {
    private final SampleService service;
    @GetMapping public Result<PageVO<SampleSummaryVO>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) Long patientId){return Result.success(service.list(page,pageSize,patientId));}
    @PostMapping public Result<SampleDetailVO> create(@Valid @RequestBody SampleRequest request){return Result.success(service.create(request));}
    @GetMapping("/{id}") public Result<SampleDetailVO> detail(@PathVariable Long id){return Result.success(service.detail(id));}
    @PostMapping("/{id}/receive") public Result<SampleDetailVO> receive(@PathVariable Long id,@Valid @RequestBody SampleActionRequest request){return Result.success(service.receive(id,request));}
    @PostMapping("/{id}/reject") public Result<SampleDetailVO> reject(@PathVariable Long id,@Valid @RequestBody SampleActionRequest request){return Result.success(service.reject(id,request));}
}
