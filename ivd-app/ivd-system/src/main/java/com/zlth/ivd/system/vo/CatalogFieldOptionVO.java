package com.zlth.ivd.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CatalogFieldOptionVO {
    private Long id;
    private String fieldCode;
    private String fieldLabel;
    private String optionLabel;
    private String optionValue;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
