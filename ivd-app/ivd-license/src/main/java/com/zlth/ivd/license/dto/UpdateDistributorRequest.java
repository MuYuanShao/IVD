package com.zlth.ivd.license.dto;

import lombok.Data;

/**
 * 编辑配送商请求
 *
 * @author IVD Team
 * @since 2026-07-09
 */
@Data
public class UpdateDistributorRequest {

    private String name;
    private String shortName;
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;
    private String address;
    private Boolean isActive;
}
