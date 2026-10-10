package com.zlth.ivd.system.service;

import com.zlth.ivd.system.dto.CatalogFieldOptionRequest;
import com.zlth.ivd.system.vo.CatalogFieldOptionVO;

import java.util.List;
import java.util.Map;

public interface CatalogFieldOptionService {
    List<CatalogFieldOptionVO> list(String fieldCode, boolean includeDisabled);
    Map<String, List<CatalogFieldOptionVO>> activeOptions();
    CatalogFieldOptionVO create(CatalogFieldOptionRequest request);
    CatalogFieldOptionVO update(Long id, CatalogFieldOptionRequest request);
    void toggleStatus(Long id);
    void delete(Long id);
}
