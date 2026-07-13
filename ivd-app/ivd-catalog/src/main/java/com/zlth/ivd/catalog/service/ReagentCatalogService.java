package com.zlth.ivd.catalog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zlth.ivd.catalog.dto.CreateReagentRequest;
import com.zlth.ivd.catalog.dto.ReagentCatalogQuery;
import com.zlth.ivd.catalog.dto.ReviewRequest;
import com.zlth.ivd.catalog.dto.SubmitApprovalRequest;
import com.zlth.ivd.catalog.dto.UpdateReagentRequest;
import com.zlth.ivd.catalog.entity.CatalogApprovalLog;
import com.zlth.ivd.catalog.entity.UdiBacklog;
import com.zlth.ivd.catalog.vo.ReagentCatalogVO;
import com.zlth.ivd.common.dto.PageVO;

/**
 * 试剂目录管理服务接口
 *
 * @author IVD Team
 * @since 2026-07-01
 */
public interface ReagentCatalogService {

    /** 分页查询试剂目录列表 */
    PageVO<ReagentCatalogVO> pageList(ReagentCatalogQuery query, int page, int pageSize);

    /** 按ID查询单个试剂详情（含五码映射） */
    ReagentCatalogVO getById(Long id);

    /** 新增试剂 */
    ReagentCatalogVO create(CreateReagentRequest request);

    /** 编辑试剂 */
    ReagentCatalogVO update(Long id, UpdateReagentRequest request);

    /** 软删除试剂 */
    void delete(Long id);

    /** 提交审批 */
    void submitApproval(Long id, SubmitApprovalRequest request);

    /** 审批通过 */
    void approve(Long approvalLogId, ReviewRequest request);

    /** 审批驳回 */
    void reject(Long approvalLogId, ReviewRequest request);

    /** 分页查询待审批列表 */
    PageVO<CatalogApprovalLog> pagePendingApprovals(int page, int pageSize);

    /** 分页查询审批历史 */
    PageVO<CatalogApprovalLog> pageApprovalHistory(int page, int pageSize);

    /** 分页查询 UDI 待补全列表 */
    PageVO<UdiBacklog> pageUdiBacklog(int page, int pageSize, String impactLevel);
}
