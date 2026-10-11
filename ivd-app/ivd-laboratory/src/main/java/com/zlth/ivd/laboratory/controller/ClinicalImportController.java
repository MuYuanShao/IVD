package com.zlth.ivd.laboratory.controller;
import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.laboratory.importing.ImportResource;
import com.zlth.ivd.laboratory.service.ClinicalImportService;
import com.zlth.ivd.laboratory.vo.ImportBatchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/v1/lab/imports") @RequiredArgsConstructor
@ConditionalOnExpression("${ivd.isolation.enabled:false} && ${ivd.clinical.enabled:false}")
@RequireRole({"SYSTEM_ADMIN","LAB_DIRECTOR","LAB_DOCTOR","LAB_TECH"})
public class ClinicalImportController {
    private final ClinicalImportService service;
    @GetMapping("/{resource}/template") public ResponseEntity<byte[]> template(@PathVariable ImportResource resource){return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).cacheControl(CacheControl.noStore()).header("Content-Disposition","attachment; filename=ivd-import-"+resource.name()+".xlsx").body(service.template(resource));}
    @PostMapping(value="/{resource}/preflight",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public Result<ImportBatchVO> preflight(@PathVariable ImportResource resource,@RequestPart("file") MultipartFile file)throws java.io.IOException{
        if(file.getSize()>10*1024*1024)throw new com.zlth.ivd.common.exception.BizException(40001,"导入文件超过10MB");return Result.success(service.preflight(resource,file.getBytes()));
    }
    @GetMapping("/batches/{id}") public Result<ImportBatchVO> detail(@PathVariable Long id){return Result.success(service.detail(id));}
    @PostMapping("/batches/{id}/execute") public Result<ImportBatchVO> execute(@PathVariable Long id){return Result.success(service.execute(id));}
}
