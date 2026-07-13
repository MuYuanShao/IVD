package com.zlth.ivd.catalog.vo;

import lombok.Data;

/**
 * 五码映射 VO
 *
 * @author IVD Team
 * @since 2026-07-03
 */
@Data
public class FiveCodeMappingVO {

    private Long id;
    private Long reagentId;
    private String udiDi;
    private String gtin;
    private String insuranceCode;
    private String nmpaRegNo;
    private String hisCode;
    private String mappingStatus;
    private String mappingStatusLabel;
}
