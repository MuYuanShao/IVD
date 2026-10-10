package com.zlth.ivd.license.service.impl;

import com.zlth.ivd.license.entity.Supplier;
import com.zlth.ivd.license.mapper.SupplierMapper;
import com.zlth.ivd.license.service.SupplierReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierReferenceServiceImpl implements SupplierReferenceService {
    private final SupplierMapper mapper;
    public Supplier findById(Long id) { return mapper.selectById(id); }
    public List<Supplier> findByIds(Collection<Long> ids) { return ids == null || ids.isEmpty() ? List.of() : mapper.selectBatchIds(ids); }
    public List<Supplier> findAll() { return mapper.selectList(null); }
}
