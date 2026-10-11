package com.zlth.ivd.inventory.service.impl;

import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.enums.UdiStatusEnum;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.common.utils.IdGenerator;
import com.zlth.ivd.inventory.service.InstrumentUsageService;
import com.zlth.ivd.udi.service.UdiStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(rollbackFor=Exception.class)
public class InstrumentUsageServiceImpl implements InstrumentUsageService {
    private final JdbcTemplate jdbc;
    private final UdiStateMachine states;
    private final com.zlth.ivd.inventory.service.ProcurementInventoryService warehouses;
    @Override @Transactional(readOnly=true)
    public List<Map<String,Object>> devices() {
        return jdbc.queryForList("SELECT CAST(id AS CHAR) deviceId,device_code deviceCode,name deviceName,manufacturer,location_id warehouseId FROM device WHERE is_active=1 ORDER BY id");
    }
    @Override @Transactional(readOnly=true)
    public List<Map<String,Object>> activeLoads() {
        return jdbc.queryForList("SELECT CAST(l.id AS CHAR) loadId,CAST(l.batch_id AS CHAR) batchId,l.device_id deviceId,d.device_code deviceCode,d.name deviceName,d.manufacturer,l.batch_no loadedBatch,c.generic_name reagentName,l.load_time loadedAt,b.stock,b.unit,b.warehouse_id warehouseId,'IN_USE' status FROM device_batch_load l JOIN device d ON d.id=l.device_id LEFT JOIN reagent_catalog c ON c.id=l.reagent_id LEFT JOIN reagent_batch b ON b.id=l.batch_id WHERE l.unload_time IS NULL ORDER BY l.load_time DESC,l.id");
    }
    @Override public void bind(Long batchId,Long deviceId) {
        if(batchId==null || deviceId==null) throw new BizException(40001,"请选择库存批次和仪器");
        var devices=jdbc.queryForList("SELECT id,location_id FROM device WHERE id=? AND is_active=1 FOR UPDATE",deviceId);
        if(devices.isEmpty()) throw new BizException(40400,"仪器不存在或已停用");
        var batches=jdbc.queryForList("SELECT id,batch_no,reagent_id,warehouse_id,udi_status FROM reagent_batch WHERE id=? AND stock>0 AND expiry_date>=CURRENT_DATE FOR UPDATE",batchId);
        if(batches.isEmpty()) throw new BizException(40001,"批次无可用库存或效期未维护/已过期");
        var b=batches.get(0);
        com.zlth.ivd.common.security.InstitutionContext.current()
                .filter(scope->Set.of("LAB_TECH","LAB_DIRECTOR").contains(scope.role())).ifPresent(scope->{
                    if(!(b.get("warehouse_id") instanceof Number warehouse))throw new BizException(40001,"批次库房未维护");
                    warehouses.requireDepartmentWarehouse(warehouse.longValue(),scope.departmentId());
                });
        if(!(devices.get(0).get("location_id") instanceof Number dw) || !(b.get("warehouse_id") instanceof Number bw) || dw.longValue()!=bw.longValue()) throw new BizException(40001,"仪器与批次不在同一库房，请先调拨");
        String status=String.valueOf(b.get("udi_status"));
        if(!List.of("IN_STOCK","IN_USE").contains(status)) throw new BizException(40001,"该状态的批次不能上机");
        var existing=jdbc.queryForList("SELECT id FROM device_batch_load WHERE device_id=? AND batch_id=? AND unload_time IS NULL FOR UPDATE",deviceId,batchId);
        if(!existing.isEmpty()) return;
        jdbc.update("INSERT INTO device_batch_load(id,device_id,batch_id,batch_no,reagent_id,load_time,operator_id) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP(3),?)",IdGenerator.nextId(),deviceId,batchId,b.get("batch_no"),b.get("reagent_id"),SecurityUtils.getCurrentUserId());
        if("IN_STOCK".equals(status)) states.transition(batchId,String.valueOf(b.get("batch_no")),UdiStatusEnum.IN_STOCK,UdiStatusEnum.IN_USE,String.valueOf(SecurityUtils.getCurrentUserId()));
    }
    @Override @Transactional(readOnly=true)
    public List<Map<String,Object>> availableBatches(String keyword) {
        String q=keyword==null?"":keyword.trim();
        if(q.length()>100) throw new BizException(40001,"搜索词过长");
        return jdbc.queryForList("SELECT CAST(b.id AS CHAR) batchId,b.inventory_generation inventoryGeneration,CAST(b.origin_purchase_order_item_id AS CHAR) originPurchaseOrderItemId,b.settlement_mode settlementMode,b.unit_price unitPrice,b.batch_no batchNo,b.stock,COALESCE(b.unit,c.unit) unit,b.warehouse_id warehouseId,w.name warehouseName,c.generic_name reagentName,c.device_bind_mode deviceBindMode FROM reagent_batch b JOIN reagent_catalog c ON c.id=b.reagent_id LEFT JOIN warehouse w ON w.id=b.warehouse_id WHERE b.stock>0 AND w.level>=2 AND w.status=1 AND b.udi_status IN ('IN_STOCK','IN_USE') AND b.expiry_date>=CURRENT_DATE AND (?='' OR LOCATE(?,b.batch_no)>0 OR LOCATE(?,b.udi_di)>0 OR LOCATE(?,c.generic_name)>0) ORDER BY b.expiry_date,b.id LIMIT 100",q,q,q,q);
    }
    @Override public void unload(Long loadId) {
        var identity=jdbc.queryForList("SELECT device_id,batch_id FROM device_batch_load WHERE id=?",loadId);
        if(identity.isEmpty()) throw new BizException(40400,"上机记录不存在");
        jdbc.queryForList("SELECT id FROM device WHERE id=? FOR UPDATE",identity.get(0).get("device_id"));
        if(identity.get(0).get("batch_id")!=null) jdbc.queryForList("SELECT id FROM reagent_batch WHERE id=? FOR UPDATE",identity.get(0).get("batch_id"));
        var loads=jdbc.queryForList("SELECT * FROM device_batch_load WHERE id=? FOR UPDATE",loadId);
        if(loads.isEmpty()) throw new BizException(40400,"上机记录不存在");
        var load=loads.get(0); if(load.get("unload_time")!=null) return;
        jdbc.update("UPDATE device_batch_load SET unload_time=CURRENT_TIMESTAMP(3) WHERE id=?",loadId);
        if(load.get("batch_id")==null) return;
        var batch=jdbc.queryForList("SELECT id,batch_no,udi_status FROM reagent_batch WHERE id=? FOR UPDATE",load.get("batch_id"));
        var active=jdbc.queryForList("SELECT id FROM device_batch_load WHERE batch_id=? AND unload_time IS NULL FOR UPDATE",load.get("batch_id"));
        if(!batch.isEmpty() && active.isEmpty() && "IN_USE".equals(batch.get(0).get("udi_status"))) {
            Long batchId=((Number)load.get("batch_id")).longValue();
            String batchNo=String.valueOf(batch.get(0).get("batch_no"));
            String operator=String.valueOf(SecurityUtils.getCurrentUserId());
            states.transition(batchId,batchNo,UdiStatusEnum.IN_USE,UdiStatusEnum.RETURNED,operator);
            states.transition(batchId,batchNo,UdiStatusEnum.RETURNED,UdiStatusEnum.IN_STOCK,operator);
        }
    }
}
