package com.zlth.ivd.common.security;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.zlth.ivd.common.exception.BizException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.schema.Column;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Not automatically registered: enable only with the complete schema/request/JDBC/job rollout. */
public final class InstitutionTenantHandler implements TenantLineHandler {
    private static final Set<String> PLATFORM_TABLES = Set.of("institution", "flyway_schema_history");

    @Override public Expression getTenantId() {
        return new LongValue(InstitutionContext.require().institutionId());
    }
    @Override public String getTenantIdColumn() { return "institution_id"; }
    @Override public boolean ignoreTable(String tableName) {
        String normalized = tableName.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
        return PLATFORM_TABLES.contains(normalized) || InstitutionSqlInterceptor.isCteAlias(normalized);
    }
    @Override public boolean ignoreInsert(List<Column> columns, String tenantIdColumn) {
        // Even an INSERT that already contains institution_id still requires an authenticated scope.
        InstitutionContext.require();
        if (columns == null || columns.isEmpty())
            throw new BizException(40001, "机构业务写入必须声明字段列表");
        if (columns.stream().anyMatch(column -> tenantIdColumn.equalsIgnoreCase(column.getColumnName().replace("`", "").replace("\"", ""))))
            throw new BizException(40300, "机构归属由服务端生成，不能显式传入");
        return false;
    }
}
