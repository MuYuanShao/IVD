package com.zlth.ivd.system.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 科室 VO — M00-2
 *
 * @author IVD Team
 * @since 2026-07-13
 */
@Data
public class DeptVO {
    private Long id;
    private String deptName;
    private String deptCode;
    private Long parentId;
    private String parentName;
    private Integer sortOrder;
    private String contactPerson;
    private String contactPhone;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
