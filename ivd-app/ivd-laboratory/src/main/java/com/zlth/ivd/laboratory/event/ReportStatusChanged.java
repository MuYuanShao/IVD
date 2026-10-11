package com.zlth.ivd.laboratory.event;
import com.zlth.ivd.laboratory.enums.ReportStatusEnum;
public record ReportStatusChanged(long institutionId,Long departmentId,Long reportId,
        ReportStatusEnum from,ReportStatusEnum to,Long actorId) { }
