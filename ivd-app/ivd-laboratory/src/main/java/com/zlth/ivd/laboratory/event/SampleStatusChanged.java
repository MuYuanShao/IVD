package com.zlth.ivd.laboratory.event;
import com.zlth.ivd.laboratory.enums.SampleStatusEnum;
/** No demographics, specimen number or free text in published events. */
public record SampleStatusChanged(long institutionId,Long departmentId,Long sampleId,
        SampleStatusEnum from,SampleStatusEnum to,Long actorId) { }
