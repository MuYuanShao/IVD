ALTER TABLE reagent_catalog
    ADD COLUMN distributor VARCHAR(200) NULL COMMENT '配送商'
    AFTER manufacturer;
