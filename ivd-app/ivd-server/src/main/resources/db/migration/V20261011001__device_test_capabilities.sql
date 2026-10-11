ALTER TABLE lab_test_item ADD COLUMN device_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE device ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE device ADD CONSTRAINT fk_device_department_owner FOREIGN KEY(institution_id,dept_id) REFERENCES sys_dept(institution_id,id);
CREATE TABLE lab_device_capability (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 institution_id BIGINT NOT NULL,
 device_id BIGINT NOT NULL,
 item_id BIGINT NOT NULL,
 active BOOLEAN NOT NULL,
 version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_lab_device_capability(institution_id,device_id,item_id),
 CONSTRAINT fk_lab_capability_device FOREIGN KEY(institution_id,device_id) REFERENCES device(institution_id,id),
 CONSTRAINT fk_lab_capability_item FOREIGN KEY(institution_id,item_id) REFERENCES lab_test_item(institution_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- No capability is inferred from a reference range, existing result or stock load.
