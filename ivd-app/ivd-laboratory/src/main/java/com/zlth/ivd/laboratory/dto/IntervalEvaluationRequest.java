package com.zlth.ivd.laboratory.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record IntervalEvaluationRequest(
        @NotNull @Positive Long itemId, @Positive Long deviceId,
        @NotBlank @Size(max=100) String methodology,
        @NotNull @Size(max=32) String unit,
        @NotNull @Pattern(regexp="MALE|FEMALE|UNKNOWN") String sex,
        @NotNull @Min(0) @Max(73000) Integer ageDays,
        @NotNull LocalDate sampleDate,
        @NotBlank @Size(max=200) String result,
        @NotNull @Pattern(regexp="REFERENCE|MEASUREMENT") String kind) { }
