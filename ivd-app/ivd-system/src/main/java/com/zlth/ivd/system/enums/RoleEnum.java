package com.zlth.ivd.system.enums;

import lombok.Getter;

/**
 * 系统角色枚举 — M00 用户管理
 *
 * @author IVD Team
 * @since 2026-07-13
 */
@Getter
public enum RoleEnum {

    SYSTEM_ADMIN("系统管理员"),
    PURCHASER("采购员"),
    STOREKEEPER("库管员"),
    LAB_TECH("检验技师"),
    LAB_DOCTOR("检验医师"),
    LAB_DIRECTOR("检验科主任"),
    FINANCE("财务人员"),
    IT_STAFF("信息科");

    private final String label;

    RoleEnum(String label) {
        this.label = label;
    }
}
