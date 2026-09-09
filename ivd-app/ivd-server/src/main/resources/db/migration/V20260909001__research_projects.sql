CREATE TABLE research_project (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    leader VARCHAR(80) NULL,
    remark VARCHAR(500) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_research_project_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE purchase_requisition
    ADD COLUMN research_project_id BIGINT NULL COMMENT '可选科研项目',
    ADD COLUMN research_project_name VARCHAR(120) NULL COMMENT '申领创建时的科研项目名称快照',
    ADD INDEX idx_req_research_project (research_project_id),
    ADD CONSTRAINT fk_req_research_project FOREIGN KEY (research_project_id) REFERENCES research_project(id);
