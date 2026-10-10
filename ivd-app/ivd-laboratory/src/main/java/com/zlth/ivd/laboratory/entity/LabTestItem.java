package com.zlth.ivd.laboratory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.security.InstitutionOwnedEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_test_item")
public class LabTestItem extends InstitutionOwnedEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String code;
    private String name;
    private String methodology;
    private String resultType;
    private String unit;
    private Boolean active;
    @Version private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
