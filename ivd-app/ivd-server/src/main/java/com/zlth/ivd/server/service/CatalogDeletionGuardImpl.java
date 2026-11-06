package com.zlth.ivd.server.service;

import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.service.CatalogDeletionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogDeletionGuardImpl implements CatalogDeletionGuard {
    private final JdbcTemplate jdbc;

    @Override public void requireDeletable(Long reagentId) {
        // Scoped JDBC enforces institution ownership; this read model does not access foreign Mappers.
        var stock = jdbc.queryForList("SELECT id FROM reagent_batch WHERE reagent_id=? AND stock<>0 FOR UPDATE", reagentId);
        if (!stock.isEmpty()) throw new BizException(40900, "该试剂存在库存，不能删除，建议停用");
        var openOrders = jdbc.queryForList("""
                SELECT i.id FROM purchase_order_item i
                JOIN purchase_order p ON p.id=i.po_id AND p.institution_id=i.institution_id
                WHERE i.reagent_id=? AND (p.status IS NULL OR p.status NOT IN ('COMPLETED','CANCELLED'))
                FOR UPDATE
                """, reagentId);
        if (!openOrders.isEmpty())
            throw new BizException(40900, "该试剂存在未完成采购，不能删除");
    }
}
