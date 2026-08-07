package com.zlth.ivd.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 对象存储配置属性
 * <p>
 * 绑定 application.yml 中 {@code minio.*} 配置块，
 * 支持开发环境默认值与生产环境环境变量覆盖。
 *
 * @author IVD Team
 * @since 2026-07-21
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** MinIO 服务端点（如 http://localhost:9000） */
    private String endpoint;

    /** 访问密钥 */
    private String accessKey;

    /** 秘密密钥 */
    private String secretKey;

    /** 证照附件 Bucket */
    private String bucketCert;

    /** 报表/报告 Bucket（预留） */
    private String bucketReport;
}
