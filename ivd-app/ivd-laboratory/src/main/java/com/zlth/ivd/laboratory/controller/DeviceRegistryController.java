package com.zlth.ivd.laboratory.controller;
import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.common.dto.*;
import com.zlth.ivd.inventory.dto.DeviceRegistryRequest;
import com.zlth.ivd.inventory.vo.DeviceRegistryVO;
import com.zlth.ivd.inventory.service.DeviceRegistryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/lab/devices") @RequiredArgsConstructor
@ConditionalOnExpression("${ivd.isolation.enabled:false} && ${ivd.clinical.enabled:false}")
@RequireRole({"SYSTEM_ADMIN","LAB_DIRECTOR","LAB_DOCTOR","LAB_TECH"})
public class DeviceRegistryController {
    private final DeviceRegistryService service;
    @GetMapping("/warehouses/options") public Result<java.util.List<com.zlth.ivd.inventory.vo.DeviceWarehouseOptionVO>> warehouses(){return Result.success(service.warehouseOptions());}
    @GetMapping public Result<PageVO<DeviceRegistryVO>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword){return Result.success(service.list(page,pageSize,keyword));}
    @GetMapping("/{id}") public Result<DeviceRegistryVO> detail(@PathVariable Long id){return Result.success(service.detail(id));}
    @PostMapping @RequireRole({"SYSTEM_ADMIN","LAB_DIRECTOR"}) public Result<DeviceRegistryVO> create(@Valid @RequestBody DeviceRegistryRequest request){return Result.success(service.create(request));}
    @PutMapping("/{id}") @RequireRole({"SYSTEM_ADMIN","LAB_DIRECTOR"}) public Result<DeviceRegistryVO> update(@PathVariable Long id,@Valid @RequestBody DeviceRegistryRequest request){return Result.success(service.update(id,request));}
}
