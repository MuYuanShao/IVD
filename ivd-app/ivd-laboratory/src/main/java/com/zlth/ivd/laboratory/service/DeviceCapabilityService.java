package com.zlth.ivd.laboratory.service;
import com.zlth.ivd.laboratory.dto.DeviceCapabilityRequest;
import com.zlth.ivd.laboratory.vo.DeviceCapabilityVO;
import java.util.List;
public interface DeviceCapabilityService {
    DeviceCapabilityVO set(Long deviceId,DeviceCapabilityRequest request);
    List<DeviceCapabilityVO> list(Long deviceId);
    void requireExecutable(Long deviceId,Long itemId);
}
