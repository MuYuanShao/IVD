package com.zlth.ivd.common.annotation;

import java.lang.annotation.*;

/**
 * 审计日志注解 — 标注需记录操作日志的方法
 * <p>
 * 遵循 5W 原则: Who(操作人), When(时间), What(操作), Where(模块), Why(原因)
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 操作模块，如 "试剂目录"、"证照管理" */
    String module();

    /** 操作类型，如 "新增"、"编辑"、"删除"、"审批" */
    String action();

    /** 操作描述 SpEL 表达式，支持 #{#param.field} 引用方法参数 */
    String description() default "";
}
