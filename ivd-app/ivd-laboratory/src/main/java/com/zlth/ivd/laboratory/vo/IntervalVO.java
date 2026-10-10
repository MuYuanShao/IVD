package com.zlth.ivd.laboratory.vo;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
public record IntervalVO(@JsonSerialize(using=ToStringSerializer.class) Long id,
        @JsonSerialize(using=ToStringSerializer.class) Long itemId,
        String itemCode,String itemName,
        @JsonSerialize(using=ToStringSerializer.class) Long deviceId,
        String kind,String methodology,String unit,String resultType,
        String sex,Integer minAgeDays,Integer maxAgeDays,
        @JsonSerialize(using=ToStringSerializer.class) BigDecimal lowerBound,
        @JsonSerialize(using=ToStringSerializer.class) BigDecimal upperBound,String normalText,
        LocalDate validFrom,LocalDate validTo,Boolean active,Integer version) { }
