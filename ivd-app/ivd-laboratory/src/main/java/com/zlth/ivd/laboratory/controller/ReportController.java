package com.zlth.ivd.laboratory.controller;
import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.common.dto.*;
import com.zlth.ivd.laboratory.dto.*;
import com.zlth.ivd.laboratory.service.ReportService;
import com.zlth.ivd.laboratory.vo.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/lab/reports") @RequiredArgsConstructor
@ConditionalOnExpression("${ivd.isolation.enabled:false} && ${ivd.clinical.enabled:false}")
@RequireRole({"LAB_DIRECTOR","LAB_DOCTOR","LAB_TECH"})
public class ReportController {
    private final ReportService service;
    @GetMapping public Result<PageVO<ReportSummaryVO>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) Long sampleId){return Result.success(service.list(page,pageSize,sampleId));}
    @GetMapping("/{id}") public Result<ReportDetailVO> detail(@PathVariable Long id){return Result.success(service.detail(id));}
    @PostMapping public Result<ReportDetailVO> create(@Valid @RequestBody ReportRequest request){return Result.success(service.create(request));}
    @PutMapping("/{id}") public Result<ReportDetailVO> update(@PathVariable Long id,@Valid @RequestBody ReportRequest request){return Result.success(service.update(id,request));}
    @PostMapping("/{id}/submit") public Result<ReportDetailVO> submit(@PathVariable Long id,@Valid @RequestBody ReportActionRequest request){return Result.success(service.submit(id,request));}
    @PostMapping("/{id}/review") public Result<ReportDetailVO> review(@PathVariable Long id,@Valid @RequestBody ReportActionRequest request){return Result.success(service.review(id,request));}
    @PostMapping("/{id}/return") public Result<ReportDetailVO> returnForEdit(@PathVariable Long id,@Valid @RequestBody ReportActionRequest request){return Result.success(service.returnForEdit(id,request));}
    @PostMapping("/{id}/sign") @RequireRole("LAB_DIRECTOR") public Result<ReportDetailVO> sign(@PathVariable Long id,@Valid @RequestBody ReportActionRequest request){return Result.success(service.sign(id,request));}
    @PostMapping("/{id}/correct") public Result<ReportDetailVO> correct(@PathVariable Long id,@Valid @RequestBody ReportActionRequest request){return Result.success(service.correct(id,request));}
    @PostMapping("/{id}/revoke") @RequireRole("LAB_DIRECTOR") public Result<ReportDetailVO> revoke(@PathVariable Long id,@Valid @RequestBody ReportActionRequest request){return Result.success(service.revoke(id,request));}
    @GetMapping("/{id}/versions") public Result<List<ReportSummaryVO>> versions(@PathVariable Long id){return Result.success(service.versions(id));}
    @GetMapping("/{id}/history") public Result<List<ReportHistoryVO>> history(@PathVariable Long id){return Result.success(service.history(id));}
    @GetMapping("/{id}/pdf") public ResponseEntity<byte[]> pdf(@PathVariable Long id,@RequestParam(defaultValue="PREVIEW") String purpose){
        var bytes=service.pdf(id,purpose);return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header("Content-Disposition",("DOWNLOAD".equals(purpose)?"attachment":"inline")+"; filename=ivd-report-"+id+".pdf")
                .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox; frame-ancestors 'self'").body(bytes);
    }
}
