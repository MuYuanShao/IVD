package com.zlth.ivd.inventory.service;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.inventory.vo.RemainingBatchVO;

public interface RemainingBatchService {
    PageVO<RemainingBatchVO> list(int page, int pageSize, String keyword, Integer level);
}
