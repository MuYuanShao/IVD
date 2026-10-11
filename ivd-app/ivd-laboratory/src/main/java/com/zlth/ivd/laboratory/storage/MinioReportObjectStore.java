package com.zlth.ivd.laboratory.storage;
import com.zlth.ivd.common.config.MinioProperties;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import io.minio.*;
import java.io.ByteArrayInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class MinioReportObjectStore implements ReportObjectStore {
    private final MinioClient client;
    private final MinioProperties properties;
    private void validate(String key){
        if(!key.matches(InstitutionContext.require().institutionId()+"/lab-reports/[0-9]+/[a-f0-9]{32}\\.enc"))throw new BizException(40300,"报告对象归属无效");
    }
    private void requirePrivateBucket()throws Exception{
        try{String policy=client.getBucketPolicy(GetBucketPolicyArgs.builder().bucket(properties.getBucketReport()).build());if(policy!=null && !policy.isBlank())throw new BizException(50000,"报告存储必须为无公共策略的私有桶");}
        catch(io.minio.errors.ErrorResponseException error){if(!"NoSuchBucketPolicy".equals(error.errorResponse().code()))throw error;}
    }
    public void put(String key,byte[] encrypted){
        validate(key);if(encrypted.length>25*1024*1024)throw new BizException(40001,"报告对象超过安全大小限制");
        try{requirePrivateBucket();client.putObject(PutObjectArgs.builder().bucket(properties.getBucketReport()).object(key).stream(new ByteArrayInputStream(encrypted),encrypted.length,-1).contentType("application/octet-stream").build());}
        catch(BizException error){throw error;}catch(Exception error){throw new BizException(50000,"私有报告存储失败，报告未签发");}
    }
    public byte[] get(String key){
        validate(key);
        try{requirePrivateBucket();try(var input=client.getObject(GetObjectArgs.builder().bucket(properties.getBucketReport()).object(key).build())){byte[] data=input.readNBytes(25*1024*1024+1);if(data.length>25*1024*1024)throw new BizException(40900,"报告对象大小异常");return data;}}
        catch(BizException error){throw error;}catch(Exception error){throw new BizException(50000,"私有报告读取失败");}
    }
    public void remove(String key){validate(key);try{client.removeObject(RemoveObjectArgs.builder().bucket(properties.getBucketReport()).object(key).build());}catch(Exception error){throw new BizException(50000,"回滚报告对象清理失败，需管理员核对孤立对象");}}
}
