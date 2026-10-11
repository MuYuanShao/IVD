package com.zlth.ivd.laboratory.dto;
import jakarta.validation.constraints.*;
public record DeviceCapabilityRequest(@NotNull @Positive Long itemId,@NotNull Boolean active,@Min(0) Integer version) { }
