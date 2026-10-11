package com.zlth.ivd.laboratory.dto;
import jakarta.validation.constraints.*;
public record ResultEvidenceRequest(@NotNull @Min(0) Integer resultVersion,@NotNull @Positive Long batchId,
        @Positive Long deviceLoadId,@Positive Long consumptionId) { }
