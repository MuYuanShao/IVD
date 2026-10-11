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
    @Mock com.zlth.ivd.procurement.mapper.PurchaseRequisitionItemMapper items;
    @Mock com.zlth.ivd.inventory.service.ProcurementInventoryService inventory;
    @Mock com.zlth.ivd.catalog.service.CatalogReferenceService catalog;
    @Mock com.zlth.ivd.system.service.ProcurementOrgService organizations;
    @InjectMocks ProcurementServiceImpl service;
    @BeforeEach void login() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(1L,"test",List.of()));
        var dept=new com.zlth.ivd.system.entity.SysDept();dept.setId(11L);dept.setStatus(1);
        lenient().when(organizations.findDept(11L)).thenReturn(dept);
        var reagent=new com.zlth.ivd.catalog.entity.ReagentCatalog();reagent.setId(2L);reagent.setPricePerUnit(java.math.BigDecimal.TEN);
        reagent.setApprovalStatus(com.zlth.ivd.common.enums.ApprovalStatusEnum.APPROVED);
        lenient().when(catalog.findByIds(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(reagent));
    }
    private PurchaseRequisition validRequest(){
        var req=new PurchaseRequisition();req.setFromDeptId(11L);req.setToWarehouseId(9L);
        var item=new com.zlth.ivd.procurement.entity.PurchaseRequisitionItem();item.setReagentId(2L);item.setQtyRequested(java.math.BigDecimal.ONE);
        req.setItems(List.of(item));return req;
    }
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }
    @Test void selectedProjectNameComesFromServerNotRequestBody() {
        var req=validRequest();req.setResearchProjectId(10L);req.setResearchProjectName("伪造名称");
        when(projects.requireActiveName(10L)).thenReturn("真实项目名称");service.createRequisition(req);
        assertEquals("真实项目名称",req.getResearchProjectName());verify(requisitions).insert(req);
    }
    @Test void omittedProjectPreservesLegacyRequisitionCreation() {
        var req=validRequest();req.setResearchProjectName("伪造名称");service.createRequisition(req);
        assertNull(req.getResearchProjectId());assertNull(req.getResearchProjectName());verify(requisitions).insert(req);
    }
    @Test void disabledOrMissingProjectPreventsAnyRequisitionInsert() {
        var req=validRequest();req.setResearchProjectId(10L);
        when(projects.requireActiveName(10L)).thenThrow(new BizException(40001,"项目已停用"));
        assertThrows(BizException.class,()->service.createRequisition(req));verifyNoInteractions(requisitions);
    }
}
