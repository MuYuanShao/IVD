package com.zlth.ivd.catalog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.catalog.entity.CatalogApprovalLog;
import com.zlth.ivd.catalog.mapper.CatalogApprovalLogMapper;
import com.zlth.ivd.catalog.service.CatalogApprovalLogService;
import com.zlth.ivd.common.utils.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 审批日志 Service 实现
 *
 * @author IVD Team
 * @since 2026-07-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogApprovalLogServiceImpl implements CatalogApprovalLogService {

    private final CatalogApprovalLogMapper approvalLogMapper;

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public CatalogApprovalLog lockById(Long id) {
        return approvalLogMapper.selectOne(new LambdaQueryWrapper<CatalogApprovalLog>()
                .eq(CatalogApprovalLog::getId, id).last("FOR UPDATE"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CatalogApprovalLog createLog(Long reagentId, String changeType, String fieldChanges,
                                         String changeReason, Long submitterId, String submitterName) {
        CatalogApprovalLog approvalLog = new CatalogApprovalLog();
        approvalLog.setId(IdGenerator.nextId());
        approvalLog.setReagentId(reagentId);
        approvalLog.setChangeType(changeType);
        approvalLog.setFieldChanges(fieldChanges);
        approvalLog.setChangeReason(changeReason);
        approvalLog.setSubmitterId(submitterId);
        approvalLog.setSubmitterName(submitterName);
        approvalLog.setReviewStatus("PENDING");
        approvalLog.setSubmittedAt(LocalDateTime.now());

        approvalLogMapper.insert(approvalLog);
        log.info("审批日志已创建: id={}, reagentId={}, changeType={}", approvalLog.getId(), reagentId, changeType);
        return approvalLog;
    }

    @Override
    public CatalogApprovalLog getById(Long id) {
        return approvalLogMapper.selectById(id);
    }

    @Override
    public IPage<CatalogApprovalLog> pagePending(int page, int pageSize) {
        Page<CatalogApprovalLog> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<CatalogApprovalLog> wrapper = new LambdaQueryWrapper<CatalogApprovalLog>()
                .eq(CatalogApprovalLog::getReviewStatus, "PENDING")
                .orderByDesc(CatalogApprovalLog::getSubmittedAt);
        return approvalLogMapper.selectPage(p, wrapper);
    }

    @Override
    public IPage<CatalogApprovalLog> pageHistory(int page, int pageSize) {
        Page<CatalogApprovalLog> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<CatalogApprovalLog> wrapper = new LambdaQueryWrapper<CatalogApprovalLog>()
                .orderByDesc(CatalogApprovalLog::getSubmittedAt);
        return approvalLogMapper.selectPage(p, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long logId, Long reviewerId, String reviewerName, String comment) {
        CatalogApprovalLog approvalLog = getById(logId);
        approvalLog.setReviewerId(reviewerId);
        approvalLog.setReviewerName(reviewerName);
        approvalLog.setReviewComment(comment);
        approvalLog.setReviewStatus("APPROVED");
        approvalLog.setReviewedAt(LocalDateTime.now());
        approvalLogMapper.updateById(approvalLog);
        log.info("审批已通过: logId={}, reviewerId={}", logId, reviewerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long logId, Long reviewerId, String reviewerName, String comment) {
        CatalogApprovalLog approvalLog = getById(logId);
        approvalLog.setReviewerId(reviewerId);
        approvalLog.setReviewerName(reviewerName);
        approvalLog.setReviewComment(comment);
        approvalLog.setReviewStatus("REJECTED");
        approvalLog.setReviewedAt(LocalDateTime.now());
        approvalLogMapper.updateById(approvalLog);
        log.info("审批已驳回: logId={}, reviewerId={}", logId, reviewerId);
    }

    @Override
    public boolean hasPending(Long reagentId) {
        Long count = approvalLogMapper.selectCount(
                new LambdaQueryWrapper<CatalogApprovalLog>()
                        .eq(CatalogApprovalLog::getReagentId, reagentId)
                        .eq(CatalogApprovalLog::getReviewStatus, "PENDING")
        );
        return count != null && count > 0;
    }
}
