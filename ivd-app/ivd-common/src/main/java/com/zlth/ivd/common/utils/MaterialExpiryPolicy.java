package com.zlth.ivd.common.utils;

import com.zlth.ivd.common.enums.MaterialTypeEnum;
import java.time.LocalDate;

/** Missing expiry is allowed only for explicit quantity-received material types. */
public final class MaterialExpiryPolicy {
    private MaterialExpiryPolicy() {}
    public static boolean usable(MaterialTypeEnum type, LocalDate expiry, LocalDate today) {
        if (expiry != null) return !expiry.isBefore(today);
        return type == MaterialTypeEnum.CONSUMABLE || type == MaterialTypeEnum.OTHER;
    }
}
