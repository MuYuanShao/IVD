package com.zlth.ivd.catalog.service;

import com.zlth.ivd.catalog.entity.ReagentCatalog;

import java.util.Collection;
import java.util.List;

/** 跨模块只读目录契约。 */
public interface CatalogReferenceService {
    ReagentCatalog findById(Long id);
    List<ReagentCatalog> findByIds(Collection<Long> ids);
}
