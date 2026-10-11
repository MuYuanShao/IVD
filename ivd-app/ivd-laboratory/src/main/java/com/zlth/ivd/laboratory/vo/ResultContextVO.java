package com.zlth.ivd.laboratory.vo;
/** Internal clinical service snapshot. Never bypass authorization to retrieve it. */
public record ResultContextVO(ResultDetailVO result,PatientDetailVO patient,SampleDetailVO sample,
        TestItemVO item,Long createdBy,Long editedBy) { }
