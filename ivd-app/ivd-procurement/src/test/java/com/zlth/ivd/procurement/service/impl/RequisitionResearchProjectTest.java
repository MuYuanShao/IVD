package com.zlth.ivd.procurement.service.impl;

import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.procurement.entity.PurchaseRequisition;
import com.zlth.ivd.procurement.mapper.PurchaseRequisitionMapper;
import com.zlth.ivd.system.service.ResearchProjectService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequisitionResearchProjectTest {
    @Mock ResearchProjectService projects;
    @Mock PurchaseRequisitionMapper requisitions;
    @InjectMocks ProcurementServiceImpl service;
    @BeforeEach void login() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(1L,"test",List.of())); }
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }
    @Test void selectedProjectNameComesFromServerNotRequestBody() {
        var req=new PurchaseRequisition();req.setResearchProjectId(10L);req.setResearchProjectName("伪造名称");
        when(projects.requireActiveName(10L)).thenReturn("真实项目名称");service.createRequisition(req);
        assertEquals("真实项目名称",req.getResearchProjectName());verify(requisitions).insert(req);
    }
    @Test void omittedProjectPreservesLegacyRequisitionCreation() {
        var req=new PurchaseRequisition();req.setResearchProjectName("伪造名称");service.createRequisition(req);
        assertNull(req.getResearchProjectId());assertNull(req.getResearchProjectName());verify(requisitions).insert(req);
    }
    @Test void disabledOrMissingProjectPreventsAnyRequisitionInsert() {
        var req=new PurchaseRequisition();req.setResearchProjectId(10L);
        when(projects.requireActiveName(10L)).thenThrow(new BizException(40001,"项目已停用"));
        assertThrows(BizException.class,()->service.createRequisition(req));verifyNoInteractions(requisitions);
    }
}
