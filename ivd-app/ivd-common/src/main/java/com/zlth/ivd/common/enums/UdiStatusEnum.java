package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * UDI 状态枚举（批次生命周期 — 11 状态）
 * <p>
 * 仅适用于 material_type ∈ {REAGENT, CALIBRATOR, QC_MATERIAL}
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum UdiStatusEnum {

    RECEIVED("RECEIVED", "已签收", false),
    IN_STOCK("IN_STOCK", "在库", false),
    PENDING_UDI("PENDING_UDI", "UDI待补全", false),
    TRANSFERRED("TRANSFERRED", "调拨在途", false),
    IN_USE("IN_USE", "使用中", false),
    CONSUMED("CONSUMED", "已消耗", true),
    RETURNED("RETURNED", "已退回", false),
    EXPIRED("EXPIRED", "已过期", true),
    RECALLED("RECALLED", "已召回", true),
    BACK_SUPPLIER("BACK_SUPPLIER", "已退供应商", true),
    SCRAPPED("SCRAPPED", "已报损", false),
    DESTROYED("DESTROYED", "已销毁", true);

    @EnumValue
    private final String code;
    private final String label;
    /** 是否为终态 */
    private final boolean terminal;

    UdiStatusEnum(String code, String label, boolean terminal) {
        this.code = code;
        this.label = label;
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
