package com.zlth.ivd.laboratory.service;
import com.zlth.ivd.laboratory.dto.ResultEvidenceRequest;
import com.zlth.ivd.laboratory.vo.ResultEvidenceVO;
import java.util.List;
public interface ResultEvidenceService {
    ResultEvidenceVO bind(Long resultId,ResultEvidenceRequest request);
    List<ResultEvidenceVO> list(Long resultId,Integer resultVersion);
    List<com.zlth.ivd.inventory.service.BatchEvidenceDirectoryService.BatchEvidence> batchOptions(Long resultId,String keyword);
    List<com.zlth.ivd.consumption.service.ConsumptionEvidenceService.ConsumptionEvidence> consumptionOptions(Long resultId,Long batchId);
    com.zlth.ivd.common.dto.PageVO<com.zlth.ivd.laboratory.vo.ClinicalEvidenceTraceVO> trace(Long batchId,Long consumptionId,int page,int pageSize);
}
