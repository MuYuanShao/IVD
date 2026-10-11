package com.zlth.ivd.laboratory.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/** Manual identifier; ownership and operator always come from the server. */
public record SampleRequest(@NotNull @Positive Long patientId,
        @NotBlank @Size(max=64) String number,
        @NotBlank @Size(max=64) String specimenType,
        @NotNull @PastOrPresent LocalDateTime collectedAt) { }
