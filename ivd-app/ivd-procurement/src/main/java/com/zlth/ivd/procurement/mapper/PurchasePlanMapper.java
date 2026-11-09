package com.zlth.ivd.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlth.ivd.procurement.entity.PurchasePlan;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PurchasePlanMapper extends BaseMapper<PurchasePlan> {
    @org.apache.ibatis.annotations.Select("""
            SELECT i.reagent_id AS "reagentId", SUM(i.quantity) AS "quantity"
            FROM purchase_plan_item i JOIN purchase_plan p ON p.id=i.plan_id AND p.institution_id=i.institution_id
            WHERE p.status IN ('PENDING_REVIEW','APPROVED') GROUP BY i.reagent_id
            """)
    java.util.List<java.util.Map<String,Object>> committedQuantities();
}
