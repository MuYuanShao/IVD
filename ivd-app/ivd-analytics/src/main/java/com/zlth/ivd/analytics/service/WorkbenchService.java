package com.zlth.ivd.analytics.service;

import com.zlth.ivd.analytics.vo.WorkbenchVO;
import com.zlth.ivd.common.dto.PageVO;

public interface WorkbenchService {
    WorkbenchVO overview(Long userId);
    PageVO<WorkbenchVO.Product> products(Long userId, int page, int pageSize, String keyword);
}
