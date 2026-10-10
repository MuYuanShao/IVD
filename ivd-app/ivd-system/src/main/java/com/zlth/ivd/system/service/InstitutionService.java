package com.zlth.ivd.system.service;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.system.dto.InstitutionRequest;
import com.zlth.ivd.system.vo.InstitutionVO;
import java.util.List;
public interface InstitutionService {
    PageVO<InstitutionVO> list(int page,int pageSize,String keyword);
    List<InstitutionVO> options();
    InstitutionVO create(InstitutionRequest request);
    InstitutionVO update(Long id,InstitutionRequest request);
}
