package com.zlth.ivd.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlth.ivd.procurement.entity.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {
    @org.apache.ibatis.annotations.Select("""
            SELECT i.reagent_id AS "reagentId", SUM(CASE
                WHEN i.order_quantity>COALESCE(i.received_quantity,0)
                THEN i.order_quantity-COALESCE(i.received_quantity,0) ELSE 0 END) AS "quantity"
            FROM purchase_order_item i JOIN purchase_order p ON p.id=i.po_id AND p.institution_id=i.institution_id
            WHERE p.status IN ('DRAFT','SENT','PARTIAL_RECEIVED') AND p.approval_origin IN ('LEGACY','PLAN')
            GROUP BY i.reagent_id
            """)
    java.util.List<java.util.Map<String,Object>> outstandingQuantities();
    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO receipt_virtual_batch_sequence(receipt_day,last_number) VALUES(#{day},1)
            ON DUPLICATE KEY UPDATE last_number=last_number+1
            """)
    int advanceVirtualBatch(@org.apache.ibatis.annotations.Param("day") String day);

    @org.apache.ibatis.annotations.Select("SELECT last_number FROM receipt_virtual_batch_sequence WHERE receipt_day=#{day} FOR UPDATE")
    int currentVirtualBatch(@org.apache.ibatis.annotations.Param("day") String day);
    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO purchase_order_number_sequence(order_day, last_number)
            VALUES(#{day}, #{size})
            ON DUPLICATE KEY UPDATE last_number = last_number + #{size}
            """)
    int advanceNumberSequence(
            @org.apache.ibatis.annotations.Param("day") String day,
            @org.apache.ibatis.annotations.Param("size") int size);

    @org.apache.ibatis.annotations.Select("""
            SELECT last_number FROM purchase_order_number_sequence
            WHERE order_day=#{day} FOR UPDATE
            """)
    int currentNumberSequence(@org.apache.ibatis.annotations.Param("day") String day);

    default PurchaseOrder lockById(Long id) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getId,id).last("FOR UPDATE"));
    }
}
