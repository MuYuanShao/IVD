package com.zlth.ivd.laboratory.service;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.laboratory.dto.PatientRequest;
import com.zlth.ivd.laboratory.dto.PatientDepartmentRequest;
import com.zlth.ivd.laboratory.vo.*;
public interface PatientService {
    PatientDetailVO create(PatientRequest request);
    PatientDetailVO detail(Long id);
    /** Caller transaction retains the authorization lock until its clinical write commits. */
    PatientDetailVO requireAuthorizedForWrite(Long id);
    PatientDetailVO update(Long id,PatientRequest request);
    PageVO<PatientSummaryVO> list(int page,int pageSize);
    void setDepartmentAccess(Long id,PatientDepartmentRequest request);
    PatientDetailVO requireNumber(String number);
}
