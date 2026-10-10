package com.zlth.ivd.laboratory.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record IntervalRequest(
        @NotNull @Positive Long itemId,
        @Positive Long deviceId,
        @NotNull @Pattern(regexp="REFERENCE|MEASUREMENT") String kind,
        @NotNull @Pattern(regexp="ANY|MALE|FEMALE") String sex,
        @NotNull @Min(0) @Max(73000) Integer minAgeDays,
        @NotNull @Min(0) @Max(73000) Integer maxAgeDays,
        @Digits(integer=12,fraction=6) BigDecimal lowerBound,
        @Digits(integer=12,fraction=6) BigDecimal upperBound,
        @Size(max=200) String normalText,
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validTo,
        @NotNull Boolean active,
        @PositiveOrZero Integer version) { }
