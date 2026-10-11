package com.zlth.ivd.laboratory.dto;
import jakarta.validation.constraints.*;

public record TestItemRequest(
        @NotBlank @Size(max=64) String code,
        @NotBlank @Size(max=200) String name,
        @NotBlank @Size(max=100) String methodology,
        @NotNull @Pattern(regexp="NUMERIC|TEXT") String resultType,
        @NotNull @Size(max=32) String unit,
        @NotNull Boolean active,
        @PositiveOrZero Integer version,Boolean deviceRequired) {
    public TestItemRequest(String code,String name,String methodology,String resultType,String unit,Boolean active,Integer version){
        this(code,name,methodology,resultType,unit,active,version,null);
    }
}
