package com.zlth.ivd.system.service;

import com.zlth.ivd.system.dto.ResearchProjectRequest;
import com.zlth.ivd.system.vo.ResearchProjectVO;
import java.util.List;

public interface ResearchProjectService {
    List<ResearchProjectVO> list(boolean includeDisabled);
    ResearchProjectVO create(ResearchProjectRequest request);
    ResearchProjectVO update(Long id, ResearchProjectRequest request);
    /** Called inside requisition transaction; locks selection until the requisition is saved. */
    String requireActiveName(Long id);
}
