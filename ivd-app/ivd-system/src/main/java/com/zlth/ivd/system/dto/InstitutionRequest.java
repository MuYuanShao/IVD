package com.zlth.ivd.system.dto;
import jakarta.validation.constraints.*;
public record InstitutionRequest(@NotBlank @Size(max=64) String code,@NotBlank @Size(max=200) String name,
        @NotBlank @Pattern(regexp="INTERNAL|EXTERNAL") String type,
        @Size(max=64) String contactPerson,@Size(max=32) String contactPhone,@Size(max=500) String address,
        @Size(max=5000) String capabilities,@NotNull @Min(0) @Max(1) Integer status) { }
