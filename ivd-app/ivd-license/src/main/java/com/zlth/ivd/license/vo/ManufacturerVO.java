package com.zlth.ivd.license.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 厂家视图对象
 *
 * @author IVD Team
 * @since 2026-07-09
 */
@Data
public class ManufacturerVO {

    private Long id;
    private String name;
    private String shortName;
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;
    private String address;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
