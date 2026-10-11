package com.zlth.ivd.laboratory.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlth.ivd.laboratory.entity.LabReportResult;
import org.apache.ibatis.annotations.*;
@Mapper public interface LabReportResultMapper extends BaseMapper<LabReportResult> {
    @Select("SELECT COUNT(*) FROM lab_report_result rr JOIN lab_report r ON r.id=rr.report_id AND r.institution_id=rr.institution_id WHERE rr.result_id=#{id} AND rr.institution_id=#{owner} AND r.status IN ('PENDING_REVIEW','REVIEWED')")
    long frozenReferences(@Param("id") Long id,@Param("owner") long owner);
}
