package com.zlth.ivd.laboratory.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
import java.util.List;
public record ReportDetailVO(@JsonSerialize(using=ToStringSerializer.class) Long id,String number,int edition,String status,int version,
        @JsonSerialize(using=ToStringSerializer.class) Long sampleId,PatientDetailVO patient,List<ResultDetailVO> results,
        @JsonSerialize(using=ToStringSerializer.class) Long createdBy,@JsonSerialize(using=ToStringSerializer.class) Long reviewedBy,
        @JsonSerialize(using=ToStringSerializer.class) Long signedBy,LocalDateTime signedAt,String pdfDigest,String caStatus,
        @JsonSerialize(contentUsing=ToStringSerializer.class) List<Long> entryActorIds) { }
