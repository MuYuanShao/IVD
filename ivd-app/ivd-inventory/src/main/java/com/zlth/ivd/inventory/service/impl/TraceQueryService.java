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
        var batches=jdbc.queryForList("""
                SELECT CAST(b.id AS CHAR) AS "batchId", b.batch_no AS "batchNo", b.reagent_id AS "reagentId",
                       b.udi_di AS "udiDi", b.stock AS "stock", b.unit AS "unit",
                       b.udi_status AS "status", b.expiry_date AS "expiryDate",
                       b.manufacture_date AS "productionDate", b.warehouse_id AS "warehouseId",
                       w.name AS "warehouseName", c.generic_name AS "reagentName",
                       c.specification AS "spec", c.manufacturer AS "manufacturer",
                       c.his_code AS "hisCode", c.gtin AS "gtin", c.insurance_code AS "insuranceCode",
                       c.registration_cert_no AS "registrationCertNo", c.registration_cert_no AS "nmpaCert"
                FROM reagent_batch b
                LEFT JOIN warehouse w ON w.id=b.warehouse_id
                LEFT JOIN reagent_catalog c ON c.id=b.reagent_id
                WHERE
                """+predicate("b",pairs.size())+" ORDER BY b.expiry_date,b.id",args.toArray());
        var ledger=jdbc.queryForList("SELECT r.*,w.name warehouse_name,w.level warehouse_level,f.name from_name,u.real_name operator_name FROM consumption_record r LEFT JOIN warehouse w ON w.id=r.warehouse_id LEFT JOIN warehouse f ON f.id=r.from_warehouse_id LEFT JOIN sys_user u ON u.id=r.operator_id WHERE "+predicate("r",pairs.size())+" ORDER BY r.occurred_at,r.id",args.toArray());
        List<Map<String,Object>> timeline=new ArrayList<>(); Set<Object> reqIds=new HashSet<>(),poItems=new HashSet<>();
        for(var r:ledger) {
            String type=String.valueOf(r.get("record_type"));
            String node="CONSUME".equals(type)?"消耗核销":"INBOUND".equals(type)?"验收入库":Objects.equals(r.get("warehouse_level"),2)?"调拨至科室":"下发至组库";
            Object deferred = r.get("stock_deduct_on_receipt");
            if ("TRANSFER".equals(type) && (Boolean.TRUE.equals(deferred) || "1".equals(String.valueOf(deferred))))
                node = "中心库拣配预占";
            String detail=r.get("batch_no")+" · 数量 "+r.get("quantity")+" "+(r.get("unit")==null?"（历史单位未记录）":r.get("unit"));
            if("TRANSFER".equals(type)) detail+=" · "+r.get("from_name")+" → "+r.get("warehouse_name");
            timeline.add(event(node,"consumption_record",r.get("id"),r.get("occurred_at"),detail,r.get("operator_name"),r.get("warehouse_name")));
            if(r.get("requisition_id")!=null) reqIds.add(r.get("requisition_id"));
            if(r.get("purchase_order_item_id")!=null) poItems.add(r.get("purchase_order_item_id"));
            String settlementType = switch (type) {
                case "CONSUME" -> "CONSUMPTION";
                case "INBOUND" -> "INBOUND";
                case "TRANSFER" -> "TRANSFER";
                default -> null;
            };
            var settlements = settlementType == null ? List.<Map<String, Object>>of()
                    : jdbc.queryForList("SELECT id,settlement_no,settled_at,status,amount FROM settlement_record WHERE source_id=? AND settlement_type=?",
                            r.get("id"), settlementType);
            if ("TRANSFER".equals(type) && r.get("requisition_id") != null) {
                for (var receipt : jdbc.queryForList("SELECT * FROM requisition_transfer_receipt WHERE transfer_record_id=?", r.get("id"))) {
                    boolean recovered = "STOCK_POSTING_RECOVERY".equals(receipt.get("evidence_source"));
                    timeline.add(event(recovered ? "历史入库证据" : "科室签收", "requisition_transfer_receipt", receipt.get("id"), receipt.get("received_at"),
                            detail + (recovered ? " · 来源：目标批次创建记录，非人工签收时间" : ""), receipt.get("received_by"), r.get("warehouse_name")));
                }
            }
            settlements.forEach(s->timeline.add(event("结算","settlement_record",s.get("id"),s.get("settled_at"),s.get("settlement_no")+" · "+s.get("status")+" · 金额 "+s.get("amount"),null,null)));
        }
        for(Object reqId:reqIds) {
            for(var r:jdbc.queryForList("SELECT * FROM purchase_requisition WHERE id=?",reqId)) {
                timeline.add(event("申领单","purchase_requisition",reqId,r.get("created_at"),String.valueOf(r.get("req_no")),null,null));
                if(r.get("review_time")!=null) timeline.add(event("申领审核","purchase_requisition",reqId,r.get("review_time"),String.valueOf(r.get("req_no")),null,null));
            }
            jdbc.queryForList("SELECT * FROM settlement_record WHERE settlement_type='REQUISITION' AND source_id=?",reqId).forEach(s->timeline.add(event("结算","settlement_record",s.get("id"),s.get("settled_at"),s.get("settlement_no")+" · 原记录批号 "+s.get("batch_no")+" · "+s.get("status"),null,null)));
        }
        Set<Object> orderIds=new HashSet<>(), planIds=new HashSet<>();
        for(Object itemId:poItems) for(var p:jdbc.queryForList("""
                SELECT p.id,p.po_no,p.created_at,pp.id AS linked_plan_id,pp.plan_no,pp.created_at AS plan_created_at,
                       pp.created_by AS plan_created_by
                FROM purchase_order p
                JOIN purchase_order_item i ON i.po_id=p.id AND i.institution_id=p.institution_id
                LEFT JOIN purchase_plan pp ON pp.id=p.plan_id AND pp.institution_id=p.institution_id AND p.approval_origin='PLAN'
                WHERE i.id=?
                """, itemId)) {
            if(orderIds.add(p.get("id"))) timeline.add(event("采购订单","purchase_order",p.get("id"),p.get("created_at"),String.valueOf(p.get("po_no")),null,null));
            Object planId = p.get("linked_plan_id");
            if (planId == null || !planIds.add(planId)) continue;
            String planNo = String.valueOf(p.get("plan_no"));
            timeline.add(event("采购计划","purchase_plan",planId,p.get("plan_created_at"),planNo,p.get("plan_created_by"),null));
            for (var decision : jdbc.queryForList("""
                    SELECT id,to_status,operator_id,created_at FROM purchase_plan_event
                    WHERE plan_id=? AND to_status IN ('PENDING_REVIEW','APPROVED','REJECTED') ORDER BY id
                    """, planId)) {
                String stage = switch (String.valueOf(decision.get("to_status"))) {
                    case "APPROVED" -> "采购计划审批通过";
                    case "REJECTED" -> "采购计划审批驳回";
                    default -> "采购计划提交审批";
                };
                timeline.add(event(stage,"purchase_plan_event",decision.get("id"),decision.get("created_at"),
                        planNo,decision.get("operator_id"),null));
            }
        }
        for(var load:jdbc.queryForList("SELECT l.*,d.name device_name FROM device_batch_load l LEFT JOIN device d ON d.id=l.device_id WHERE "+predicate("l",pairs.size()),args.toArray())) {
            timeline.add(event("扫码上机","device_batch_load",load.get("id"),load.get("load_time"),load.get("batch_no")+" · "+load.get("device_name"),load.get("operator_id"),null));
            if(load.get("unload_time")!=null) timeline.add(event("退机","device_batch_load",load.get("id"),load.get("unload_time"),String.valueOf(load.get("device_name")),null,null));
        }
        for (var status : jdbc.queryForList("""
                SELECT l.id, l.batch_no, l.from_status, l.to_status, l.occurred_at, l.operator_id,
                       l.operator_name
                FROM udi_status_log l
                JOIN reagent_batch b ON b.id=l.batch_id AND b.institution_id=l.institution_id
                WHERE
                """ + predicate("b", pairs.size()), args.toArray())) {
            timeline.add(event("UDI 状态流转", "udi_status_log", status.get("id"), status.get("occurred_at"),
                    status.get("batch_no") + " · " + Objects.toString(status.get("from_status"), "-")
                            + " → " + status.get("to_status"),
                    status.get("operator_name") != null ? status.get("operator_name") : status.get("operator_id"),
                    null));
        }
        timeline.sort(Comparator.<Map<String,Object>,String>comparing(row->String.valueOf(row.getOrDefault("timestamp","9999")))
                .thenComparingInt(row->switch(String.valueOf(row.get("source"))) {
                    case "purchase_plan" -> 0;
                    case "purchase_plan_event" -> 1;
                    case "purchase_order" -> 2;
                    default -> 3;
                }));
        Map<String,Object> result=new LinkedHashMap<>();
        if(pairs.size()==1) result.putAll(batches.get(0));
        result.put("batches",batches);result.put("timeline",timeline);
        result.put("batchNo",pairs.size()==1?pairs.get(0).get("batch_no"):"多个批号，请查看批次列表");
        result.put("status",batches.size()==1?batches.get(0).get("status"):"MULTI_BATCH");result.put("statusLabel",batches.size()==1?batches.get(0).get("status"):"跨库/多批次");
        if(pairs.stream().map(p->p.get("reagent_id")).distinct().count()==1) result.put("stock",batches.stream().map(b->(BigDecimal)b.get("stock")).reduce(BigDecimal.ZERO,BigDecimal::add));
        result.put("limitations","仅展示已保存且可确认归属的流水。只有批号而无批次 ID 的旧状态日志不推断归属；多库库存逐行展示。");
        return result;
    }
    private String predicate(String alias,int size) { return "("+String.join(" OR ",Collections.nCopies(size,"("+alias+".reagent_id=? AND "+alias+".batch_no=?)"))+")"; }
    private Map<String,Object> event(String node,String source,Object id,Object time,String detail,Object operator,Object location) {
        Map<String,Object> event=new LinkedHashMap<>();event.put("node",node);event.put("status","completed");event.put("source",source);event.put("sourceId",String.valueOf(id));event.put("detail",detail);
        if(time!=null) event.put("timestamp",time.toString());if(operator!=null) event.put("operator",operator.toString());if(location!=null) event.put("location",location.toString());return event;
    }
}
