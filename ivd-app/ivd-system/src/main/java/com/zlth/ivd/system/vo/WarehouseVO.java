package com.zlth.ivd.system.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 库房管理 VO — M00-3
 *
 * @author IVD Team
 * @since 2026-07-13
 */
@Data
public class WarehouseVO {
    private Long id;
    private String name;
    private String code;
    private Integer level;
    private String levelLabel;
    private Long parentId;
    private String parentName;
    private Long deptId;
    private String deptName;
    private String location;
    private String contactPerson;
    private String contactPhone;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
