package com.zlth.ivd.procurement.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 验收校验请求 DTO — UDI 扫码验收六项校验
 *
 * @author IVD Team
 * @since 2026-07-07
 */
public class InboundValidationRequest {

    @NotBlank(message = "UDI-DI不能为空")
    private String udiDi;

    @NotBlank(message = "批号不能为空")
    private String batchNo;

    private String expiryDate;
    private String certNo;
    private String licenseNo;

    public String getUdiDi() { return udiDi; }
    public void setUdiDi(String udiDi) { this.udiDi = udiDi; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    public String getCertNo() { return certNo; }
    public void setCertNo(String certNo) { this.certNo = certNo; }

    public String getLicenseNo() { return licenseNo; }
    public void setLicenseNo(String licenseNo) { this.licenseNo = licenseNo; }
}
