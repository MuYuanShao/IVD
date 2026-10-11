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
                .eq(ReagentBatch::getBatchNo, batchNo).eq(ReagentBatch::getWarehouseId, warehouseId)
                .orderByDesc(ReagentBatch::getInventoryGeneration).last("LIMIT 1"));
    }
    public Warehouse requireWarehouse(Long id) {
        Warehouse warehouse=warehouseMapper.selectById(id);
        if(warehouse==null || !Integer.valueOf(1).equals(warehouse.getStatus()))
            throw new com.zlth.ivd.common.exception.BizException(40001,"库房不存在、未启用或不属于当前机构");
        return warehouse;
    }
    public Warehouse findActiveCentralWarehouse() {
        var warehouses=warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getLevel,1)
                .eq(Warehouse::getStatus,1).orderByAsc(Warehouse::getId));
        if(warehouses.size()!=1) throw new com.zlth.ivd.common.exception.BizException(40001,"请明确选择本机构中心库");
        return warehouses.get(0);
    }
    public Warehouse requireDepartmentWarehouse(Long id,Long departmentId) {
        Warehouse warehouse=requireWarehouse(id), cursor=warehouse;
        if(warehouse.getLevel()==null || warehouse.getLevel()<2 || departmentId==null)
            throw new com.zlth.ivd.common.exception.BizException(40001,"申领目标必须是所属科室库或组库");
        java.util.Set<Long> visited=new java.util.HashSet<>();
        for(int depth=0;depth<3;depth++) {
            if(!visited.add(cursor.getId())) break;
            if(cursor.getDeptId()!=null) {
                if(!departmentId.equals(cursor.getDeptId())) break;
                return warehouse;
            }
            if(cursor.getParentId()==null) break;
            cursor=requireWarehouse(cursor.getParentId());
        }
        throw new com.zlth.ivd.common.exception.BizException(40300,"目标库房与申领科室不一致");
    }

    /** Warehouse lock serializes lifecycle allocation without reviving a terminal batch. */
    public ReagentBatch receiveIntoWarehouse(ReagentBatch source,Long targetId,java.math.BigDecimal quantity) {
        if(quantity==null || quantity.signum()<=0) throw new com.zlth.ivd.common.exception.BizException(40001,"入库数量必须大于0");
        requireWarehouse(targetId);
        warehouseMapper.selectOne(new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getId,targetId).last("FOR UPDATE"));
        var query=new LambdaQueryWrapper<ReagentBatch>().eq(ReagentBatch::getReagentId,source.getReagentId())
                .eq(ReagentBatch::getBatchNo,source.getBatchNo()).eq(ReagentBatch::getWarehouseId,targetId);
        var candidates=batchMapper.selectList(query.last("FOR UPDATE"));
        candidates.sort(java.util.Comparator.comparing(ReagentBatch::getInventoryGeneration,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        for(var candidate:candidates) {
            if(candidate.getUdiStatus()==UdiStatusEnum.RECALLED || candidate.getUdiStatus()==UdiStatusEnum.EXPIRED
                    || candidate.getUdiStatus()==UdiStatusEnum.DESTROYED)
                throw new com.zlth.ivd.common.exception.BizException(40001,"该批号存在召回、过期或销毁记录，不能自动补货");
            if(candidate.getUdiStatus()!=UdiStatusEnum.IN_STOCK && candidate.getUdiStatus()!=UdiStatusEnum.IN_USE) continue;
            boolean samePrice=candidate.getUnitPrice()==null?source.getUnitPrice()==null:
                    source.getUnitPrice()!=null && candidate.getUnitPrice().compareTo(source.getUnitPrice())==0;
            if(!java.util.Objects.equals(candidate.getOriginPurchaseOrderItemId(),source.getOriginPurchaseOrderItemId())
                    || !java.util.Objects.equals(candidate.getSettlementMode(),source.getSettlementMode()) || !samePrice) continue;
            if(!java.util.Objects.equals(candidate.getExpiryDate(),source.getExpiryDate())
                    || !java.util.Objects.equals(candidate.getUnit(),source.getUnit()))
                throw new com.zlth.ivd.common.exception.BizException(40900,"同批号来源的效期或单位不一致，请核对");
            candidate.setStock(candidate.getStock().add(quantity));
            if(!updateBatch(candidate)) throw new com.zlth.ivd.common.exception.BizException(40900,"入库库存发生并发修改");
            return candidate;
        }
        var created=new ReagentBatch();
        org.springframework.beans.BeanUtils.copyProperties(source,created,"id","version","createdAt","updatedAt","openDate","openExpiryDate");
        created.setInventoryGeneration(candidates.stream().map(ReagentBatch::getInventoryGeneration)
                .filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(-1)+1);
        created.setWarehouseId(targetId);created.setStock(quantity);created.setUdiStatus(UdiStatusEnum.IN_STOCK);
        createBatch(created);
        return created;
    }
    public ReagentBatch getBatch(Long batchId) { return batchMapper.selectById(batchId); }
    public ReagentBatch lockBatch(Long batchId){return batchMapper.lockById(batchId);}
    public ReagentBatch findUniqueLegacyBatch(Long reagentId,String batchNo,Long warehouseId) {
        var matches=batchMapper.selectList(new LambdaQueryWrapper<ReagentBatch>().eq(ReagentBatch::getReagentId,reagentId)
                .eq(ReagentBatch::getBatchNo,batchNo).eq(ReagentBatch::getWarehouseId,warehouseId).last("LIMIT 2"));
        if(matches.size()>1)throw new com.zlth.ivd.common.exception.BizException(40900,"历史发货未记录库存ID且存在多来源，请人工核对，禁止猜测来源");
        return matches.isEmpty()?null:matches.get(0);
    }
    public boolean updateBatch(ReagentBatch batch) {
        ReagentBatch persisted = batchMapper.lockById(batch.getId());
        if (persisted == null) return false;

        UdiStatusEnum desiredStatus = batch.getUdiStatus();
        UdiStatusEnum persistedStatus = persisted.getUdiStatus();
        boolean statusChanged = desiredStatus != null && desiredStatus != persistedStatus;
        if (statusChanged && batch.getMaterialType()!=null && batch.getMaterialType().entersUdiStateMachine()) {
            // 状态只能由 UDI 状态机持久化；先保存其余字段并保持数据库原状态。
            batch.setUdiStatus(persistedStatus);
        }
        if (batchMapper.updateById(batch) != 1) return false;
        if (statusChanged && batch.getMaterialType()!=null && batch.getMaterialType().entersUdiStateMachine()) {
            udiStateMachine.transition(batch.getId(), batch.getBatchNo(), persistedStatus, desiredStatus, operator());
            batch.setUdiStatus(desiredStatus);
        }
        return true;
    }
    public void createBatch(ReagentBatch batch) {
        UdiStatusEnum initialStatus = batch.getUdiStatus();
        if(batch.getMaterialType()!=null && !batch.getMaterialType().entersUdiStateMachine()) {
            batchMapper.insert(batch);return;
        }
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
