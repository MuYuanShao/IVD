package com.zlth.ivd.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zlth.ivd.common.enums.FiveCodeMappingStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 五码映射表 Entity
 *
 * @author IVD Team
 * @since 2026-07-03
 */
@Data
@TableName("five_code_mapping")
public class FiveCodeMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联试剂ID（唯一） */
    private Long reagentId;

    /** UDI-DI */
    private String udiDi;

    /** GTIN */
    private String gtin;

    /** 医保编码 */
    private String insuranceCode;

    /** NMPA 注册证编号 */
    private String nmpaRegNo;

    /** 院内 HIS 编码 */
    private String hisCode;

    /** 映射状态 */
    private FiveCodeMappingStatusEnum mappingStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
