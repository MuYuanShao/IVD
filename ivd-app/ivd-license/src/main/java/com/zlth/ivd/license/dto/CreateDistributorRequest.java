package com.zlth.ivd.license.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增配送商请求
 *
 * @author IVD Team
 * @since 2026-07-09
 */
@Data
public class CreateDistributorRequest {

    @NotBlank(message = "配送商名称不能为空")
    private String name;

    private String shortName;
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;
    private String address;
}
