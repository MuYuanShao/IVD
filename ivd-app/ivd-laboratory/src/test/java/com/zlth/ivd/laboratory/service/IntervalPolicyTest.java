package com.zlth.ivd.laboratory.service;

import com.zlth.ivd.common.exception.BizException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntervalPolicyTest {
    private final IntervalPolicy policy = new IntervalPolicy();
    private final LocalDate day = LocalDate.of(2026, 10, 10);

    private IntervalPolicy.Rule rule() {
        return new IntervalPolicy.Rule(1L, 2L, null, "REFERENCE", "酶法", "mmol/L", "NUMERIC", "ANY",
                0, 36500, new BigDecimal("3.5"), new BigDecimal("5.5"), null, day.minusDays(1), day.plusDays(1), true);
    }

    @Test void exactNumericBoundariesAreNormalWithoutUnitConversion() {
        var input = new IntervalPolicy.Input(2L, 8L, "酶法", "mmol/L", "MALE", 100, day, "3.5");
        assertEquals("NORMAL", policy.evaluate(List.of(rule()), input, "REFERENCE").status());
        assertEquals("LOW", policy.evaluate(List.of(rule()),
                new IntervalPolicy.Input(2L, 8L, "酶法", "mmol/L", "MALE", 100, day, "3.49"), "REFERENCE").status());
        assertEquals("UNIT_MISMATCH", policy.evaluate(List.of(rule()),
                new IntervalPolicy.Input(2L, 8L, "酶法", "mg/dL", "MALE", 100, day, "3.5"), "REFERENCE").status());
    }

    @Test void wildcardAndDeviceSpecificIntervalsCannotOverlap() {
        var generic = rule();
        var specific = new IntervalPolicy.Rule(2L, 2L, 8L, "REFERENCE", "酶法", "mmol/L", "NUMERIC", "MALE",
                100, 200, new BigDecimal("4"), new BigDecimal("6"), null, day, day.plusDays(5), true);
        assertThrows(BizException.class, () -> policy.requireNoOverlap(specific, List.of(generic)));
    }

    @Test void expirationAndInvalidOrMissingResultsNeverBecomeNormal() {
        assertEquals("MISSING",policy.evaluate(List.of(rule()),new IntervalPolicy.Input(2L,null,"酶法","mmol/L","MALE",100,day.plusDays(2),"4"),"REFERENCE").status());
        assertEquals("INVALID_RESULT",policy.evaluate(List.of(rule()),new IntervalPolicy.Input(2L,null,"酶法","mmol/L","MALE",100,day,"未检出"),"REFERENCE").status());
        assertEquals("MISSING",policy.evaluate(List.of(),new IntervalPolicy.Input(2L,null,"酶法","mmol/L","MALE",100,day,"4"),"REFERENCE").status());
        assertEquals("CONFLICT",policy.evaluate(List.of(rule(),rule()),new IntervalPolicy.Input(2L,null,"酶法","mmol/L","MALE",100,day,"4"),"REFERENCE").status());
    }

    @Test void qualitativeValuesAreComparedWithoutInventingDiagnosis() {
        var qualitative = new IntervalPolicy.Rule(3L,3L,null,"REFERENCE","胶体金","","TEXT","FEMALE",10,20,null,null,"阴性",day,day,true);
        assertEquals("NORMAL",policy.evaluate(List.of(qualitative),new IntervalPolicy.Input(3L,null,"胶体金","","FEMALE",10,day,"阴性"),"REFERENCE").status());
        assertEquals("ABNORMAL",policy.evaluate(List.of(qualitative),new IntervalPolicy.Input(3L,null,"胶体金","","FEMALE",20,day,"阳性"),"REFERENCE").status());
        assertEquals("MISSING",policy.evaluate(List.of(qualitative),new IntervalPolicy.Input(3L,null,"胶体金","","MALE",10,day,"阴性"),"REFERENCE").status());
        assertEquals("MISSING",policy.evaluate(List.of(qualitative),new IntervalPolicy.Input(3L,null,"胶体金","","FEMALE",21,day,"阴性"),"REFERENCE").status());
    }

    @Test void adjacentValidityDatesDoNotOverlapButSharedEndpointsDo() {
        var next = new IntervalPolicy.Rule(2L,2L,null,"REFERENCE","酶法","mmol/L","NUMERIC","ANY",0,36500,new BigDecimal("3"),new BigDecimal("6"),null,day.plusDays(2),day.plusDays(8),true);
        assertDoesNotThrow(()->policy.requireNoOverlap(next,List.of(rule())));
        var touching = new IntervalPolicy.Rule(2L,2L,null,"REFERENCE","酶法","mmol/L","NUMERIC","ANY",0,36500,new BigDecimal("3"),new BigDecimal("6"),null,day.plusDays(1),day.plusDays(8),true);
        assertThrows(BizException.class,()->policy.requireNoOverlap(touching,List.of(rule())));
    }
}
