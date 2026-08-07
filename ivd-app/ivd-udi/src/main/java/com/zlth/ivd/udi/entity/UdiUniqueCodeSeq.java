package com.zlth.ivd.udi.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 唯一码日序列 — 按天原子自增，保证并发安全
 *
 * @author IVD Team
 * @since 2026-07-23
 */
@Data
@TableName("udi_unique_code_seq")
public class UdiUniqueCodeSeq {

    /** 日期 yyyyMMdd */
    @TableId
    private String dateKey;

    /** 当前日最大已分配序号 */
    private Long currentSeq;
}
