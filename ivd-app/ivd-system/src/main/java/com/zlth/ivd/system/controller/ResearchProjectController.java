package com.zlth.ivd.system.controller;

import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.system.dto.ResearchProjectRequest;
import com.zlth.ivd.system.service.ResearchProjectService;
import com.zlth.ivd.system.vo.ResearchProjectVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/system/research-projects")
@RequiredArgsConstructor
public class ResearchProjectController {
    private final ResearchProjectService service;
    @GetMapping @RequireRole({"SYSTEM_ADMIN","ADMIN","LAB_DIRECTOR"})
    public Result<List<ResearchProjectVO>> list() { return Result.success(service.list(true)); }
    @GetMapping("/options")
    public Result<List<ResearchProjectVO>> options() { return Result.success(service.list(false)); }
    @PostMapping @RequireRole({"SYSTEM_ADMIN","ADMIN","LAB_DIRECTOR"})
    public Result<ResearchProjectVO> create(@Valid @RequestBody ResearchProjectRequest request) { return Result.success(service.create(request)); }
    @PutMapping("/{id}") @RequireRole({"SYSTEM_ADMIN","ADMIN","LAB_DIRECTOR"})
    public Result<ResearchProjectVO> update(@PathVariable Long id,@Valid @RequestBody ResearchProjectRequest request) { return Result.success(service.update(id,request)); }
}
