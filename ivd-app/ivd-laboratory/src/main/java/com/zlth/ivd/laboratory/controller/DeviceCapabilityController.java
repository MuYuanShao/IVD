package com.zlth.ivd.laboratory.controller;
import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.laboratory.dto.DeviceCapabilityRequest;
import com.zlth.ivd.laboratory.service.DeviceCapabilityService;
import com.zlth.ivd.laboratory.vo.DeviceCapabilityVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
@RestController @RequestMapping("/api/v1/lab/devices/{deviceId}/capabilities") @RequiredArgsConstructor
@ConditionalOnExpression("${ivd.isolation.enabled:false} && ${ivd.clinical.enabled:false}")
@RequireRole({"SYSTEM_ADMIN","LAB_DIRECTOR","LAB_DOCTOR","LAB_TECH"})
public class DeviceCapabilityController {
    private final DeviceCapabilityService service;
    @GetMapping public Result<List<DeviceCapabilityVO>> list(@PathVariable Long deviceId){return Result.success(service.list(deviceId));}
    @PutMapping @RequireRole({"SYSTEM_ADMIN","LAB_DIRECTOR"}) public Result<DeviceCapabilityVO> set(@PathVariable Long deviceId,@Valid @RequestBody DeviceCapabilityRequest request){return Result.success(service.set(deviceId,request));}
}
