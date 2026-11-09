package com.zlth.ivd.license.service;

import com.zlth.ivd.common.enums.LicenseStatusEnum;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Display and alert policy, independent of whether the asynchronous expiry job has run. */
public final class LicenseExpiryPolicy {
    private LicenseExpiryPolicy() {}

    public static LicenseStatusEnum status(LicenseStatusEnum persisted, LocalDate expiry, LocalDate today) {
        if (persisted == LicenseStatusEnum.FROZEN) return LicenseStatusEnum.FROZEN;
        if (persisted == LicenseStatusEnum.EXPIRED) return LicenseStatusEnum.EXPIRED;
        if (expiry == null || !expiry.isAfter(today)) return LicenseStatusEnum.EXPIRED;
        return expiry.isAfter(today.plusDays(30)) ? LicenseStatusEnum.ACTIVE : LicenseStatusEnum.EXPIRING;
    }

    public static Long remainingDays(LocalDate expiry, LocalDate today) {
        return expiry == null ? null : ChronoUnit.DAYS.between(today, expiry);
    }

    public static String reminderLevel(LicenseStatusEnum persisted, LocalDate expiry, LocalDate today) {
        if (persisted == LicenseStatusEnum.FROZEN) return "FROZEN";
        if (persisted == LicenseStatusEnum.EXPIRED) return "OVERDUE";
        Long days = remainingDays(expiry, today);
        if (days == null) return "UNKNOWN";
        if (days <= 0) return "OVERDUE";
        if (days <= 7) return "URGENT";
        if (days <= 30) return "EXPIRING";
        if (days <= 90) return "ATTENTION";
        return "NORMAL";
    }

    public static String reminderLabel(String level) {
        return switch (level) {
            case "FROZEN" -> "已冻结";
            case "OVERDUE" -> "已到期";
            case "URGENT" -> "紧急（7天内）";
            case "EXPIRING" -> "即将到期（30天内）";
            case "ATTENTION" -> "关注（90天内）";
            case "UNKNOWN" -> "效期待核对";
            default -> "有效";
        };
    }
}
