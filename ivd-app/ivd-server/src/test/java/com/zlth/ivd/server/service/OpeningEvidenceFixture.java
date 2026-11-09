package com.zlth.ivd.server.service;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

final class OpeningEvidenceFixture {
    private OpeningEvidenceFixture() {}

    static void audit(JdbcTemplate db, long batchId, LocalDateTime effectiveAt) {
        db.update("""
                INSERT INTO inventory_opening_balance(institution_id,batch_id,reagent_id,batch_no,warehouse_id,
                    quantity,unit_price,amount,effective_at,created_at,evidence_source,evidence_reference)
                SELECT institution_id,id,reagent_id,batch_no,warehouse_id,stock,unit_price,stock*unit_price,
                    ?,?,'QA_AUDITED_IMPORT','QA independently reconciled opening evidence'
                FROM reagent_batch WHERE id=?
                """, effectiveAt, effectiveAt, batchId);
    }
}
