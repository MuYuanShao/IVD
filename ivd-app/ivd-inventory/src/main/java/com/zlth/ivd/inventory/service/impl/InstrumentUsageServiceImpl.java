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
import java.time.LocalDate;
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
        return jdbc.queryForList("SELECT TRIM(CAST(l.id AS CHAR(20))) loadId,TRIM(CAST(l.batch_id AS CHAR(20))) batchId,l.device_id deviceId,d.device_code deviceCode,d.name deviceName,d.manufacturer,l.batch_no loadedBatch,c.generic_name reagentName,l.load_time loadedAt,b.stock,b.unit,b.warehouse_id warehouseId,'IN_USE' status FROM device_batch_load l LEFT JOIN device d ON d.id=l.device_id LEFT JOIN reagent_catalog c ON c.id=l.reagent_id LEFT JOIN reagent_batch b ON b.id=l.batch_id WHERE l.unload_time IS NULL AND (l.device_id IS NULL OR d.id IS NOT NULL) ORDER BY l.load_time DESC,l.id");
    }
    @Override public void bind(Long batchId,Long deviceId) {
        if(batchId==null) throw new BizException(40001,"请选择库存批次");
        var devices=deviceId == null ? List.<Map<String,Object>>of()
                : jdbc.queryForList("SELECT id,location_id FROM device WHERE id=? AND is_active=1 FOR UPDATE",deviceId);
        if(deviceId != null && devices.isEmpty()) throw new BizException(40400,"仪器不存在或已停用");
        var batches=jdbc.queryForList("SELECT id,batch_no,reagent_id,warehouse_id,udi_status,material_type,expiry_date,open_date,open_expiry_date FROM reagent_batch WHERE id=? AND stock>0 AND expiry_date>=CURRENT_DATE FOR UPDATE",batchId);
        if(batches.isEmpty()) throw new BizException(40001,"批次无可用库存或效期未维护/已过期");
        var b=batches.get(0);
        var catalogs = jdbc.queryForList("SELECT material_type,device_bind_mode,stability_days_after_open FROM reagent_catalog WHERE id=? AND is_deleted=0", b.get("reagent_id"));
        var catalog = catalogs.isEmpty() ? Map.<String,Object>of() : catalogs.get(0);
        Object materialType = b.get("material_type") != null ? b.get("material_type") : catalog.get("material_type");
        if (deviceId == null && !"FLEXIBLE".equals(catalog.get("device_bind_mode")))
            throw new BizException(40001, "该批次必须指定仪器后才能上机");
        if (!Set.of("REAGENT", "CALIBRATOR", "QC_MATERIAL").contains(Objects.toString(materialType, "")))
            throw new BizException(40001, "该物料类型不支持上机");
        com.zlth.ivd.common.security.InstitutionContext.current()
                .filter(scope->Set.of("LAB_TECH","LAB_DIRECTOR").contains(scope.role())).ifPresent(scope->{
                    if(!(b.get("warehouse_id") instanceof Number warehouse))throw new BizException(40001,"批次库房未维护");
                    warehouses.requireDepartmentWarehouse(warehouse.longValue(),scope.departmentId());
                });
        if(deviceId != null && (!(devices.get(0).get("location_id") instanceof Number dw)
                || !(b.get("warehouse_id") instanceof Number bw) || dw.longValue()!=bw.longValue()))
            throw new BizException(40001,"仪器与批次不在同一库房，请先调拨");
        if (deviceId == null && (!(b.get("warehouse_id") instanceof Number bw)
                || warehouses.requireWarehouse(bw.longValue()).getLevel() < 2))
            throw new BizException(40001, "无仪器上机仅允许在科室库或组库登记");
        String status=String.valueOf(b.get("udi_status"));
        if(!List.of("IN_STOCK","IN_USE").contains(status)) throw new BizException(40001,"该状态的批次不能上机");
        LocalDate today = LocalDate.now();
        LocalDate openExpiry = dateValue(b.get("open_expiry_date"));
        if (openExpiry != null && openExpiry.isBefore(today))
            throw new BizException(40001, "批次已超过开瓶稳定期");
        var existing=deviceId == null
                ? jdbc.queryForList("SELECT id FROM device_batch_load WHERE device_id IS NULL AND batch_id=? AND unload_time IS NULL FOR UPDATE",batchId)
                : jdbc.queryForList("SELECT id FROM device_batch_load WHERE device_id=? AND batch_id=? AND unload_time IS NULL FOR UPDATE",deviceId,batchId);
        if(!existing.isEmpty()) return;
        if (b.get("open_date") == null) {
            var previousLoad = jdbc.queryForObject("SELECT MIN(load_time) FROM device_batch_load WHERE batch_id=?",
                    java.sql.Timestamp.class, batchId);
            LocalDate opened = previousLoad == null ? today : previousLoad.toLocalDateTime().toLocalDate();
            if (openExpiry == null && catalog.get("stability_days_after_open") instanceof Number days && days.longValue() >= 0) {
                LocalDate byStability = opened.plusDays(days.longValue());
                LocalDate labelExpiry = dateValue(b.get("expiry_date"));
                openExpiry = byStability.isBefore(labelExpiry) ? byStability : labelExpiry;
            }
            if (openExpiry != null && openExpiry.isBefore(today))
                throw new BizException(40001, "批次已超过开瓶稳定期");
            jdbc.update("UPDATE reagent_batch SET open_date=?,open_expiry_date=? WHERE id=? AND open_date IS NULL",
                    opened, openExpiry, batchId);
        }
        jdbc.update("INSERT INTO device_batch_load(id,device_id,batch_id,batch_no,reagent_id,load_time,operator_id) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP(3),?)",IdGenerator.nextId(),deviceId,batchId,b.get("batch_no"),b.get("reagent_id"),SecurityUtils.getCurrentUserId());
        if("IN_STOCK".equals(status)) states.transition(batchId,String.valueOf(b.get("batch_no")),UdiStatusEnum.IN_STOCK,UdiStatusEnum.IN_USE,String.valueOf(SecurityUtils.getCurrentUserId()));
    }
    private LocalDate dateValue(Object value) {
        return value == null ? null : LocalDate.parse(value.toString());
    }
    @Override @Transactional(readOnly=true)
    public List<Map<String,Object>> availableBatches(String keyword) {
        String q=keyword==null?"":keyword.trim();
        if(q.length()>100) throw new BizException(40001,"搜索词过长");
        return jdbc.queryForList("SELECT CAST(b.id AS CHAR) batchId,b.inventory_generation inventoryGeneration,CAST(b.origin_purchase_order_item_id AS CHAR) originPurchaseOrderItemId,b.settlement_mode settlementMode,b.unit_price unitPrice,b.batch_no batchNo,b.stock,COALESCE(b.unit,c.unit) unit,b.warehouse_id warehouseId,w.name warehouseName,c.generic_name reagentName,c.device_bind_mode deviceBindMode,COALESCE(b.material_type,c.material_type) materialType FROM reagent_batch b JOIN reagent_catalog c ON c.id=b.reagent_id LEFT JOIN warehouse w ON w.id=b.warehouse_id WHERE b.stock>0 AND w.level>=2 AND w.status=1 AND b.udi_status IN ('IN_STOCK','IN_USE') AND (b.expiry_date>=CURRENT_DATE OR (b.expiry_date IS NULL AND COALESCE(b.material_type,c.material_type) IN ('CONSUMABLE','OTHER'))) AND (?='' OR LOCATE(?,b.batch_no)>0 OR LOCATE(?,b.udi_di)>0 OR LOCATE(?,c.generic_name)>0) ORDER BY b.expiry_date,b.id LIMIT 100",q,q,q,q);
    }
    @Override public void unload(Long loadId) {
        var identity=jdbc.queryForList("SELECT device_id,batch_id FROM device_batch_load WHERE id=?",loadId);
        if(identity.isEmpty()) throw new BizException(40400,"上机记录不存在");
        if (identity.get(0).get("device_id") != null)
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
