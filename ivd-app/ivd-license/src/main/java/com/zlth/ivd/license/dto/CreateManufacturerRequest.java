package com.zlth.ivd.license.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增厂家请求
 *
 * @author IVD Team
 * @since 2026-07-09
 */
@Data
public class CreateManufacturerRequest {

    @NotBlank(message = "厂家名称不能为空")
    private String name;

    private String shortName;
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;
    private String address;
}
