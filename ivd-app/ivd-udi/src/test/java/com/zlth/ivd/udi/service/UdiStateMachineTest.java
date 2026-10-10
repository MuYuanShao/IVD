package com.zlth.ivd.udi.service;

import com.zlth.ivd.common.enums.UdiStatusEnum;
import com.zlth.ivd.common.exception.IllegalStateTransitionException;
import com.zlth.ivd.udi.mapper.UdiStatusLogMapper;
import com.zlth.ivd.udi.event.UdiStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UdiStateMachineTest {

    @Test
    void legalTransitionMustUpdateAppendLogAndPublishEvent() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        UdiBatchStateStore store = mock(UdiBatchStateStore.class);
        UdiStatusLogMapper logs = mock(UdiStatusLogMapper.class);
        when(store.updateStatus(1L, UdiStatusEnum.RECEIVED, UdiStatusEnum.IN_STOCK)).thenReturn(true);
        UdiStateMachine machine = new UdiStateMachine(publisher, store, logs);

        machine.transition(1L, "LOT-1", UdiStatusEnum.RECEIVED, UdiStatusEnum.IN_STOCK, "1002");

        verify(store).updateStatus(1L, UdiStatusEnum.RECEIVED, UdiStatusEnum.IN_STOCK);
        verify(logs).append(any());
        verify(publisher).publishEvent(any(UdiStatusChangedEvent.class));
    }

    @Test
    void illegalTransitionMustBeRejectedBeforeUpdate() {
        UdiStateMachine machine = new UdiStateMachine(
                mock(ApplicationEventPublisher.class), mock(UdiBatchStateStore.class), mock(UdiStatusLogMapper.class));

        assertThrows(IllegalStateTransitionException.class,
                () -> machine.transition(1L, "LOT-1", UdiStatusEnum.RECEIVED,
                        UdiStatusEnum.CONSUMED, "1002"));
    }
}
