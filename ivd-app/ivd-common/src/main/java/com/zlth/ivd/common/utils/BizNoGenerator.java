package com.zlth.ivd.common.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务单号生成器
 * <p>
 * 格式见规范附录 B，日期 + 自增序号。
 * 一期简化实现：使用 Redis 原子自增获取每日序号。
 *
 * @author IVD Team
 * @since 2026-07-01
 */
public final class BizNoGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private BizNoGenerator() {}

    /** 申领单号 REQ-YYYYMMDD-NNN */
    public static String requisition(LocalDate date, int seq) {
        return String.format("REQ-%s-%03d", date.format(DATE_FMT), seq);
    }

    /** 缺货单号 SH-YYYYMMDD-NNN */
    public static String shortage(LocalDate date, int seq) {
        return String.format("SH-%s-%03d", date.format(DATE_FMT), seq);
    }

    /** 采购订单号 PO-YYYYMMDD-NNNN */
    public static String purchaseOrder(LocalDate date, int seq) {
        return String.format("PO-%s-%04d", date.format(DATE_FMT), seq);
    }

    /** 结算单号 STL-YYYYMMDD-NNNN */
    public static String settlement(LocalDate date, int seq) {
        return String.format("STL-%s-%04d", date.format(DATE_FMT), seq);
    }

    /** 虚拟批号（耗材）BATCH-CONS-YYYYMMDD-NNN */
    public static String consumableBatch(LocalDate date, int seq) {
        return String.format("BATCH-CONS-%s-%03d", date.format(DATE_FMT), seq);
    }

    /** 院内编码 IVD-{组别缩写}-{序号} */
    public static String hisCode(String groupAbbr, int seq) {
        return String.format("IVD-%s-%03d", groupAbbr, seq);
    }
}
