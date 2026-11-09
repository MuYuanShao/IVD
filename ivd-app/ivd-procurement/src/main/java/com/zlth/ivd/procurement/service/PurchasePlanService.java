package com.zlth.ivd.procurement.service;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.procurement.dto.CreatePurchasePlanRequest;
import com.zlth.ivd.procurement.vo.PurchasePlanVO;

public interface PurchasePlanService {
    PageVO<PurchasePlanVO> list(int page,int pageSize,String status,String keyword);
    PurchasePlanVO get(Long id);
    PurchasePlanVO create(CreatePurchasePlanRequest request);
    PurchasePlanVO update(Long id,CreatePurchasePlanRequest request);
    PurchasePlanVO submit(Long id);
    PurchasePlanVO approve(Long id,String comment);
    PurchasePlanVO reject(Long id,String comment);
    PurchasePlanVO convert(Long id);
}
