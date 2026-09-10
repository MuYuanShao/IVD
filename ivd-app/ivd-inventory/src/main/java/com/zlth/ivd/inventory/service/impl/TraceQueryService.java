package com.zlth.ivd.inventory.service.impl;

import com.zlth.ivd.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;

/** Builds evidence-backed events; missing historical links are never invented. */
public class TraceQueryService {
    private final JdbcTemplate jdbc;
    public TraceQueryService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Map<String,Object> trace(String query,String lotNo) {
        if(query==null || query.isBlank()) throw new BizException(40001,"请输入UDI、批号或唯一码");
        String condition="(b.udi_di=? OR b.batch_no=? OR EXISTS(SELECT 1 FROM udi_unique_code u WHERE u.batch_id=b.id AND u.unique_code=?))";
        List<Object> search=new ArrayList<>(List.of(query.trim(),query.trim(),query.trim()));
        if(lotNo!=null && !lotNo.isBlank()) { condition+=" AND b.batch_no=?";search.add(lotNo.trim()); }
        var matches=jdbc.queryForList("SELECT b.reagent_id,b.batch_no FROM reagent_batch b WHERE "+condition,search.toArray());
        if(matches.isEmpty()) throw new BizException(40400,"未找到对应批次或唯一码");
        Map<String,Map<String,Object>> distinct=new LinkedHashMap<>();
        matches.forEach(row->distinct.put(row.get("reagent_id")+"|"+row.get("batch_no"),row));
        List<Map<String,Object>> pairs=new ArrayList<>(distinct.values());
        List<Object> args=new ArrayList<>();pairs.forEach(row->{args.add(row.get("reagent_id"));args.add(row.get("batch_no"));});
        var batches=jdbc.queryForList("SELECT CAST(b.id AS CHAR) batchId,b.batch_no batchNo,b.reagent_id reagentId,b.udi_di udiDi,b.stock,b.unit,b.udi_status status,b.expiry_date expiryDate,b.manufacture_date productionDate,b.warehouse_id warehouseId,w.name warehouseName,c.generic_name reagentName,c.specification spec,c.manufacturer,c.his_code hisCode,c.registration_cert_no registrationCertNo,c.insurance_code insuranceCode FROM reagent_batch b LEFT JOIN warehouse w ON w.id=b.warehouse_id LEFT JOIN reagent_catalog c ON c.id=b.reagent_id WHERE "+predicate("b",pairs.size())+" ORDER BY b.expiry_date,b.id",args.toArray());
        var ledger=jdbc.queryForList("SELECT r.*,w.name warehouse_name,w.level warehouse_level,f.name from_name,u.real_name operator_name FROM consumption_record r LEFT JOIN warehouse w ON w.id=r.warehouse_id LEFT JOIN warehouse f ON f.id=r.from_warehouse_id LEFT JOIN sys_user u ON u.id=r.operator_id WHERE "+predicate("r",pairs.size())+" ORDER BY r.occurred_at,r.id",args.toArray());
        List<Map<String,Object>> timeline=new ArrayList<>(); Set<Object> reqIds=new HashSet<>(),poItems=new HashSet<>();
        for(var r:ledger) {
            String type=String.valueOf(r.get("record_type"));
            String node="CONSUME".equals(type)?"消耗核销":"INBOUND".equals(type)?"验收入库":Objects.equals(r.get("warehouse_level"),2)?"调拨至科室":"下发至组库";
            String detail=r.get("batch_no")+" · 数量 "+r.get("quantity")+" "+(r.get("unit")==null?"（历史单位未记录）":r.get("unit"));
            if("TRANSFER".equals(type)) detail+=" · "+r.get("from_name")+" → "+r.get("warehouse_name");
            timeline.add(event(node,"consumption_record",r.get("id"),r.get("occurred_at"),detail,r.get("operator_name"),r.get("warehouse_name")));
            if(r.get("requisition_id")!=null) reqIds.add(r.get("requisition_id"));
            if(r.get("purchase_order_item_id")!=null) poItems.add(r.get("purchase_order_item_id"));
            var settlements=jdbc.queryForList("SELECT id,settlement_no,settled_at,status,amount FROM settlement_record WHERE source_id=? AND settlement_type=?",r.get("id"),"CONSUME".equals(type)?"CONSUMPTION":"TRANSFER");
            settlements.forEach(s->timeline.add(event("结算","settlement_record",s.get("id"),s.get("settled_at"),s.get("settlement_no")+" · "+s.get("status")+" · 金额 "+s.get("amount"),null,null)));
        }
        for(Object reqId:reqIds) {
            for(var r:jdbc.queryForList("SELECT * FROM purchase_requisition WHERE id=?",reqId)) {
                timeline.add(event("申领单","purchase_requisition",reqId,r.get("created_at"),String.valueOf(r.get("req_no")),null,null));
                if(r.get("review_time")!=null) timeline.add(event("申领审核","purchase_requisition",reqId,r.get("review_time"),String.valueOf(r.get("req_no")),null,null));
                if("FULFILLED".equals(r.get("status"))) timeline.add(event("科室签收","purchase_requisition",reqId,r.get("received_at"),r.get("req_no")+(r.get("received_at")==null?" · 历史签收时间未单独记录":""),null,null));
            }
            jdbc.queryForList("SELECT * FROM settlement_record WHERE settlement_type='REQUISITION' AND source_id=?",reqId).forEach(s->timeline.add(event("结算","settlement_record",s.get("id"),s.get("settled_at"),s.get("settlement_no")+" · 原记录批号 "+s.get("batch_no")+" · "+s.get("status"),null,null)));
        }
        Set<Object> orderIds=new HashSet<>();
        for(Object itemId:poItems) for(var p:jdbc.queryForList("SELECT p.id,p.po_no,p.created_at FROM purchase_order p JOIN purchase_order_item i ON i.po_id=p.id WHERE i.id=?",itemId)) {
            if(orderIds.add(p.get("id"))) timeline.add(event("采购订单","purchase_order",p.get("id"),p.get("created_at"),String.valueOf(p.get("po_no")),null,null));
        }
        for(var load:jdbc.queryForList("SELECT l.*,d.name device_name FROM device_batch_load l LEFT JOIN device d ON d.id=l.device_id WHERE "+predicate("l",pairs.size()),args.toArray())) {
            timeline.add(event("扫码上机","device_batch_load",load.get("id"),load.get("load_time"),load.get("batch_no")+" · "+load.get("device_name"),load.get("operator_id"),null));
            if(load.get("unload_time")!=null) timeline.add(event("退机","device_batch_load",load.get("id"),load.get("unload_time"),String.valueOf(load.get("device_name")),null,null));
        }
        timeline.sort(Comparator.comparing(row->String.valueOf(row.getOrDefault("timestamp","9999"))));
        Map<String,Object> result=new LinkedHashMap<>();
        if(pairs.size()==1) result.putAll(batches.get(0));
        result.put("batches",batches);result.put("timeline",timeline);
        result.put("batchNo",pairs.size()==1?pairs.get(0).get("batch_no"):"多个批号，请查看批次列表");
        result.put("status",batches.size()==1?batches.get(0).get("status"):"MULTI_BATCH");result.put("statusLabel",batches.size()==1?batches.get(0).get("status"):"跨库/多批次");
        if(pairs.stream().map(p->p.get("reagent_id")).distinct().count()==1) result.put("stock",batches.stream().map(b->(BigDecimal)b.get("stock")).reduce(BigDecimal.ZERO,BigDecimal::add));
        result.put("limitations","仅展示已保存的流水及关联。未记录的历史上机、采购或签收时间不补造；多库库存逐行展示。");
        return result;
    }
    private String predicate(String alias,int size) { return "("+String.join(" OR ",Collections.nCopies(size,"("+alias+".reagent_id=? AND "+alias+".batch_no=?)"))+")"; }
    private Map<String,Object> event(String node,String source,Object id,Object time,String detail,Object operator,Object location) {
        Map<String,Object> event=new LinkedHashMap<>();event.put("node",node);event.put("status","completed");event.put("source",source);event.put("sourceId",String.valueOf(id));event.put("detail",detail);
        if(time!=null) event.put("timestamp",time.toString());if(operator!=null) event.put("operator",operator.toString());if(location!=null) event.put("location",location.toString());return event;
    }
}
