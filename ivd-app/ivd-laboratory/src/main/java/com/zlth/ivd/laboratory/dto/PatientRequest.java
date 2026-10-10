package com.zlth.ivd.laboratory.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** Minimal demographics only; no identity document, diagnosis, phone or address. */
public record PatientRequest(
        @NotBlank @Size(max=64) String number,
        @NotBlank @Size(max=100) String name,
        @NotNull @Pattern(regexp="MALE|FEMALE|UNKNOWN") String sex,
        @PastOrPresent LocalDate birthDate,
        @PositiveOrZero Integer version) { }
