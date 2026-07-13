package com.zlth.ivd.license.service;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.license.dto.CreateDistributorRequest;
import com.zlth.ivd.license.dto.UpdateDistributorRequest;
import com.zlth.ivd.license.vo.DistributorVO;

import java.util.List;

/**
 * 配送商管理服务接口
 *
 * @author IVD Team
 * @since 2026-07-09
 */
public interface DistributorService {

    PageVO<DistributorVO> list(int page, int pageSize, String keyword);

    DistributorVO getById(Long id);

    DistributorVO create(CreateDistributorRequest request);

    DistributorVO update(Long id, UpdateDistributorRequest request);

    void delete(Long id);

    /** 启用/停用切换 */
    void toggleActive(Long id);

    /** 全部配送商（下拉选项） */
    List<DistributorVO> options(String keyword);

    /** 导出全部（含停用） */
    List<DistributorVO> exportAll();

    /** 批量导入，返回成功条数 */
    int importBatch(List<CreateDistributorRequest> requests);
}
