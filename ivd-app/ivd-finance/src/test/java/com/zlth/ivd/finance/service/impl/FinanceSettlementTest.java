package com.zlth.ivd.finance.service.impl;

import com.zlth.ivd.finance.entity.DailyInventorySnapshot;
import com.zlth.ivd.finance.mapper.DailyInventorySnapshotMapper;
import com.zlth.ivd.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class FinanceSettlementTest {
    @Test void beforeImportDateCannotCreateHistoricalSnapshots() {
        var mapper = mock(DailyInventorySnapshotMapper.class);
        when(mapper.getEarliestSupportedDate()).thenReturn(LocalDate.of(2026,8,7));
        assertThrows(BizException.class,()->new FinanceServiceImpl(mapper).dailySettlement(LocalDate.of(2026,7,7)));
        verify(mapper,never()).getDailyBalances(any());
        verify(mapper,never()).insert(any(DailyInventorySnapshot.class));
    }
    @Test void unpricedEvidenceCannotBeSilentlyValuedAtZero() {
        var mapper = mock(DailyInventorySnapshotMapper.class);
        when(mapper.countUnpricedEvidence()).thenReturn(1L);
        assertThrows(BizException.class,()->new FinanceServiceImpl(mapper).dailySettlement(LocalDate.now()));
        verify(mapper,never()).getDailyBalances(any());
        verify(mapper,never()).insert(any(DailyInventorySnapshot.class));
    }
    @Test void eachReagentWarehouseUsesItsOwnMonetaryBalancesIncludingZeroStock() {
        var mapper = mock(DailyInventorySnapshotMapper.class);
        LocalDate date = LocalDate.now().minusDays(1);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.getDailyBalances(date)).thenReturn(List.of(
                Map.of("reagentId",1L,"warehouseId",2L,"opening",100,"inbound",20,"outbound",30,"closing",90),
                Map.of("reagentId",3L,"warehouseId",2L,"opening",40,"inbound",0,"outbound",40,"closing",0)));
        new FinanceServiceImpl(mapper).dailySettlement(date);
        var captured = ArgumentCaptor.forClass(DailyInventorySnapshot.class);
        verify(mapper,times(2)).insert(captured.capture());
        assertEquals(new BigDecimal("100"),captured.getAllValues().get(0).getOpening());
        assertEquals(new BigDecimal("40"),captured.getAllValues().get(1).getOpening());
        assertEquals(BigDecimal.ZERO,captured.getAllValues().get(1).getClosing());
        assertTrue(captured.getAllValues().stream().allMatch(s -> s.getDifference().signum()==0));
    }
    @Test void duplicateSettlementIsRejectedWithoutAppendingSnapshots() {
        var mapper = mock(DailyInventorySnapshotMapper.class);
        when(mapper.selectCount(any())).thenReturn(1L);
        assertThrows(BizException.class,()->new FinanceServiceImpl(mapper).dailySettlement(LocalDate.now()));
        verify(mapper,never()).insert(any(DailyInventorySnapshot.class));
    }
}
