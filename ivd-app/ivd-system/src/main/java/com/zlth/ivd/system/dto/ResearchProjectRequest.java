package com.zlth.ivd.system.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ResearchProjectRequest {
    @NotBlank @Size(max=120) private String name;
    @Size(max=80) private String leader;
    @Size(max=500) private String remark;
    @NotNull @Min(0) @Max(9999) private Integer sortOrder = 0;
    @NotNull private Boolean enabled = true;
}
