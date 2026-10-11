package com.zlth.ivd.consumption.service.impl;

import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.catalog.service.CatalogReferenceService;
import com.zlth.ivd.common.enums.*;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.consumption.entity.ConsumptionRecord;
import com.zlth.ivd.consumption.mapper.ConsumptionRecordMapper;
import com.zlth.ivd.inventory.entity.ReagentBatch;
import com.zlth.ivd.inventory.service.ConsumptionInventoryService;
import com.zlth.ivd.inventory.service.InstrumentUsageService;
import com.zlth.ivd.udi.service.UniqueCodeService;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DeviceBindingConsumptionTest {
    ConsumptionServiceImpl service; ReagentBatch batch; ReagentCatalog catalog;
    ConsumptionRecordMapper records; ConsumptionInventoryService batches; InstrumentUsageService instruments;
    @BeforeEach void setup() {
        records=mock(ConsumptionRecordMapper.class);batches=mock(ConsumptionInventoryService.class);instruments=mock(InstrumentUsageService.class);
        var catalogs=mock(CatalogReferenceService.class);
        service=new ConsumptionServiceImpl(records,batches,catalogs,mock(UniqueCodeService.class),mock(org.springframework.context.ApplicationEventPublisher.class),
                mock(com.zlth.ivd.common.service.StockBillingStatusService.class),instruments);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(1L,"test"));
        batch=new ReagentBatch();batch.setId(1L);batch.setReagentId(2L);batch.setWarehouseId(3L);batch.setStock(new BigDecimal("3"));batch.setUnitPrice(new BigDecimal("12.50"));batch.setUdiStatus(UdiStatusEnum.IN_STOCK);batch.setExpiryDate(LocalDate.now().plusDays(30));
        catalog=new ReagentCatalog();catalog.setMaterialType(MaterialTypeEnum.REAGENT);catalog.setUnit("盒");catalog.setDeviceBindMode(DeviceBindModeEnum.FLEXIBLE);
        when(batches.getBatch(1L)).thenReturn(batch);when(catalogs.findById(2L)).thenReturn(catalog);
        when(batches.consumeStock(eq(1L),any())).thenAnswer(call->{
            if(batch.getExpiryDate().isBefore(LocalDate.now()))throw new BizException(40001,"批次已过期");
            batch.setStock(batch.getStock().subtract(call.getArgument(1)));return batch;
        });
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); }
    @Test void flexibleWithoutDevicePersistsUnitAndExactStockBatch() {
        service.recordConsumption(1L,null,BigDecimal.ONE,"ROUTINE",null);
        var capture=org.mockito.ArgumentCaptor.forClass(ConsumptionRecord.class);verify(records).insert(capture.capture());
        assertNull(capture.getValue().getDeviceId());assertEquals("盒",capture.getValue().getUnit());assertEquals(1L,capture.getValue().getBatchId());assertEquals(3L,capture.getValue().getWarehouseId());assertEquals(new BigDecimal("12.50"),capture.getValue().getAmount());assertEquals(new BigDecimal("2"),batch.getStock());verifyNoInteractions(instruments);
    }
    @Test void forceWithoutDeviceDoesNotDeductOrInsert() {
        catalog.setDeviceBindMode(DeviceBindModeEnum.FORCE);assertThrows(BizException.class,()->service.recordConsumption(1L,null,BigDecimal.ONE,null,null));verify(records,never()).insert(any(ConsumptionRecord.class));assertEquals(new BigDecimal("3"),batch.getStock());
    }
    @Test void suppliedDeviceMustBeValidatedAndRegistered() {
        catalog.setDeviceBindMode(DeviceBindModeEnum.FORCE);service.recordConsumption(1L,5L,BigDecimal.ONE,null,null);verify(instruments).bind(1L,5L);
    }
    @Test void expiredBatchCannotBeConsumed() { batch.setExpiryDate(LocalDate.now().minusDays(1));assertThrows(BizException.class,()->service.recordConsumption(1L,null,BigDecimal.ONE,null,null));verifyNoInteractions(records); }
    @Test void uniqueCodeUnitsMustBeWhole() { catalog.setEnableUniqueCode(1);assertThrows(BizException.class,()->service.recordConsumption(1L,null,new BigDecimal("0.5"),null,null));verifyNoInteractions(records); }
}
