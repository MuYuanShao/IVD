CREATE TABLE operation_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operator_id BIGINT NULL,
    operator_name VARCHAR(64) NOT NULL,
    module VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    request_method VARCHAR(16) NULL,
    request_path VARCHAR(255) NULL,
    handler VARCHAR(255) NOT NULL,
    client_ip VARCHAR(64) NULL,
    reason VARCHAR(500) NULL,
    success TINYINT(1) NOT NULL,
    error_message VARCHAR(1000) NULL,
    duration_ms BIGINT NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_operation_audit_operator (operator_id, occurred_at),
    INDEX idx_operation_audit_module (module, occurred_at),
    INDEX idx_operation_audit_path (request_path, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='接口操作审计日志（APPEND-ONLY）';

DELIMITER $$
CREATE TRIGGER trg_operation_audit_log_no_update
BEFORE UPDATE ON operation_audit_log
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operation_audit_log is append-only';
END$$

CREATE TRIGGER trg_operation_audit_log_no_delete
BEFORE DELETE ON operation_audit_log
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operation_audit_log is append-only';
END$$
DELIMITER ;
