package com.zlth.ivd.laboratory.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zlth.ivd.inventory.service.BatchEvidenceDirectoryService.BatchEvidence;
import com.zlth.ivd.consumption.service.ConsumptionEvidenceService.ConsumptionEvidence;
public record ResultEvidenceVO(@JsonSerialize(using=ToStringSerializer.class) Long id,
        @JsonSerialize(using=ToStringSerializer.class) Long resultId,int resultVersion,
        BatchEvidence batch,ConsumptionEvidence consumption,@JsonSerialize(using=ToStringSerializer.class) Long boundBy) { }
