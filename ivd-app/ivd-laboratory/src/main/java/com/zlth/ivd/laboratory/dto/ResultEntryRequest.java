package com.zlth.ivd.laboratory.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
public record ResultEntryRequest(@NotNull @Positive Long itemId,
        @NotNull @Pattern(regexp="INSTRUMENT|MANUAL|EXTERNAL") String executionMode,
        @Positive Long deviceId,@Positive Long testingInstitutionId,
        @NotBlank @Size(max=200) String result,@NotNull @Size(max=32) String unit,
        @NotNull @PastOrPresent LocalDateTime testedAt,@Min(0) Integer version) { }
