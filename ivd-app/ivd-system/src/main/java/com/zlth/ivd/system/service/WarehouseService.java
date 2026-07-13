package com.zlth.ivd.system.service;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.system.vo.WarehouseVO;

import java.util.List;

/**
 * 库房管理服务 — M00-3（管理端CRUD，区别于 InventoryService 的库存操作）
 *
 * @author IVD Team
 * @since 2026-07-13
 */
public interface WarehouseService {

    PageVO<WarehouseVO> list(int page, int pageSize, String keyword, Integer level);

    WarehouseVO getById(Long id);

    WarehouseVO create(String name, String code, Integer level, Long parentId, Long deptId,
                       String location, String contactPerson, String contactPhone, Integer sortOrder);

    WarehouseVO update(Long id, String name, String code, Integer level, Long parentId, Long deptId,
                       String location, String contactPerson, String contactPhone, Integer sortOrder);

    void delete(Long id);

    void toggleStatus(Long id);

    List<WarehouseVO> tree();

    List<WarehouseVO> options(Integer level);
}
