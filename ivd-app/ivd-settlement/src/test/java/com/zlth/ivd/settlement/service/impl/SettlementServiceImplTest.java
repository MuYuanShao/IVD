package com.zlth.ivd.settlement.service.impl;

import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.consumption.mapper.ConsumptionRecordMapper;
import com.zlth.ivd.settlement.mapper.SettlementRecordMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SettlementServiceImplTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingConsumptionSourceMustNotCreateSettlement() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, "token"));
        SettlementServiceImpl service = new SettlementServiceImpl(
                mock(SettlementRecordMapper.class), mock(ConsumptionRecordMapper.class),
                null, null, null, null);

        assertThrows(BizException.class, () -> service.settleConsumption(999L));
    }
}
