package com.zlth.ivd.laboratory.service;
import com.zlth.ivd.laboratory.vo.*;
import java.time.LocalDateTime;
import java.util.List;
/** Private encrypted document, including recorded result criteria and explicit evidence. */
public record ReportSnapshot(String number,Long sampleId,int edition,PatientDetailVO patient,
        List<Line> lines,List<Stamp> stamps,String workflowNote) {
    public record Line(ResultContextVO context,List<ResultEvidenceVO> evidence) { }
    public record Stamp(String action,Long actorId,String username,LocalDateTime at) { }
}
