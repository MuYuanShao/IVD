-- Fix: batch_no should be unique per warehouse, not globally unique.
-- Transfers create same batch_no in different warehouses.
DROP INDEX idx_batch_no ON reagent_batch;
CREATE UNIQUE INDEX idx_batch_warehouse ON reagent_batch (batch_no, warehouse_id);
