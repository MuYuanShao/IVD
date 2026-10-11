package com.zlth.ivd.inventory.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record DeviceRegistryRequest(@NotBlank @Size(max=64) String code,@NotBlank @Size(max=128) String name,
        @Size(max=64) String model,@Size(max=128) String manufacturer,@NotNull @Positive Long locationId,
        @Positive Long departmentId,@NotNull Boolean active,LocalDate lastCalibrationDate,LocalDate nextCalibrationDate,
        @Min(0) Integer version) { }
