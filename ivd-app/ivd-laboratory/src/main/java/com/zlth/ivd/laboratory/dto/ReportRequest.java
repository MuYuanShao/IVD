package com.zlth.ivd.laboratory.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record ReportRequest(@NotBlank @Size(max=64) String number,@NotNull @Positive Long sampleId,
        @NotEmpty @Size(max=1000) List<@NotNull @Positive Long> resultIds,@Min(0) Integer version) { }
