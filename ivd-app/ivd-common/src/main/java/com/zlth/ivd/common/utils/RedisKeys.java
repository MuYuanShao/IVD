package com.zlth.ivd.common.utils;

/**
 * Redis Key 常量 — 统一管理所有 Redis Key 前缀
 * <p>
 * 命名规范：ivd:{module}:{entity}:{id}
 *
 * @author IVD Team
 * @since 2026-07-01
 */
public final class RedisKeys {

    private RedisKeys() {}

    private static final String PREFIX = "ivd";

    // ==================== 批次映射缓存（核心命脉） ====================

    /** 设备→活跃批次映射 (Sorted Set, member={batch_no}:{load}:{unload}, score=load_time) */
    public static String deviceBatches(String deviceId) {
        return PREFIX + ":device_batches:" + deviceId;
    }

    /** 批次详情缓存 (Hash, TTL 1h) */
    public static String batchInfo(String batchNo) {
        return PREFIX + ":batch:" + batchNo;
    }

    /** 批次移动加权平均成本 (String, 实时更新) */
    public static String batchAvgCost(String batchNo) {
        return PREFIX + ":batch_avg_cost:" + batchNo;
    }

    // ==================== 证照 ====================

    /** 证照到期预警队列 (Sorted Set, score=expire_timestamp) */
    public static String licenseExpireQueue() {
        return PREFIX + ":license_expire";
    }

    // ==================== 结算 ====================

    /** 结算防重锁 (String, TTL 30s) */
    public static String settlementLock(String sourceId) {
        return PREFIX + ":settlement_lock:" + sourceId;
    }

    // ==================== 业务自增序号 ====================

    /** 每日业务序号 (String, TTL 到当天结束) */
    public static String dailySeq(String prefix) {
        return PREFIX + ":seq:" + prefix;
    }

    // ==================== 用户会话 ====================

    /** JWT Token 黑名单 (String, TTL = token 过期时间) */
    public static String tokenBlacklist(String tokenId) {
        return PREFIX + ":token_blacklist:" + tokenId;
    }
}
