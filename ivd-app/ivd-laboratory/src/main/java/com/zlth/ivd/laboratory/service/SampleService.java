package com.zlth.ivd.laboratory.service;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.laboratory.dto.*;
import com.zlth.ivd.laboratory.vo.*;

public interface SampleService {
    SampleDetailVO create(SampleRequest request);
    SampleDetailVO detail(Long id);
    SampleDetailVO requireNumber(String number);
    PageVO<SampleSummaryVO> list(int page,int pageSize,Long patientId);
    SampleDetailVO receive(Long id,SampleActionRequest request);
    SampleDetailVO reject(Long id,SampleActionRequest request);
    /** Locks both patient authorization and sample for a result-writing transaction. */
    SampleDetailVO requireReceivedForWrite(Long id);
}
