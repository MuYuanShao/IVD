package com.zlth.ivd.system.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户 VO — M00-1
 *
 * @author IVD Team
 * @since 2026-07-13
 */
@Data
public class UserVO {
    private Long id;
    private String username;
    private String realName;
    private String role;
    private String roleLabel;
    private Long deptId;
    private String deptName;
    private String phone;
    private String email;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
