package com.zlth.ivd.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统用户实体 — M00-1 用户管理
 *
 * @author IVD Team
 * @since 2026-07-13
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @TableField(value = "institution_id", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long institutionId;

    /** 用户编码（业务唯一标识，格式 USER-YYYYMMDD-NNN） */
    private String userCode;

    /** 登录用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** BCrypt 加密密码 */
    private String password;

    /** 角色 */
    private String role;

    /** 所属科室ID */
    private Long deptId;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态：1=启用 0=停用 */
    private Integer status;

    /** 软删除标记 */
    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
