package com.zlth.ivd.laboratory.vo;
/** Trace the recorded result version, not an unrelated newer edit of the same result. */
public record ClinicalEvidenceTraceVO(ResultDetailVO result,ResultEvidenceVO evidence) { }
