package com.zlth.ivd.inventory.service;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.inventory.dto.DeviceRegistryRequest;
import com.zlth.ivd.inventory.vo.DeviceRegistryVO;
public interface DeviceRegistryService {
    DeviceRegistryVO create(DeviceRegistryRequest request);
    DeviceRegistryVO update(Long id,DeviceRegistryRequest request);
    DeviceRegistryVO detail(Long id);
    PageVO<DeviceRegistryVO> list(int page,int pageSize,String keyword);
    java.util.List<com.zlth.ivd.inventory.vo.DeviceWarehouseOptionVO> warehouseOptions();
}
