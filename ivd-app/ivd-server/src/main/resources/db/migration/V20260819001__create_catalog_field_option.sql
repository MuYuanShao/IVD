-- 试剂目录可配置下拉选项
CREATE TABLE IF NOT EXISTS `catalog_field_option` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '配置项ID',
    `field_code`   VARCHAR(48)  NOT NULL COMMENT '字段分类编码',
    `option_label` VARCHAR(128) NOT NULL COMMENT '显示名称',
    `option_value` VARCHAR(128) NOT NULL COMMENT '存储值',
    `sort_order`   INT          NOT NULL DEFAULT 99 COMMENT '排序号，升序',
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    `remark`       VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_catalog_field_option` (`field_code`, `option_value`, `is_deleted`),
    KEY `idx_catalog_field_option_list` (`field_code`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试剂目录字段下拉配置';

INSERT INTO `catalog_field_option`
    (`field_code`, `option_label`, `option_value`, `sort_order`, `status`)
VALUES
    ('TEST_GROUP', '生化', '生化', 10, 1),
    ('TEST_GROUP', '免疫', '免疫', 20, 1),
    ('TEST_GROUP', '临检', '临检', 30, 1),
    ('TEST_GROUP', '微生物', '微生物', 40, 1),
    ('TEST_GROUP', '分子', '分子', 50, 1),
    ('DETECTION_METHOD', '免疫抑制法', '免疫抑制法', 10, 1),
    ('DETECTION_METHOD', '速率法', '速率法', 20, 1),
    ('DETECTION_METHOD', '酶法', '酶法', 30, 1),
    ('DETECTION_METHOD', '电化学检测法', '电化学检测法', 40, 1),
    ('DETECTION_METHOD', '凝固法', '凝固法', 50, 1),
    ('DETECTION_METHOD', 'HPLC法', 'HPLC法', 60, 1),
    ('DETECTION_METHOD', '免疫比浊法', '免疫比浊法', 70, 1),
    ('DETECTION_METHOD', '化学发光法', '化学发光法', 80, 1),
    ('VOLUME_TYPE', '江西牵头生化试剂联盟', '江西牵头生化试剂联盟', 10, 1),
    ('VOLUME_TYPE', '安徽牵头免疫 / 分子诊断联盟', '安徽牵头免疫 / 分子诊断联盟', 20, 1),
    ('INSURANCE_PAYMENT_TYPE', '甲类', '甲类', 10, 1),
    ('INSURANCE_PAYMENT_TYPE', '乙类', '乙类', 20, 1),
    ('INSURANCE_PAYMENT_TYPE', '丙类', '丙类', 30, 1),
    ('INSURANCE_PAYMENT_TYPE', '自费', '自费', 40, 1);
