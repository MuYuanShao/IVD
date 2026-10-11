package com.zlth.ivd.laboratory.service;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.laboratory.dto.*;
import com.zlth.ivd.laboratory.vo.*;
import java.util.List;
public interface ReportService {
    ReportDetailVO create(ReportRequest request);
    ReportDetailVO update(Long id,ReportRequest request);
    ReportDetailVO detail(Long id);
    PageVO<ReportSummaryVO> list(int page,int pageSize,Long sampleId);
    ReportDetailVO submit(Long id,ReportActionRequest request);
    ReportDetailVO review(Long id,ReportActionRequest request);
    ReportDetailVO returnForEdit(Long id,ReportActionRequest request);
    ReportDetailVO sign(Long id,ReportActionRequest request);
    ReportDetailVO correct(Long id,ReportActionRequest request);
    ReportDetailVO revoke(Long id,ReportActionRequest request);
    List<ReportSummaryVO> versions(Long id);
    List<ReportHistoryVO> history(Long id);
    byte[] pdf(Long id,String purpose);
}
