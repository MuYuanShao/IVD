package com.zlth.ivd.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 科室实体 — M00-2 科室管理
 *
 * @author IVD Team
 * @since 2026-07-13
 */
@Data
@TableName("sys_dept")
public class SysDept {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 科室名称 */
    private String deptName;

    /** 科室编码 */
    private String deptCode;

    /** 上级科室ID */
    private Long parentId;

    /** 排序序号 */
    private Integer sortOrder;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话 */
    private String contactPhone;

    /** 状态：1=启用 0=停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 软删除标记 */
    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
