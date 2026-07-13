package com.zlth.ivd.license.service;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.license.dto.CreateManufacturerRequest;
import com.zlth.ivd.license.dto.UpdateManufacturerRequest;
import com.zlth.ivd.license.vo.ManufacturerVO;

import java.util.List;

/**
 * 厂家管理服务接口
 *
 * @author IVD Team
 * @since 2026-07-09
 */
public interface ManufacturerService {

    PageVO<ManufacturerVO> list(int page, int pageSize, String keyword);

    ManufacturerVO getById(Long id);

    ManufacturerVO create(CreateManufacturerRequest request);

    ManufacturerVO update(Long id, UpdateManufacturerRequest request);

    void delete(Long id);

    /** 启用/停用切换 */
    void toggleActive(Long id);

    /** 全部厂家（下拉选项） */
    List<ManufacturerVO> options(String keyword);

    /** 导出全部（含停用） */
    List<ManufacturerVO> exportAll();

    /** 批量导入，返回成功条数 */
    int importBatch(List<CreateManufacturerRequest> requests);
}
