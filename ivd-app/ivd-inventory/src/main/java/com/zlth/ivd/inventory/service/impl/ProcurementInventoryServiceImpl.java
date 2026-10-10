package com.zlth.ivd.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.common.enums.UdiStatusEnum;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.inventory.entity.ReagentBatch;
import com.zlth.ivd.inventory.entity.Warehouse;
import com.zlth.ivd.inventory.mapper.ReagentBatchMapper;
import com.zlth.ivd.inventory.mapper.WarehouseMapper;
import com.zlth.ivd.inventory.service.ProcurementInventoryService;
import com.zlth.ivd.udi.service.UdiStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ProcurementInventoryServiceImpl implements ProcurementInventoryService {
    private final ReagentBatchMapper batchMapper;
    private final WarehouseMapper warehouseMapper;
    private final UdiStateMachine udiStateMachine;

    public Warehouse findActiveDeptWarehouse(Long deptId) {
        return warehouseMapper.selectOne(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getDeptId, deptId).eq(Warehouse::getLevel, 2).eq(Warehouse::getStatus, 1).last("LIMIT 1"));
    }
    public ReagentBatch findBatch(Long reagentId, String batchNo, Long warehouseId) {
        return batchMapper.selectOne(new LambdaQueryWrapper<ReagentBatch>()
                .eq(ReagentBatch::getReagentId, reagentId)
                .eq(ReagentBatch::getBatchNo, batchNo).eq(ReagentBatch::getWarehouseId, warehouseId).last("LIMIT 1"));
    }
    public ReagentBatch getBatch(Long batchId) { return batchMapper.selectById(batchId); }
    public boolean updateBatch(ReagentBatch batch) {
        ReagentBatch persisted = batchMapper.selectById(batch.getId());
        if (persisted == null) return false;

        UdiStatusEnum desiredStatus = batch.getUdiStatus();
        UdiStatusEnum persistedStatus = persisted.getUdiStatus();
        boolean statusChanged = desiredStatus != null && desiredStatus != persistedStatus;
        if (statusChanged) {
            // 状态只能由 UDI 状态机持久化；先保存其余字段并保持数据库原状态。
            batch.setUdiStatus(persistedStatus);
        }
        if (batchMapper.updateById(batch) != 1) return false;
        if (statusChanged) {
            udiStateMachine.transition(batch.getId(), batch.getBatchNo(), persistedStatus, desiredStatus, operator());
            batch.setUdiStatus(desiredStatus);
        }
        return true;
    }
    public void createBatch(ReagentBatch batch) {
        UdiStatusEnum initialStatus = batch.getUdiStatus();
        batch.setUdiStatus(UdiStatusEnum.RECEIVED);
        batchMapper.insert(batch);
        if (initialStatus != null && initialStatus != UdiStatusEnum.RECEIVED) {
            udiStateMachine.transition(batch.getId(), batch.getBatchNo(), UdiStatusEnum.RECEIVED,
                    initialStatus, operator());
            batch.setUdiStatus(initialStatus);
        }
    }
    public List<ReagentBatch> findInStockBatches(Long reagentId, Long warehouseId) {
        return batchMapper.selectList(new LambdaQueryWrapper<ReagentBatch>()
                .eq(ReagentBatch::getReagentId, reagentId).eq(ReagentBatch::getWarehouseId, warehouseId)
                .eq(ReagentBatch::getUdiStatus, UdiStatusEnum.IN_STOCK));
    }
    public List<ReagentBatch> listAllBatches() { return batchMapper.selectList(null); }

    private String operator() {
        Long userId = SecurityUtils.getCurrentUserIdOrNull();
        return userId != null ? userId.toString() : SecurityUtils.getCurrentUsername();
    }
}
