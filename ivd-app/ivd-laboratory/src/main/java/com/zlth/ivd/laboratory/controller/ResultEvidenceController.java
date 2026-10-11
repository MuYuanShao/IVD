package com.zlth.ivd.laboratory.controller;
import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.laboratory.dto.ResultEvidenceRequest;
import com.zlth.ivd.laboratory.service.ResultEvidenceService;
import com.zlth.ivd.laboratory.vo.ResultEvidenceVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
@RestController @RequestMapping("/api/v1/lab/results/{resultId}/evidence") @RequiredArgsConstructor
@ConditionalOnExpression("${ivd.isolation.enabled:false} && ${ivd.clinical.enabled:false}")
@RequireRole({"LAB_DIRECTOR","LAB_DOCTOR","LAB_TECH"})
public class ResultEvidenceController {
    private final ResultEvidenceService service;
    @GetMapping("/batch-options") public Result<java.util.List<com.zlth.ivd.inventory.service.BatchEvidenceDirectoryService.BatchEvidence>> batchOptions(@PathVariable Long resultId,@RequestParam(required=false) String keyword){return Result.success(service.batchOptions(resultId,keyword));}
    @GetMapping("/consumption-options") public Result<java.util.List<com.zlth.ivd.consumption.service.ConsumptionEvidenceService.ConsumptionEvidence>> consumptionOptions(@PathVariable Long resultId,@RequestParam Long batchId){return Result.success(service.consumptionOptions(resultId,batchId));}
    @GetMapping public Result<List<ResultEvidenceVO>> list(@PathVariable Long resultId,@RequestParam(required=false) Integer resultVersion){return Result.success(service.list(resultId,resultVersion));}
    @PostMapping public Result<ResultEvidenceVO> bind(@PathVariable Long resultId,@Valid @RequestBody ResultEvidenceRequest request){return Result.success(service.bind(resultId,request));}
}
