package com.zlth.ivd.inventory.service;

/** Read-only cross-module contract; laboratory never injects an inventory mapper. */
public interface DeviceDirectoryService {
    DeviceReference requireActiveOwned(Long id);
    DeviceReference requireOwned(Long id);
    java.util.List<DeviceReference> activeOptions(String keyword);
    record DeviceReference(Long id,String code,String name,Long departmentId) { }
}
