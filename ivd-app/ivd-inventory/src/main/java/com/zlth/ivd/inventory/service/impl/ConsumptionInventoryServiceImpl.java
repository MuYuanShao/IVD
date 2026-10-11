package com.zlth.ivd.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.common.enums.*;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.inventory.entity.*;
import com.zlth.ivd.inventory.mapper.*;
import com.zlth.ivd.inventory.service.*;
import com.zlth.ivd.udi.service.UdiStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(rollbackFor=Exception.class)
public class ConsumptionInventoryServiceImpl implements ConsumptionInventoryService {
    private final ReagentBatchMapper batches;
    private final DeviceMapper devices;
    private final DeviceBatchLoadMapper loads;
    private final ProcurementInventoryService warehouses;
    private final UdiStateMachine states;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public ReagentBatch getBatch(Long id){return batches.selectById(id);}
    public Device getDevice(Long id){return devices.selectById(id);}
    public List<Device> getDevices(Collection<Long> ids){return ids.isEmpty()?List.of():devices.selectBatchIds(ids);}
    public List<ReagentBatch> availableByBatchNo(String batchNo){return batches.selectList(new LambdaQueryWrapper<ReagentBatch>()
            .eq(ReagentBatch::getBatchNo,batchNo).gt(ReagentBatch::getStock,BigDecimal.ZERO)
            .in(ReagentBatch::getUdiStatus,UdiStatusEnum.IN_STOCK,UdiStatusEnum.IN_USE));}
    public List<ReagentBatch> availableForDevice(String batchNo,Long warehouseId,BigDecimal quantity){return batches.selectList(
            new LambdaQueryWrapper<ReagentBatch>().eq(ReagentBatch::getBatchNo,batchNo).eq(ReagentBatch::getWarehouseId,warehouseId)
            .ge(ReagentBatch::getStock,quantity).in(ReagentBatch::getUdiStatus,UdiStatusEnum.IN_STOCK,UdiStatusEnum.IN_USE));}
    public long activeDeviceCount(){return devices.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getIsActive,true));}
    public long openLoadCount(){return loads.selectCount(new LambdaQueryWrapper<DeviceBatchLoad>().isNull(DeviceBatchLoad::getUnloadedAt));}
    public ReagentBatch consumeStock(Long id,BigDecimal quantity){
        ReagentBatch batch=batches.lockById(id);
        if(batch==null) throw new BizException(40400,"库存批次不存在");
        Warehouse warehouse=warehouses.requireWarehouse(batch.getWarehouseId());
        if(warehouse.getLevel()==null || warehouse.getLevel()<2) throw new BizException(40001,"科室消耗必须从科室库或组库扣减");
        InstitutionContext.current().filter(scope->Set.of("LAB_TECH","LAB_DIRECTOR").contains(scope.role())).ifPresent(scope->{
            warehouses.requireDepartmentWarehouse(batch.getWarehouseId(),scope.departmentId());
        });
        if(quantity==null || quantity.signum()<=0) throw new BizException(40001,"消耗数量必须大于零");
        if(batch.getExpiryDate()==null || batch.getExpiryDate().isBefore(LocalDate.now())) throw new BizException(40001,"批次过期或效期未维护");
        if(batch.getOpenExpiryDate()!=null && batch.getOpenExpiryDate().isBefore(LocalDate.now())) throw new BizException(40001,"批次已超过开瓶稳定期");
        if(batch.getUdiStatus()!=UdiStatusEnum.IN_STOCK && batch.getUdiStatus()!=UdiStatusEnum.IN_USE) throw new BizException(40001,"批次状态不允许消耗");
        if(batch.getStock().compareTo(quantity)<0) throw new BizException(40001,"库存不足");
        batch.setStock(batch.getStock().subtract(quantity));
        if(batches.updateById(batch)!=1) throw new BizException(40900,"库存发生并发变化");
        if(batch.getStock().signum()==0){
            if(batch.getMaterialType()!=null && batch.getMaterialType().entersUdiStateMachine()){
                if(batch.getUdiStatus()==UdiStatusEnum.IN_STOCK){states.transition(id,batch.getBatchNo(),UdiStatusEnum.IN_STOCK,UdiStatusEnum.IN_USE,operator());batch.setUdiStatus(UdiStatusEnum.IN_USE);}
                states.transition(id,batch.getBatchNo(),batch.getUdiStatus(),UdiStatusEnum.CONSUMED,operator());
            } else {
                batch.setUdiStatus(UdiStatusEnum.CONSUMED);
                if(batches.updateById(batch)!=1) throw new BizException(40900,"库存状态发生并发变化");
            }
            batch.setUdiStatus(UdiStatusEnum.CONSUMED);
            jdbc.update("UPDATE device_batch_load SET unload_time=CURRENT_TIMESTAMP(3) WHERE batch_id=? AND unload_time IS NULL",id);
        }
        return batch;
    }
    public ReagentBatch addManualStock(Long id,BigDecimal quantity,Long warehouseId){
        ReagentBatch batch=batches.lockById(id);
        if(batch==null)throw new BizException(40400,"库存批次不存在");
        warehouses.requireWarehouse(warehouseId);
        if(!Objects.equals(batch.getWarehouseId(),warehouseId) || batch.getOriginPurchaseOrderItemId()!=null
                || batch.getUdiStatus()!=UdiStatusEnum.IN_STOCK)throw new BizException(40001,"采购来源或终态批次请通过验收入口入库；跨库请走调拨");
        if(quantity==null || quantity.signum()<=0)throw new BizException(40001,"入库数量须大于零");
        batch.setStock(batch.getStock().add(quantity));
        if(batches.updateById(batch)!=1)throw new BizException(40900,"库存发生并发变化");return batch;
    }
    private String operator(){return String.valueOf(SecurityUtils.getCurrentUserId());}
}
