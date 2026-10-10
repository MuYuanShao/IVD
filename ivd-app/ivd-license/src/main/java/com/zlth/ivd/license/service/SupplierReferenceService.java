package com.zlth.ivd.license.service;

import com.zlth.ivd.license.entity.Supplier;
import java.util.Collection;
import java.util.List;

/** 跨模块只读供应商契约。 */
public interface SupplierReferenceService {
    Supplier findById(Long id);
    List<Supplier> findByIds(Collection<Long> ids);
    List<Supplier> findAll();
}
