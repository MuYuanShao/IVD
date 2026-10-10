package com.zlth.ivd.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlth.ivd.procurement.entity.PurchaseRequisition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PurchaseRequisitionMapper extends BaseMapper<PurchaseRequisition> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM purchase_requisition WHERE id=#{id} FOR UPDATE")
    Long lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
