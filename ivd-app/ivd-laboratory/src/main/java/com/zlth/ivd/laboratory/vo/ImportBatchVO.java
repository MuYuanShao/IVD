package com.zlth.ivd.laboratory.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.util.List;
public record ImportBatchVO(@JsonSerialize(using=ToStringSerializer.class) Long id,String resource,String status,int rows,int succeeded,List<RowError> errors) {
    public record RowError(int row,String field,String message) { }
}
