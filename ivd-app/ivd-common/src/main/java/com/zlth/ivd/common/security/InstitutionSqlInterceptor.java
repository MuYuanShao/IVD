package com.zlth.ivd.common.security;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.zlth.ivd.common.exception.BizException;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Shares the same checked SQL policy between MyBatis and native JDBC. */
public final class InstitutionSqlInterceptor extends TenantLineInnerInterceptor {
    private static final ThreadLocal<Set<String>> ALIASES = new ThreadLocal<>();
    private static final Pattern CTE = Pattern.compile("(?:\\bWITH\\s+|,\\s*)([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:\\([^)]*\\)\\s*)?AS\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Set<String> TABLES = Set.of(
            "institution","sys_user","sys_dept","warehouse","device","device_batch_load","reagent_batch","reagent_catalog",
            "consumption_record","purchase_requisition","purchase_requisition_item","purchase_order","purchase_order_item",
            "shortage_order","shortage_order_item","settlement_record","supplier","supplier_license","manufacturer","distributor",
            "reagent_supplier_rel","audit_rule","audit_decision_log","operation_audit_log","udi_unique_code","udi_unique_code_seq",
            "udi_status_log","udi_backlog","patient_association","inventory_opening_balance","requisition_transfer_receipt",
            "daily_inventory_snapshot","dept_reagent","five_code_mapping","license_change_log","catalog_approval_log",
            "catalog_field_option","research_project","smart_replenish_suggestion","qc_result","poct_device","ai_consultation_log");

    public InstitutionSqlInterceptor() { super(new InstitutionTenantHandler()); }
    @Override public net.sf.jsqlparser.expression.Expression buildTableExpression(net.sf.jsqlparser.schema.Table table,
            net.sf.jsqlparser.expression.Expression where,String whereSegment) {
        var institution = super.buildTableExpression(table,where,whereSegment);
        if(institution==null) return null;
        var scope=InstitutionContext.require();
        if(!Set.of("LAB_TECH","LAB_DOCTOR","LAB_DIRECTOR").contains(scope.role())) return institution;
        String name=table.getName().replace("`","").toLowerCase(Locale.ROOT);
        String column=switch(name) {
            case "purchase_requisition","shortage_order" -> "from_dept_id";
            case "patient_association","audit_decision_log","lab_sample","lab_sample_event","lab_result","lab_result_revision","lab_result_evidence","lab_report","lab_report_result","lab_report_event","lab_import_batch","lab_import_unit" -> "department_id";
            default -> null;
        };
        String prefix=table.getAlias()==null?table.getName():table.getAlias().getName();
        String condition=null;
        if(column!=null) condition=prefix+"."+column+"="+requireDepartment(scope);
        if(Set.of("reagent_batch","device","consumption_record").contains(name)) {
            long department=requireDepartment(scope);
            String warehouses="SELECT w.id FROM warehouse w LEFT JOIN warehouse p ON p.id=w.parent_id AND p.institution_id="+scope.institutionId()
                    +" LEFT JOIN warehouse g ON g.id=p.parent_id AND g.institution_id="+scope.institutionId()
                    +" WHERE w.institution_id="+scope.institutionId()+" AND (w.level=1 OR COALESCE(w.dept_id,p.dept_id,g.dept_id)="+department+")";
            String ownWarehouses="SELECT w.id FROM warehouse w LEFT JOIN warehouse p ON p.id=w.parent_id AND p.institution_id="+scope.institutionId()
                    +" LEFT JOIN warehouse g ON g.id=p.parent_id AND g.institution_id="+scope.institutionId()
                    +" WHERE w.institution_id="+scope.institutionId()+" AND COALESCE(w.dept_id,p.dept_id,g.dept_id)="+department;
            if(name.equals("device")) condition=prefix+".location_id IN ("+warehouses+")";
            else if(name.equals("reagent_batch")) condition=prefix+".warehouse_id IN ("+warehouses+")";
            else condition="("+prefix+".warehouse_id IN ("+ownWarehouses+") OR ("+prefix+".record_type='TRANSFER' AND "+prefix+".from_warehouse_id IN ("+ownWarehouses+")))";
        }
        if(condition==null) return institution;
        try { return new net.sf.jsqlparser.expression.operators.conditional.AndExpression(institution,
                new net.sf.jsqlparser.expression.Parenthesis(net.sf.jsqlparser.parser.CCJSqlParserUtil.parseCondExpression(condition))); }
        catch(net.sf.jsqlparser.JSQLParserException error){throw new IllegalStateException("科室隔离条件解析失败",error);}
    }
    private long requireDepartment(InstitutionContext.Scope scope) {
        if(scope.departmentId()==null) throw new BizException(40300,"临床用户必须配置所属科室");
        return scope.departmentId();
    }
    static boolean isCteAlias(String name) {
        Set<String> aliases = ALIASES.get();
        return aliases != null && aliases.contains(name.toLowerCase(Locale.ROOT));
    }
    private <T> T withAliases(String sql, Supplier<T> operation) {
        Set<String> aliases = new HashSet<>();
        var matches = CTE.matcher(sql);
        while (matches.find()) {
            String alias = matches.group(1).toLowerCase(Locale.ROOT);
            if (TABLES.contains(alias) || alias.startsWith("lab_"))
                throw new BizException(40001, "CTE不得遮蔽机构业务表");
            aliases.add(alias);
        }
        Set<String> previous = ALIASES.get(); ALIASES.set(aliases);
        try { return operation.get(); }
        finally { if(previous == null) ALIASES.remove(); else ALIASES.set(previous); }
    }
    @Override public String parserSingle(String sql, Object object) {
        return withAliases(sql, () -> super.parserSingle(sql,object));
    }
    @Override public String parserMulti(String sql, Object object) {
        return withAliases(sql, () -> super.parserMulti(sql,object));
    }
}
