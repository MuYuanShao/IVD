package com.zlth.ivd.common.enums;

import com.zlth.ivd.common.exception.BizException;
import lombok.Getter;

/** Existing purchase-mode labels, kept compatible with historical orders and UI. */
@Getter
public enum SettlementModeEnum {
    CONSUMPTION("消耗结算"), REQUISITION("领用结算"), INBOUND("验收结算"), TRANSFER("调拨结算");
    private final String label;
    SettlementModeEnum(String label) { this.label=label; }
    public static SettlementModeEnum resolve(String value) {
        if(value==null || value.isBlank()) return null;
        for(var mode:values()) if(mode.label.equals(value) || mode.name().equals(value)) return mode;
        return null; // Historical unknown policies require manual provenance review, never guessed billing.
    }
    public static String normalizeNewOrder(String value) {
        if(value==null || value.isBlank()) return TRANSFER.label;
        var mode=resolve(value);
        if(mode==null) throw new BizException(40001,"不支持的结算模式");
        return mode.label;
    }
}
