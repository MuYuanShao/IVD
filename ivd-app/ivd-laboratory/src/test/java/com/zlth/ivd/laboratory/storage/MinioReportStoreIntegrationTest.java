package com.zlth.ivd.laboratory.storage;
import com.zlth.ivd.common.config.MinioProperties;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.laboratory.security.ClinicalCrypto;
import io.minio.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
/** Never uses a production bucket; credentials are deployment/test environment values only. */
@EnabledIfEnvironmentVariable(named="IVD_QA_MINIO_BUCKET",matches="ivd-lab-qa-[a-f0-9]{8}")
class MinioReportStoreIntegrationTest {
    @Test void actualPrivateMinioRoundTripAndControlledTaskCleanup() throws Exception {
        String bucket=System.getenv("IVD_QA_MINIO_BUCKET"),endpoint=System.getenv("IVD_QA_MINIO_ENDPOINT");
        assertTrue(bucket.matches("ivd-lab-qa-[a-f0-9]{8}"));assertEquals("http://127.0.0.1:9000",endpoint);
        var client=MinioClient.builder().endpoint(endpoint).credentials(System.getenv("IVD_QA_MINIO_ACCESS_KEY"),System.getenv("IVD_QA_MINIO_SECRET_KEY")).build();
        if("true".equals(System.getenv("IVD_QA_MINIO_CLEANUP"))){
            if(!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))return;
            var keys=new ArrayList<String>();for(var item:client.listObjects(ListObjectsArgs.builder().bucket(bucket).recursive(true).build())){String key=item.get().objectName();assertTrue(key.matches("1/lab-reports/[0-9]+/[a-f0-9]{32}\\.enc"),"Unexpected object in task bucket; abort cleanup");keys.add(key);}
            for(var key:keys)client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());return;
        }
        boolean created=!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());if(created)client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        var properties=new MinioProperties();properties.setBucketReport(bucket);var store=new MinioReportObjectStore(client,properties);
        String key="1/lab-reports/999/"+UUID.randomUUID().toString().replace("-","")+".enc";
        try{InstitutionContext.within(new InstitutionContext.Scope(1L,11L,101L,"LAB_TECH"),()->{
            var crypto=new ClinicalCrypto(Base64.getEncoder().encodeToString(new byte[32]),"fictional-independent-jwt");byte[] plain="仅限虚构私有报告测试".getBytes(java.nio.charset.StandardCharsets.UTF_8);byte[] encrypted=crypto.encrypt(plain);store.put(key,encrypted);assertArrayEquals(plain,crypto.decrypt(store.get(key)));assertFalse(new String(store.get(key),java.nio.charset.StandardCharsets.UTF_8).contains("仅限虚构"));return null;
        });
            var response=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(endpoint+"/"+bucket+"/"+key)).GET().build(),java.net.http.HttpResponse.BodyHandlers.discarding());assertEquals(403,response.statusCode());
        }finally{client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());if(created)client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());}
    }
}
