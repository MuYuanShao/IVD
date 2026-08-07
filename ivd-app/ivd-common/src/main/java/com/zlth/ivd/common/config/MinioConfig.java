package com.zlth.ivd.common.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MinIO 客户端自动配置
 * <p>
 * 创建 {@link MinioClient} Bean 并在应用启动时确保所需 Bucket 已存在。
 * 开发环境自动创建，生产环境通过 docker-compose minio-init 预先创建。
 *
 * @author IVD Team
 * @since 2026-07-21
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties props;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
    }

    /**
     * 应用启动后检查并自动创建所需 Bucket。
     * 仅当 Bucket 不存在时才创建，已存在的跳过。
     */
    @Bean
    public ApplicationRunner minioBucketInitializer(MinioClient minioClient) {
        return args -> {
            List<String> buckets = List.of(props.getBucketCert(), props.getBucketReport());
            for (String bucket : buckets) {
                if (bucket == null || bucket.isEmpty()) {
                    continue;
                }
                try {
                    boolean exists = minioClient.bucketExists(
                            BucketExistsArgs.builder().bucket(bucket).build());
                    if (!exists) {
                        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                        log.info("MinIO bucket created: {}", bucket);
                    } else {
                        log.info("MinIO bucket already exists: {}", bucket);
                    }
                } catch (Exception e) {
                    log.error("Failed to initialize MinIO bucket: {}", bucket, e);
                }
            }
        };
    }
}
