package com.zlth.ivd.system.service.impl;

import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.system.dto.ResearchProjectRequest;
import com.zlth.ivd.system.entity.ResearchProject;
import com.zlth.ivd.system.mapper.ResearchProjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResearchProjectServiceTest {
    private ResearchProjectMapper mapper;
    private ResearchProjectServiceImpl service;
    @BeforeEach void setup() { mapper=mock(ResearchProjectMapper.class);service=new ResearchProjectServiceImpl(mapper); }
    @Test void optionalProjectDoesNotQueryDatabase() { assertNull(service.requireActiveName(null));verifyNoInteractions(mapper); }
    @Test void selectionRequiresExistingEnabledProject() {
        assertThrows(BizException.class,()->service.requireActiveName(99L));
        ResearchProject disabled=new ResearchProject();disabled.setEnabled(false);when(mapper.lockById(2L)).thenReturn(disabled);
        assertThrows(BizException.class,()->service.requireActiveName(2L));
        disabled.setEnabled(true);disabled.setName("科研A");assertEquals("科研A",service.requireActiveName(2L));
    }
    @Test void createNormalizesNameAndUsesGeneratedIdentity() {
        var request=new ResearchProjectRequest();request.setName("  科研A  ");
        when(mapper.insert(any(ResearchProject.class))).thenAnswer(call->{ResearchProject p=call.getArgument(0);p.setId(123L);return 1;});
        var result=service.create(request);assertEquals("科研A",result.name());assertEquals("123",result.id());assertTrue(result.enabled());
    }
    @Test void uniqueConstraintIsReportedAsBusinessConflict() {
        var request=new ResearchProjectRequest();request.setName("科研A");when(mapper.insert(any(ResearchProject.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertEquals(40900,assertThrows(BizException.class,()->service.create(request)).getErrorCode());
    }
    @Test void invalidSettingsAreRejectedBeforePersistence() {
        var request=new ResearchProjectRequest();request.setName("  ");assertThrows(BizException.class,()->service.create(request));
        request.setName("a");request.setSortOrder(-1);assertThrows(BizException.class,()->service.create(request));
        request.setSortOrder(0);request.setEnabled(null);assertThrows(BizException.class,()->service.create(request));
        verify(mapper,never()).insert(any(ResearchProject.class));
    }
}
