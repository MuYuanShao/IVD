package com.zlth.ivd.laboratory.controller;
import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.common.dto.*;
import com.zlth.ivd.laboratory.service.ResultEvidenceService;
import com.zlth.ivd.laboratory.vo.ClinicalEvidenceTraceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/lab/evidence/trace") @RequiredArgsConstructor
@ConditionalOnExpression("${ivd.isolation.enabled:false} && ${ivd.clinical.enabled:false}")
@RequireRole({"LAB_DIRECTOR","LAB_DOCTOR","LAB_TECH"})
public class ClinicalEvidenceTraceController {
    private final ResultEvidenceService service;
    @GetMapping public Result<PageVO<ClinicalEvidenceTraceVO>> trace(@RequestParam(required=false) Long batchId,@RequestParam(required=false) Long consumptionId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return Result.success(service.trace(batchId,consumptionId,page,pageSize));}
}
