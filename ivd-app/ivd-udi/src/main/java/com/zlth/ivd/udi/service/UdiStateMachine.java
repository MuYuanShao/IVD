package com.zlth.ivd.udi.service;

import com.zlth.ivd.common.enums.UdiStatusEnum;
import com.zlth.ivd.common.exception.IllegalStateTransitionException;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.utils.IdGenerator;
import com.zlth.ivd.udi.entity.UdiStatusLog;
import com.zlth.ivd.udi.event.UdiStatusChangedEvent;
import com.zlth.ivd.udi.mapper.UdiStatusLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

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
    private final UdiBatchStateStore batchStateStore;
    private final UdiStatusLogMapper logMapper;

    private static final Map<UdiStatusEnum, EnumSet<UdiStatusEnum>> TRANSITIONS = buildTransitions();

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
        transition(batchStateStore.findUniqueBatchId(batchNo), batchNo, from, to, operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public void transition(Long batchId, String batchNo, UdiStatusEnum from, UdiStatusEnum to, String operator) {
        // 1. 合法性校验（对照状态转移矩阵）
        validateTransition(from, to);

        // 2. 更新批次状态
        if (!batchStateStore.updateStatus(batchId, from, to)) {
            throw new BizException(40900, "批次状态已变化，请刷新后重试");
        }

        // 3. 写入不可变日志
        LocalDateTime now = LocalDateTime.now();
        Integer operatorId = parseOperatorId(operator);
        UdiStatusLog statusLog = UdiStatusLog.builder()
                .id(IdGenerator.nextId())
                .batchNo(batchNo)
                .fromStatus(from.getCode())
                .toStatus(to.getCode())
                .operatorId(operatorId)
                .operatorName(operatorId == null ? operator : null)
                .occurredAt(now)
                .createdAt(now)
                .build();
        logMapper.append(statusLog);

        // 4. 发布事件（异步通知下游）
        eventPublisher.publishEvent(new UdiStatusChangedEvent(batchId, batchNo, from, to, operator));

        log.info("UDI状态转移: batch={}, {} → {}, operator={}", batchNo, from.getCode(), to.getCode(), operator);
    }

    /**
     * 校验状态转移合法性
     */
    void validateTransition(UdiStatusEnum from, UdiStatusEnum to) {
        if (from == null || to == null) {
            throw new IllegalStateTransitionException("批次状态不能为空");
        }
        if (from.isTerminal()) {
            throw new IllegalStateTransitionException(
                    String.format("批次状态 [%s] 为终态，不可再转移", from.getLabel()));
        }
        if (!TRANSITIONS.getOrDefault(from, EnumSet.noneOf(UdiStatusEnum.class)).contains(to)) {
            throw new IllegalStateTransitionException(
                    String.format("不允许从 [%s] 转移到 [%s]", from.getLabel(), to.getLabel()));
        }
    }

    private Integer parseOperatorId(String operator) {
        try {
            return operator == null ? null : Integer.valueOf(operator);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Map<UdiStatusEnum, EnumSet<UdiStatusEnum>> buildTransitions() {
        Map<UdiStatusEnum, EnumSet<UdiStatusEnum>> transitions = new EnumMap<>(UdiStatusEnum.class);
        transitions.put(UdiStatusEnum.RECEIVED, EnumSet.of(UdiStatusEnum.IN_STOCK, UdiStatusEnum.PENDING_UDI,
                UdiStatusEnum.BACK_SUPPLIER, UdiStatusEnum.SCRAPPED));
        transitions.put(UdiStatusEnum.PENDING_UDI, EnumSet.of(UdiStatusEnum.IN_STOCK, UdiStatusEnum.BACK_SUPPLIER));
        transitions.put(UdiStatusEnum.IN_STOCK, EnumSet.of(UdiStatusEnum.TRANSFERRED, UdiStatusEnum.IN_USE,
                UdiStatusEnum.BACK_SUPPLIER, UdiStatusEnum.SCRAPPED, UdiStatusEnum.EXPIRED));
        transitions.put(UdiStatusEnum.TRANSFERRED, EnumSet.of(UdiStatusEnum.IN_STOCK));
        transitions.put(UdiStatusEnum.IN_USE, EnumSet.of(UdiStatusEnum.CONSUMED, UdiStatusEnum.RETURNED,
                UdiStatusEnum.EXPIRED, UdiStatusEnum.SCRAPPED));
        transitions.put(UdiStatusEnum.RETURNED, EnumSet.of(UdiStatusEnum.IN_STOCK,
                UdiStatusEnum.BACK_SUPPLIER, UdiStatusEnum.SCRAPPED));
        transitions.put(UdiStatusEnum.SCRAPPED, EnumSet.of(UdiStatusEnum.DESTROYED));
        for (UdiStatusEnum status : UdiStatusEnum.values()) {
            if (!status.isTerminal()) {
                transitions.computeIfAbsent(status, ignored -> EnumSet.noneOf(UdiStatusEnum.class))
                        .add(UdiStatusEnum.RECALLED);
            }
        }
        return transitions;
    }
}
