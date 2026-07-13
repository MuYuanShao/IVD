package com.zlth.ivd.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlth.ivd.inventory.entity.PatientAssociation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 患者关联 Mapper
 *
 * @author IVD Team
 * @since 2026-07-07
 */
@Mapper
public interface PatientAssociationMapper extends BaseMapper<PatientAssociation> {
}
