package com.zlth.ivd.consumption.service.impl;

import com.zlth.ivd.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsumptionServiceImplTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void consumptionQuantityMustBePositiveBeforeInventoryAccess() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, "token"));
        ConsumptionServiceImpl service = new ConsumptionServiceImpl(
                null, null, null, null, null, null, null, null, null);

        assertThrows(BizException.class,
                () -> service.recordConsumption(10L, 20L, new BigDecimal("-1"), null, null));
    }
}
