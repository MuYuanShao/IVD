package com.zlth.ivd.laboratory.dto;
import jakarta.validation.constraints.*;
/** Coded reasons prevent free-text patient information entering plaintext audit logs. */
public record PatientDepartmentRequest(@NotNull @Positive Long departmentId,@NotNull Boolean active,
        @NotNull @Pattern(regexp="CLINICAL_COLLABORATION|CORRECTION|REVOCATION") String purpose) { }
