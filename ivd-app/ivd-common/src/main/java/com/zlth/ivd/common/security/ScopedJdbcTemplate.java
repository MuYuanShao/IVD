package com.zlth.ivd.common.security;

import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/** Must be the sole application JdbcTemplate; bootstrap identity reads use a separate explicit boundary. */
public final class ScopedJdbcTemplate extends JdbcTemplate {
    private final boolean isolationEnabled;
    private final InstitutionSqlInterceptor interceptor = new InstitutionSqlInterceptor();
    public ScopedJdbcTemplate(DataSource dataSource, boolean isolationEnabled) {
        super(dataSource); this.isolationEnabled = isolationEnabled;
    }
    private String scoped(String sql) {
        if (!isolationEnabled) return sql;
        InstitutionContext.require();
        return interceptor.parserSingle(sql,null);
    }
    @Override public List<Map<String,Object>> queryForList(String sql,Object... args) { return super.queryForList(scoped(sql),args); }
    @Override public List<Map<String,Object>> queryForList(String sql) { return super.queryForList(scoped(sql)); }
    @Override public <T> List<T> queryForList(String sql,Class<T> type,Object... args) { return super.queryForList(scoped(sql),type,args); }
    @Override public <T> List<T> queryForList(String sql,Class<T> type) { return super.queryForList(scoped(sql),type); }
    @Override public <T> T queryForObject(String sql,Class<T> type,Object... args) { return super.queryForObject(scoped(sql),type,args); }
    @Override public <T> T queryForObject(String sql,Class<T> type) { return super.queryForObject(scoped(sql),type); }
    @Override public Map<String,Object> queryForMap(String sql,Object... args) { return super.queryForMap(scoped(sql),args); }
    @Override public Map<String,Object> queryForMap(String sql) { return super.queryForMap(scoped(sql)); }
    @Override public int update(String sql,Object... args) { return super.update(scoped(sql),args); }
    @Override public int update(String sql) { return super.update(scoped(sql)); }
    @Override public void query(String sql,org.springframework.jdbc.core.RowCallbackHandler handler,Object... args) { super.query(scoped(sql),handler,args); }
    @Override public void query(String sql,org.springframework.jdbc.core.RowCallbackHandler handler) { super.query(scoped(sql),handler); }
    @Override public <T> T query(String sql,org.springframework.jdbc.core.ResultSetExtractor<T> extractor,Object... args) { return super.query(scoped(sql),extractor,args); }
    @Override public <T> T query(String sql,org.springframework.jdbc.core.ResultSetExtractor<T> extractor) { return super.query(scoped(sql),extractor); }
    @Override public <T> List<T> query(String sql,org.springframework.jdbc.core.RowMapper<T> mapper,Object... args) { return super.query(scoped(sql),mapper,args); }
    @Override public <T> List<T> query(String sql,org.springframework.jdbc.core.RowMapper<T> mapper) { return super.query(scoped(sql),mapper); }
}
