package com.zlth.ivd.laboratory.controller;
import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.common.dto.*;
import com.zlth.ivd.laboratory.dto.ResultEntryRequest;
import com.zlth.ivd.laboratory.service.LabResultService;
import com.zlth.ivd.laboratory.vo.ResultDetailVO;
import com.zlth.ivd.laboratory.vo.TestingInstitutionOptionVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/lab") @RequiredArgsConstructor
@ConditionalOnExpression("${ivd.isolation.enabled:false} && ${ivd.clinical.enabled:false}")
@RequireRole({"LAB_TECH","LAB_DOCTOR","LAB_DIRECTOR"})
public class LabResultController {
    private final LabResultService service;
    @GetMapping("/results") public Result<PageVO<ResultDetailVO>> search(@RequestParam(required=false) Long deviceId,@RequestParam(required=false) Long patientId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return Result.success(service.search(deviceId,patientId,page,pageSize));}
    @GetMapping("/testing-institutions/options") public Result<List<TestingInstitutionOptionVO>> testingInstitutions(){return Result.success(service.testingInstitutions());}
    @GetMapping("/samples/{sampleId}/results") public Result<PageVO<ResultDetailVO>> list(@PathVariable Long sampleId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return Result.success(service.list(sampleId,page,pageSize));}
    @PostMapping("/samples/{sampleId}/results") public Result<ResultDetailVO> create(@PathVariable Long sampleId,@Valid @RequestBody ResultEntryRequest request){return Result.success(service.create(sampleId,request));}
    @GetMapping("/results/{id}") public Result<ResultDetailVO> detail(@PathVariable Long id){return Result.success(service.detail(id));}
    @PutMapping("/results/{id}") public Result<ResultDetailVO> update(@PathVariable Long id,@Valid @RequestBody ResultEntryRequest request){return Result.success(service.update(id,request));}
    @GetMapping("/results/{id}/versions") public Result<List<ResultDetailVO>> versions(@PathVariable Long id){return Result.success(service.versions(id));}
}
