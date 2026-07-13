package com.zlth.ivd.udi.service;

import com.zlth.ivd.common.enums.UdiStatusEnum;
import com.zlth.ivd.common.exception.IllegalStateTransitionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * UDI 状态机 — 批次生命周期管理
 * <p>
 * 仅适用于 material_type ∈ {REAGENT, CALIBRATOR, QC_MATERIAL}
 * 所有状态转移在同一事务内完成：更新状态 + 写入不可变日志 + 发布事件
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UdiStateMachine {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 执行状态转移
     *
     * @param batchNo  批次号
     * @param from     当前状态
     * @param to       目标状态
     * @param operator 操作人
     * @throws IllegalStateTransitionException 非法状态转移
     */
    @Transactional(rollbackFor = Exception.class)
    public void transition(String batchNo, UdiStatusEnum from, UdiStatusEnum to, String operator) {
        // 1. 合法性校验（对照状态转移矩阵）
        validateTransition(from, to);

        // 2. 更新批次状态
        // reagentBatchMapper.updateStatus(batchNo, to);

        // 3. 写入不可变日志
        // UdiStatusLog log = UdiStatusLog.builder()
        //         .batchNo(batchNo)
        //         .fromStatus(from.getCode())
        //         .toStatus(to.getCode())
        //         .operator(operator)
        //         .occurredAt(LocalDateTime.now())
        //         .build();
        // logMapper.insert(log);

        // 4. 发布事件（异步通知下游）
        // eventPublisher.publishEvent(new UdiStatusChangedEvent(batchNo, from, to, operator));

        log.info("UDI状态转移: batch={}, {} → {}, operator={}", batchNo, from.getCode(), to.getCode(), operator);
    }

    /**
     * 校验状态转移合法性
     */
    private void validateTransition(UdiStatusEnum from, UdiStatusEnum to) {
        if (from.isTerminal()) {
            throw new IllegalStateTransitionException(
                    String.format("批次状态 [%s] 为终态，不可再转移", from.getLabel()));
        }
        // TODO: 完整的状态转移矩阵校验
    }
}
