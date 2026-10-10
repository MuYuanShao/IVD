package com.zlth.ivd.catalog.service.impl;

import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.catalog.mapper.ReagentCatalogMapper;
import com.zlth.ivd.catalog.service.CatalogReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogReferenceServiceImpl implements CatalogReferenceService {
    private final ReagentCatalogMapper mapper;

    @Override
    public ReagentCatalog findById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public List<ReagentCatalog> findByIds(Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of() : mapper.selectBatchIds(ids);
    }
}
