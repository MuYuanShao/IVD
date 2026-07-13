package com.zlth.ivd.common.utils;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * 雪花 ID 生成器（分布式场景主键）
 * <p>
 * 单号格式见规范附录 B：
 * - 申领单号 REQ-YYYYMMDD-NNN
 * - 缺货单号 SH-YYYYMMDD-NNN
 * - 采购订单号 PO-YYYYMMDD-NNNN
 * - 结算单号 STL-YYYYMMDD-NNNN
 *
 * @author IVD Team
 * @since 2026-07-01
 */
public final class IdGenerator {

    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(1, 1);

    private IdGenerator() {}

    /** 雪花 ID（用于数据库主键） */
    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    /** 雪花 ID 字符串 */
    public static String nextIdStr() {
        return String.valueOf(SNOWFLAKE.nextId());
    }
}
