package com.zlth.ivd.laboratory.security;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlth.ivd.common.audit.*;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.common.utils.SecurityUtils;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
/** Clinical persistence never falls back to plaintext or omits its success evidence. */
@Component
public class ClinicalRecordSupport {
    private final ClinicalCrypto crypto;
    private final ObjectMapper json;
    private final Validator validator;
    private final OperationAuditLogWriter audits;
    public ClinicalRecordSupport(ClinicalCrypto crypto,ObjectMapper applicationJson,Validator validator,OperationAuditLogWriter audits){
        this.crypto=crypto;this.validator=validator;this.audits=audits;
        // Application responses historically use a space-only datetime serializer. Encrypted
        // documents need a symmetric, version-stable codec, without changing legacy APIs.
        var module=new com.fasterxml.jackson.databind.module.SimpleModule("clinical-document-iso-v1");
        module.addSerializer(LocalDateTime.class,new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        module.addDeserializer(LocalDateTime.class,new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        this.json=applicationJson.copy().registerModule(module).disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    public void validate(Object request){
        if(request==null || !validator.validate(request).isEmpty()) throw new BizException(40001,"临床资料字段校验失败");
    }
    public String encrypt(Object value){
        try{return crypto.encryptText(json.writeValueAsString(value));}
        catch(JsonProcessingException error){throw new BizException(50000,"临床资料序列化失败");}
    }
    public <T> T decrypt(String value,Class<T> type){
        try{return json.readValue(crypto.decryptText(value),type);}
        catch(JsonProcessingException error){throw new BizException(40900,"临床资料格式校验失败");}
    }
    public String index(String kind,String value){return crypto.index(kind,value);}
    public byte[] encryptBytes(byte[] value){return crypto.encrypt(value);}
    public byte[] decryptBytes(byte[] value){return crypto.decrypt(value);}
    public void audit(String module,String action,Long id){
        if(!crypto.available()) throw new BizException(50000,"临床加密密钥未配置");
        audits.appendDomain(OperationAuditEntry.builder().operatorId(InstitutionContext.requireUserActor())
                .operatorName(SecurityUtils.getCurrentUsername()).module(module).action(action)
                .handler(module+"Service").reason(id==null?"Authorized clinical list":"Record ID="+id)
                .success(true).durationMs(0L).occurredAt(LocalDateTime.now()).build());
    }
}
