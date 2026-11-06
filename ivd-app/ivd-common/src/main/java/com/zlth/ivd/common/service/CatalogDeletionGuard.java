package com.zlth.ivd.common.service;

/** Catalog owns deletion; application composition supplies cross-domain reference checks. */
public interface CatalogDeletionGuard {
    void requireDeletable(Long reagentId);
}
