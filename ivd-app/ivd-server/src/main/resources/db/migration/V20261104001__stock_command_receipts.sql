-- 库存请求回执与业务事务同生共灭；不回填或改变旧库存、流水及结算数据。
CREATE TABLE stock_command_receipt (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 institution_id BIGINT NOT NULL,
 operation_code VARCHAR(64) NOT NULL,
 request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_id BIGINT NOT NULL,
 request_hash CHAR(64) NOT NULL,
 response_json LONGTEXT NULL,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 completed_at DATETIME(3) NULL,
 UNIQUE KEY uk_stock_command(institution_id,operation_code,request_key),
 INDEX idx_stock_command_actor(institution_id,actor_id,created_at),
 CONSTRAINT ck_stock_command_complete CHECK ((completed_at IS NULL AND response_json IS NULL) OR (completed_at IS NOT NULL AND response_json IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$
CREATE TRIGGER stock_command_receipt_no_rewrite BEFORE UPDATE ON stock_command_receipt FOR EACH ROW
BEGIN
 IF OLD.completed_at IS NOT NULL OR NEW.id<>OLD.id OR NEW.institution_id<>OLD.institution_id
     OR NEW.operation_code<>OLD.operation_code OR NEW.request_key<>OLD.request_key
     OR NEW.actor_id<>OLD.actor_id OR NEW.request_hash<>OLD.request_hash OR NEW.created_at<>OLD.created_at THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Stock command receipt is immutable';
 END IF;
END$$
CREATE TRIGGER stock_command_receipt_no_delete BEFORE DELETE ON stock_command_receipt FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Stock command receipt cannot be deleted'$$
DELIMITER ;
