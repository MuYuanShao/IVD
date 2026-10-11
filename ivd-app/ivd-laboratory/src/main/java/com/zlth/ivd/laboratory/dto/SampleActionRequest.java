package com.zlth.ivd.laboratory.dto;

import jakarta.validation.constraints.*;

public record SampleActionRequest(@NotNull @Min(0) Integer version,
        @Size(max=200) String reason) { }
