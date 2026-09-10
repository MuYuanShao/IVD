package com.zlth.ivd.settlement.service.impl;

import com.zlth.ivd.common.enums.RequisitionStatusEnum;
import com.zlth.ivd.procurement.entity.PurchaseRequisition;
import com.zlth.ivd.procurement.mapper.PurchaseRequisitionMapper;
import com.zlth.ivd.settlement.entity.SettlementRecord;
import com.zlth.ivd.settlement.mapper.SettlementRecordMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementEvidenceTest {
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void requisitionSettlementIsSplitByActualTransferBatch() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(1L,"test"));
        var req=new PurchaseRequisition();req.setId(1L);req.setReqNo("REQ-test");req.setStatus(RequisitionStatusEnum.FULFILLED);
        var reqs=mock(PurchaseRequisitionMapper.class);when(reqs.selectById(1L)).thenReturn(req);
        var records=mock(SettlementRecordMapper.class);
        var jdbc=mock(JdbcTemplate.class, invocation->{
            String method=invocation.getMethod().getName();
            if(method.equals("queryForObject")) return 0L;
            if(method.equals("queryForList")) {
                String sql=invocation.getArgument(0);
                if(sql.startsWith("SELECT id")) return List.of(Map.of("id",1L));
                return List.of(move(100L,"LOT-A",2),move(101L,"LOT-B",1));
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        new SettlementServiceImpl(records,null,reqs,null,null,jdbc).settleRequisition(1L);
        var captured=org.mockito.ArgumentCaptor.forClass(SettlementRecord.class);verify(records,times(2)).insert(captured.capture());
        assertEquals(List.of("LOT-A","LOT-B"),captured.getAllValues().stream().map(SettlementRecord::getBatchNo).toList());
        assertEquals(new BigDecimal("30.00"),captured.getAllValues().stream().map(SettlementRecord::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals("TRANSFER_RECORD",captured.getValue().getSourceItemKind());assertEquals(101L,captured.getValue().getSourceItemId());
    }
    private Map<String,Object> move(long id,String batch,int qty){return Map.of("id",id,"batch_no",batch,"reagent_id",4L,"quantity",BigDecimal.valueOf(qty),"unit_price",new BigDecimal("10.00"),"amount",new BigDecimal("10.00").multiply(BigDecimal.valueOf(qty)));}
    @Test void detailUsesRealSettlementAndNeverFansOutLegacyBatches() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);
        for(String sql:List.of(
                "CREATE TABLE consumption_record(id BIGINT,batch_id BIGINT,batch_no VARCHAR,record_type VARCHAR,reagent_id BIGINT,device_id BIGINT,warehouse_id BIGINT,operator_id INT,unit VARCHAR,unit_price DECIMAL(12,2),quantity DECIMAL(10,2),amount DECIMAL(12,2),occurred_at TIMESTAMP)",
                "CREATE TABLE settlement_record(id BIGINT,settlement_no VARCHAR,settlement_type VARCHAR,source_id BIGINT,status VARCHAR,settled_at TIMESTAMP)",
                "CREATE TABLE reagent_batch(id BIGINT,batch_no VARCHAR,reagent_id BIGINT,warehouse_id BIGINT,unit_price DECIMAL(12,2))",
                "CREATE TABLE reagent_catalog(id BIGINT,is_deleted INT,generic_name VARCHAR,specification VARCHAR,manufacturer VARCHAR,distributor VARCHAR,unit VARCHAR,is_volume_based INT,registration_cert_no VARCHAR,is_insurance_payment INT,volume_type VARCHAR,insurance_payment_type VARCHAR,is_chargeable INT)",
                "CREATE TABLE device(id BIGINT,device_code VARCHAR,name VARCHAR)","CREATE TABLE warehouse(id BIGINT,name VARCHAR)","CREATE TABLE sys_user(id BIGINT,real_name VARCHAR,dept_id BIGINT)","CREATE TABLE sys_dept(id BIGINT,dept_name VARCHAR)")) jdbc.execute(sql);
        jdbc.update("INSERT INTO consumption_record VALUES(10,NULL,'LOT','CONSUME',1,NULL,NULL,1,'box',10,1,10,CURRENT_TIMESTAMP),(11,NULL,'LOT','CONSUME',1,NULL,NULL,1,'box',10,1,10,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO reagent_batch VALUES(1,'LOT',1,1,10),(2,'LOT',1,2,10)");
        jdbc.update("INSERT INTO settlement_record VALUES(99,'REAL-99','CONSUMPTION',10,'ACTIVE',CURRENT_TIMESTAMP)");
        var result=new SettlementServiceImpl(null,null,null,null,null,jdbc).getConsumptionDetails(1,10,null,null,null);
        assertEquals(2,result.getTotal());
        var settled=result.getRecords().stream().filter(r->r.getConsumptionRecordId()==10L).findFirst().orElseThrow();
        assertEquals(99L,settled.getSettlementId());assertEquals("REAL-99",settled.getSettlementNo());assertEquals("LOT",settled.getBatchNo());
        var unsettled=result.getRecords().stream().filter(r->r.getConsumptionRecordId()==11L).findFirst().orElseThrow();
        assertNull(unsettled.getSettlementId());assertEquals("UNSETTLED",unsettled.getSettlementStatus());
    }
}
