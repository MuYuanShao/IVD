package com.zlth.ivd.license.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@Data
public class ApproveLicenseRenewalRequest {
    @NotBlank @Size(max=128)
    private String certNo;
    @NotNull
    private LocalDate issueDate;
    @NotNull
    private LocalDate expireDate;
    @NotBlank @Size(max=500)
    private String attachmentKey;
    @NotBlank @Size(max=500)
    private String reason;
}
