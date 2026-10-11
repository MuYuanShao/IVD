package com.zlth.ivd.laboratory.event;
/** Informational event only: it must not deduct inventory or create a settlement. */
public record ResultRecorded(long institutionId,Long departmentId,Long sampleId,Long resultId,int version) { }
