package com.zlth.ivd.laboratory.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlth.ivd.laboratory.entity.LabInterval;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LabEnumMappingTest {
    @Test void intervalFieldsAreTypedWhileJsonCodesRemainCompatible() throws Exception {
        var mapper=new ObjectMapper();
        var interval=mapper.readValue("{\"kind\":\"MEASUREMENT\",\"resultType\":\"NUMERIC\",\"sex\":\"ANY\"}",LabInterval.class);
        assertInstanceOf(Enum.class,interval.getKind());
        assertInstanceOf(Enum.class,interval.getResultType());
        assertInstanceOf(Enum.class,interval.getSex());
        var json=mapper.readTree(mapper.writeValueAsString(interval));
        assertEquals("MEASUREMENT",json.get("kind").asText());
        assertEquals("NUMERIC",json.get("resultType").asText());
        assertEquals("ANY",json.get("sex").asText());
    }
}
