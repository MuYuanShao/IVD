package com.zlth.ivd.catalog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zlth.ivd.catalog.entity.CatalogApprovalLog;

/**
 * 审批日志 Service
 *
 * @author IVD Team
 * @since 2026-07-03
 */
public interface CatalogApprovalLogService {

    /**
     * 创建审批日志（提交审批时调用）
     */
    CatalogApprovalLog createLog(Long reagentId, String changeType, String fieldChanges,
                                  String changeReason, Long submitterId, String submitterName);

    /**
     * 按ID查询
     */
    CatalogApprovalLog getById(Long id);

    /**
     * 分页查询待审批记录
     */
    IPage<CatalogApprovalLog> pagePending(int page, int pageSize);

    /**
     * 分页查询审批历史（全部记录）
     */
    IPage<CatalogApprovalLog> pageHistory(int page, int pageSize);

    /**
     * 审批通过
     */
    void approve(Long logId, Long reviewerId, String reviewerName, String comment);

    /**
     * 审批驳回
     */
    void reject(Long logId, Long reviewerId, String reviewerName, String comment);

    /**
     * 检查试剂是否存在进行中的审批记录
     */
    boolean hasPending(Long reagentId);
}
