package com.zlth.ivd.common.utils;

import com.zlth.ivd.common.exception.BizException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 患者隐私保护工具 — SHA-256 哈希
 *
 * @author IVD Team
 * @since 2026-07-01
 */
public final class PhiUtils {

    private PhiUtils() {}

    /**
     * 对患者 ID 做 SHA-256 哈希脱敏
     */
    public static String hashPatientId(String patientId) {
        if (patientId == null || patientId.isBlank()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(patientId.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BizException(50005, "SHA-256 算法不可用", e);
        }
    }

    /**
     * 日志脱敏：仅打印前 8 位
     */
    public static String maskForLog(String patientIdHash) {
        if (patientIdHash == null || patientIdHash.length() <= 8) {
            return patientIdHash;
        }
        return patientIdHash.substring(0, 8) + "***";
    }
}
