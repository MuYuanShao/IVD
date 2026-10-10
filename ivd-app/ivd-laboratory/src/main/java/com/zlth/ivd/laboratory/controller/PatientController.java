package com.zlth.ivd.laboratory.controller;

import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.common.dto.*;
import com.zlth.ivd.laboratory.dto.*;
import com.zlth.ivd.laboratory.service.PatientService;
import com.zlth.ivd.laboratory.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/lab/patients") @RequiredArgsConstructor
@ConditionalOnExpression("${ivd.isolation.enabled:false} && ${ivd.clinical.enabled:false}")
@RequireRole({"LAB_TECH","LAB_DOCTOR","LAB_DIRECTOR"})
public class PatientController {
    private final PatientService service;
    @GetMapping public Result<PageVO<PatientSummaryVO>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return Result.success(service.list(page,pageSize));}
    @GetMapping("/{id}") public Result<PatientDetailVO> detail(@PathVariable Long id){return Result.success(service.detail(id));}
    @PostMapping public Result<PatientDetailVO> create(@Valid @RequestBody PatientRequest request){return Result.success(service.create(request));}
    @PutMapping("/{id}") public Result<PatientDetailVO> update(@PathVariable Long id,@Valid @RequestBody PatientRequest request){return Result.success(service.update(id,request));}
    @PostMapping("/{id}/departments") @RequireRole("SYSTEM_ADMIN")
    public Result<Void> departmentAccess(@PathVariable Long id,@Valid @RequestBody PatientDepartmentRequest request){service.setDepartmentAccess(id,request);return Result.success();}
}
