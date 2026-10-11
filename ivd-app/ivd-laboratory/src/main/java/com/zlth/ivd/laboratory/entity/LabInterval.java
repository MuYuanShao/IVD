package com.zlth.ivd.laboratory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.security.InstitutionOwnedEntity;
import java.math.BigDecimal;
import com.zlth.ivd.laboratory.enums.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_interval")
public class LabInterval extends InstitutionOwnedEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long itemId;
    private Long deviceId;
    private IntervalKindEnum kind;
    private String methodology;
    private String unit;
    private LabResultTypeEnum resultType;
    private ReferenceSexEnum sex;
    private Integer minAgeDays;
    private Integer maxAgeDays;
    private BigDecimal lowerBound;
    private BigDecimal upperBound;
    private String normalText;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Boolean active;
    @Version private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
