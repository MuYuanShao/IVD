package com.zlth.ivd.laboratory.service;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.laboratory.dto.ResultEntryRequest;
import com.zlth.ivd.laboratory.vo.ResultDetailVO;
import com.zlth.ivd.laboratory.vo.TestingInstitutionOptionVO;
import java.util.List;
public interface LabResultService {
    ResultDetailVO create(Long sampleId,ResultEntryRequest request);
    ResultDetailVO update(Long id,ResultEntryRequest request);
    ResultDetailVO detail(Long id);
    PageVO<ResultDetailVO> list(Long sampleId,int page,int pageSize);
    List<ResultDetailVO> versions(Long id);
    PageVO<ResultDetailVO> search(Long deviceId,Long patientId,int page,int pageSize);
    List<TestingInstitutionOptionVO> testingInstitutions();
    com.zlth.ivd.laboratory.vo.ResultContextVO lockContext(Long id);
    void requireEditable(Long id);
}
