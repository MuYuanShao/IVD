CREATE TABLE purchase_plan(
 id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,plan_no VARCHAR(64),
 source_type VARCHAR(32),shortage_order_id BIGINT,status VARCHAR(32),order_date DATE,
 expected_delivery_date DATE,settlement_mode VARCHAR(32),remark VARCHAR(1000),created_by BIGINT,
 reviewed_by BIGINT,reviewed_at TIMESTAMP,review_comment VARCHAR(1000),created_at TIMESTAMP,updated_at TIMESTAMP,
 UNIQUE(institution_id,plan_no),UNIQUE(institution_id,shortage_order_id));
CREATE TABLE purchase_plan_item(
 id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,plan_id BIGINT,line_no INT,
 reagent_id BIGINT,reagent_name VARCHAR(200),supplier_id INT,material_type VARCHAR(32),acceptance_method VARCHAR(16),quantity DECIMAL(10,2),
 unit_price DECIMAL(12,2),amount DECIMAL(12,2),suggestion_id BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP,
 UNIQUE(institution_id,plan_id,line_no),UNIQUE(institution_id,suggestion_id));
CREATE TABLE purchase_plan_event(
 id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,plan_id BIGINT,from_status VARCHAR(32),
 to_status VARCHAR(32),operator_id BIGINT,reason VARCHAR(1000),snapshot LONGTEXT,created_at TIMESTAMP);
CREATE TABLE smart_replenish_run(
 id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,analysis_days INT,batch_no VARCHAR(64),
 created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(institution_id,batch_no));
ALTER TABLE purchase_order ADD COLUMN plan_id BIGINT;
ALTER TABLE purchase_order ADD COLUMN approval_origin VARCHAR(32) DEFAULT 'LEGACY';
ALTER TABLE reagent_batch ADD COLUMN reserved_stock DECIMAL(10,2) NOT NULL DEFAULT 0;
ALTER TABLE consumption_record ADD COLUMN stock_deduct_on_receipt TINYINT NOT NULL DEFAULT 0;
