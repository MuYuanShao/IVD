package com.zlth.ivd.system.controller;
import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.common.dto.*;
import com.zlth.ivd.system.dto.InstitutionRequest;
import com.zlth.ivd.system.service.InstitutionService;
import com.zlth.ivd.system.vo.InstitutionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/institutions") @RequiredArgsConstructor
@RequireRole("SYSTEM_ADMIN")
public class InstitutionController {
    private final InstitutionService service;
    @GetMapping public Result<PageVO<InstitutionVO>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword){return Result.success(service.list(page,pageSize,keyword));}
    @GetMapping("/options") @RequireRole({"SYSTEM_ADMIN","LAB_TECH","LAB_DOCTOR","LAB_DIRECTOR","STOREKEEPER","PURCHASER","FINANCE","IT_STAFF"})
    public Result<List<InstitutionVO>> options(){return Result.success(service.options());}
    @PostMapping public Result<InstitutionVO> create(@Valid @RequestBody InstitutionRequest request){return Result.success(service.create(request));}
    @PutMapping("/{id}") public Result<InstitutionVO> update(@PathVariable Long id,@Valid @RequestBody InstitutionRequest request){return Result.success(service.update(id,request));}
}
