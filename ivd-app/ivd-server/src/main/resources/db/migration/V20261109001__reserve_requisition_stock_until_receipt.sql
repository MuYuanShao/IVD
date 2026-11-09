-- Legacy transfers already deducted physical stock. Only new picks set the posting flag.
ALTER TABLE reagent_batch
    ADD COLUMN reserved_stock DECIMAL(10,2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_reagent_batch_reserved_stock CHECK (reserved_stock >= 0 AND reserved_stock <= stock);

ALTER TABLE consumption_record
    ADD COLUMN stock_deduct_on_receipt TINYINT(1) NOT NULL DEFAULT 0;
