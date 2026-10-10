package com.zlth.ivd.laboratory.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDate;
public record PatientDetailVO(@JsonSerialize(using=ToStringSerializer.class) Long id,
        String number,String name,String sex,LocalDate birthDate,Integer version) { }
