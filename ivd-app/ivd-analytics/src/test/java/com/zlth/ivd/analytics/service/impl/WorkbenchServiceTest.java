package com.zlth.ivd.analytics.service.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlth.ivd.analytics.mapper.WorkbenchMapper;
import com.zlth.ivd.analytics.vo.WorkbenchVO;
import com.zlth.ivd.common.exception.BizException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkbenchServiceTest {
    private SqlSession session;
    private WorkbenchServiceImpl service;
    @BeforeEach void setup() throws Exception {
        var source=new JdbcDataSource(); source.setURL("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(var connection=source.getConnection()) { ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.support.EncodedResource(new ClassPathResource("workbench-h2.sql"),java.nio.charset.StandardCharsets.UTF_8)); }
        var config=new Configuration(new Environment("test",new JdbcTransactionFactory(),source));
        config.setMapUnderscoreToCamelCase(true); config.addMapper(WorkbenchMapper.class);
        session=new SqlSessionFactoryBuilder().build(config).openSession();
        service=new WorkbenchServiceImpl(session.getMapper(WorkbenchMapper.class),Clock.fixed(Instant.parse("2026-09-08T08:00:00Z"),ZoneId.of("Asia/Shanghai")));
    }
    @AfterEach void close() { session.close(); }
    @Test void dayBoundaryAndTransferSourceScope() {
        var dept=service.overview(2L); var all=service.overview(1L);
        assertEquals("3.21",dept.getMetrics().getConsumptionAmount().toPlainString());
        assertEquals("1002.21",all.getMetrics().getConsumptionAmount().toPlainString());
        assertEquals(1,dept.getMetrics().getInboundRecords()); assertEquals(1,dept.getMetrics().getTransferRecords());
        assertEquals(0,service.overview(3L).getMetrics().getTransferRecords());
        assertEquals("12.34",dept.getTrend().get(29).getInbound().toPlainString());
        assertEquals("5.25",dept.getTrend().get(29).getOutbound().toPlainString());
    }
    @Test void missingDaysAreZeroButMissingPricesAreNull() {
        var result=service.overview(2L);
        assertEquals(30,result.getTrend().size()); assertEquals(LocalDate.of(2026,8,10),result.getTrend().get(0).getDate());
        assertEquals(0,result.getTrend().get(0).getInbound().signum());
        assertNull(result.getTrend().get(28).getInbound()); assertEquals(1,result.getTrend().get(28).getUnpricedRecords());
    }
    @Test void alertsDoNotDoubleCountExpiredOrEmptyBatches() {
        var alerts=service.overview(2L).getAlerts();
        assertEquals(1,alerts.getExpiredBatches());assertEquals(1,alerts.getExpiringBatches());assertEquals(1,alerts.getPendingUdiBatches());assertEquals(1,alerts.getShortageOrders());
    }
    @Test void lineProgressAndPaginationPreserveGrainAndDecimalIds() {
        var result=service.overview(2L);assertEquals(1,result.getTodoTotal());
        assertEquals(2,result.getTodos().get(0).getLineCount());assertEquals(1,result.getTodos().get(0).getFulfilledLines());
        var first=service.products(2L,1,1,"");var second=service.products(2L,2,1,"");
        assertEquals(2,first.getTotal());assertEquals("9007199254740993",first.getRecords().get(0).getId());
        assertEquals("1.25",first.getRecords().get(0).getQuantity().toPlainString());assertNotEquals(first.getRecords().get(0).getId(),second.getRecords().get(0).getId());
        assertEquals(0,service.products(2L,1,5,"生产厂B").getTotal());
        assertEquals(1,service.products(1L,1,5,"生产厂B").getTotal()); // retired catalog still has real stock
        assertTrue(service.products(2L,100,5,"").getRecords().isEmpty());
    }
    @Test void authorizationFailsClosedAndInvalidPagingFailsBeforeQuery() {
        assertThrows(BizException.class,()->service.overview(null));assertThrows(BizException.class,()->service.overview(4L));assertThrows(BizException.class,()->service.overview(5L));assertThrows(BizException.class,()->service.overview(999L));
        assertThrows(BizException.class,()->service.products(1L,0,5,""));assertThrows(BizException.class,()->service.products(1L,1,101,""));assertThrows(BizException.class,()->service.products(1L,1,5,"x".repeat(101)));
        assertEquals(0,service.overview(6L).getMetrics().getConsumedRecords());assertTrue(service.overview(6L).getTodos().isEmpty());
    }
    @Test void nullableMoneySurvivesGlobalNonNullSerialization() throws Exception {
        var mapper=new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        var node=mapper.readTree(mapper.writeValueAsString(new WorkbenchVO.Metrics()));
        assertTrue(node.has("consumptionAmount"));assertTrue(node.get("consumptionAmount").isNull());
    }
    @Test void financeAndStorekeeperUseInstitutionSummaryWithoutInventingDepartment(){
        assertEquals(service.overview(1L).getMetrics().getConsumptionAmount(),service.overview(7L).getMetrics().getConsumptionAmount());
        assertEquals(service.products(1L,1,20,"").getTotal(),service.products(8L,1,20,"").getTotal());
        assertThrows(BizException.class,()->service.overview(4L));
    }
}
