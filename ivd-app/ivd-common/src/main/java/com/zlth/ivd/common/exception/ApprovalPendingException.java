package com.zlth.ivd.common.exception;

/**
 * 审批中冲突异常 — 当对"待审批"状态的记录再次提交变更时抛出
 *
 * @author IVD Team
 * @since 2026-07-01
 */
public class ApprovalPendingException extends BizException {

    public ApprovalPendingException(Long reagentId) {
        super(40901,
                String.format("试剂 ID=%d 已有一条待审批的变更记录，请等待审批完成后再提交", reagentId),
                String.format("reagentId=%d has pending approval", reagentId));
    }
}
