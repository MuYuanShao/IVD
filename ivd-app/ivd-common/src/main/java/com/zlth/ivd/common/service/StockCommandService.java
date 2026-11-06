package com.zlth.ivd.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** 回执、库存、流水及结算同一事务，失败不留成功回执。 */
@Service
public class StockCommandService {
    public enum Operation { CONSUME, BATCH_CONSUME, ORDER_RECEIVE, DIRECT_RECEIVE }
    public static final String HEADER = "Idempotency-Key";
    public static final String CONFLICT_DETAIL = "STOCK_COMMAND_CONFLICT";
    public static final TypeReference<String> TEXT = new TypeReference<>() {};
    public static final TypeReference<Map<String,Object>> MAP = new TypeReference<>() {};
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public StockCommandService(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);}

    @Transactional(rollbackFor=Exception.class)
    public <T> T execute(Operation operation,String requestKey,Object input,TypeReference<T> responseType,Supplier<T> work){
        long actor=InstitutionContext.requireUserActor();
        var scope=InstitutionContext.require();
        if(Set.of("LAB_TECH","LAB_DOCTOR","LAB_DIRECTOR").contains(scope.role()) && scope.departmentId()==null)
            throw new BizException(40300,"临床操作需要明确科室归属");
        if(requestKey==null || !KEY.matcher(requestKey).matches())
            throw new BizException(40001,"缺少或无效的业务请求标识，请刷新页面后重试");
        String operationCode=operation.name();
        String fingerprint=fingerprint(input);
        LocalDateTime started=LocalDateTime.now();
        try {
            // 唯一键冲突会等待原事务提交或回滚；回滚后允许当前请求真正执行。
            jdbc.update("INSERT INTO stock_command_receipt(operation_code,request_key,actor_id,request_hash,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                    operationCode,requestKey,actor,fingerprint,started,started);
        }catch(DuplicateKeyException duplicate){
            var existing=jdbc.queryForList("SELECT actor_id,request_hash,response_json,completed_at FROM stock_command_receipt WHERE institution_id=? AND operation_code=? AND request_key=? FOR UPDATE",scope.institutionId(),operationCode,requestKey);
            if(existing.size()!=1)throw new BizException(40900,"业务请求尚未确认，请保留原标识后重试");
            var receipt=existing.get(0);
            if(!(receipt.get("actor_id") instanceof Number owner) || owner.longValue()!=actor || !fingerprint.equals(receipt.get("request_hash")))
                throw new BizException(40900,"业务请求标识与原操作不一致，请先核对业务结果",CONFLICT_DETAIL);
            if(receipt.get("completed_at")==null || !(receipt.get("response_json") instanceof String response))
                throw new BizException(40900,"业务请求尚未完成，请勿更换标识重复执行");
            try{return json.readerFor(responseType).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(response);}
            catch(JsonProcessingException invalid){throw new IllegalStateException("业务回执解析失败，未重复执行业务",invalid);}
        }
        T result=work.get();
        try {
            LocalDateTime finished=LocalDateTime.now();
            if(jdbc.update("UPDATE stock_command_receipt SET response_json=?,completed_at=?,updated_at=? WHERE institution_id=? AND operation_code=? AND request_key=? AND actor_id=? AND completed_at IS NULL",
                    json.writeValueAsString(result),finished,finished,scope.institutionId(),operationCode,requestKey,actor)!=1)
                throw new IllegalStateException("业务回执更新失败");
            return result;
        }catch(JsonProcessingException invalid){throw new IllegalStateException("业务回执保存失败，事务已回滚",invalid);}
    }

    /** 仅保存规范化摘要，不在回执中复制请求备注、原始报文或凭据。 */
    private String fingerprint(Object input){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(normalize(json.valueToTree(input)))));}
        catch(JsonProcessingException | NoSuchAlgorithmException failure){throw new IllegalStateException("计算业务请求摘要失败",failure);}
    }
    private JsonNode normalize(JsonNode node){
        if(node.isObject()){
            var ordered=new TreeMap<String,JsonNode>();node.fields().forEachRemaining(e->ordered.put(e.getKey(),e.getValue()));
            var result=json.createObjectNode();ordered.forEach((name,value)->result.set(name,normalize(value)));return result;
        }
        if(node.isArray()){var result=json.createArrayNode();node.forEach(value->result.add(normalize(value)));return result;}
        if(node.isNumber())return DecimalNode.valueOf(node.decimalValue().stripTrailingZeros());
        return node;
    }
}
