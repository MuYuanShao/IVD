package com.zlth.ivd.laboratory.service;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.laboratory.dto.*;
import com.zlth.ivd.laboratory.service.impl.*;
import java.util.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.laboratory.storage.ReportObjectStore;
class ReportIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private ReportService reports;
    private final InstitutionContext.Scope tech=new InstitutionContext.Scope(1L,11L,101L,"LAB_TECH");
    @BeforeEach void setup(){
        context=new AnnotationConfigApplicationContext();context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("ivd.isolation.enabled","true")));
        context.register(PatientIntegrationTest.Config.class,SampleServiceImpl.class,LabResultServiceImpl.class,DeviceCapabilityServiceImpl.class,ResultEvidenceServiceImpl.class,ReportServiceImpl.class,TestStorage.class,com.zlth.ivd.laboratory.pdf.ReportPdfRenderer.class);
        context.refresh();reports=context.getBean(ReportService.class);
    }
    @AfterEach void cleanup(){context.close();assertTrue(InstitutionContext.current().isEmpty());}
    @Test void reportDraftKeepsItsPatientSampleAndActualResults(){
        InstitutionContext.within(tech,()->{
            var patient=context.getBean(PatientService.class).create(new PatientRequest("FICTION-REPORT-P","虚构报告患者","UNKNOWN",null,null));
            var samples=context.getBean(SampleService.class);var sample=samples.create(new SampleRequest(patient.id(),"FICTION-REPORT-S","血清",LocalDateTime.now().minusHours(1)));
            samples.receive(sample.id(),new SampleActionRequest(0,null));
            var result=context.getBean(LabResultService.class).create(sample.id(),new ResultEntryRequest(1L,"INSTRUMENT",1L,null,"4.2","mmol/L",LocalDateTime.now().minusMinutes(1),null));
            var report=reports.create(new ReportRequest("FICTION-R-001",sample.id(),List.of(result.id()),null));
            assertEquals("DRAFT",reports.detail(report.id()).status());assertEquals(patient.id(),reports.detail(report.id()).patient().id());return null;
        });
    }
    @Test void differentClinicalUsersReviewAndDirectorSignsOnlyAfterPrivatePdfPersistence() throws Exception {
        var draft=InstitutionContext.within(tech,this::draft);
        var submitted=InstitutionContext.within(tech,()->reports.submit(draft.id(),new ReportActionRequest(draft.version(),null,null)));
        var reviewer=new InstitutionContext.Scope(1L,11L,102L,"LAB_DOCTOR");
        var reviewed=InstitutionContext.within(reviewer,()->reports.review(draft.id(),new ReportActionRequest(submitted.version(),"虚构风险确认说明",true)));
        var director=new InstitutionContext.Scope(1L,11L,103L,"LAB_DIRECTOR");
        var signed=InstitutionContext.within(director,()->reports.sign(draft.id(),new ReportActionRequest(reviewed.version(),null,null)));
        assertEquals("SIGNED",signed.status());assertNotNull(signed.pdfDigest());
        var pdf=InstitutionContext.within(director,()->reports.pdf(draft.id(),"DOWNLOAD"));
        try(var document=org.apache.pdfbox.Loader.loadPDF(pdf)){
            var text=new org.apache.pdfbox.text.PDFTextStripper().getText(document);assertTrue(text.contains("未使用 CA 数字签名"));assertTrue(text.contains("虚构报告患者"));
        }
    }
    private com.zlth.ivd.laboratory.vo.ReportDetailVO draft(){
        var patient=context.getBean(PatientService.class).create(new PatientRequest("FICTION-REPORT-P","虚构报告患者","UNKNOWN",null,null));
        var samples=context.getBean(SampleService.class);var sample=samples.create(new SampleRequest(patient.id(),"FICTION-REPORT-S","血清",LocalDateTime.now().minusHours(1)));
        samples.receive(sample.id(),new SampleActionRequest(0,null));
        var result=context.getBean(LabResultService.class).create(sample.id(),new ResultEntryRequest(1L,"INSTRUMENT",1L,null,"4.2","mmol/L",LocalDateTime.now().minusMinutes(1),null));
        return reports.create(new ReportRequest("FICTION-R-001",sample.id(),List.of(result.id()),null));
    }
    @Test void entryActorCannotReviewAndNonDirectorCannotSignAndPendingResultCannotChange(){
        var draft=InstitutionContext.within(tech,this::draft);
        var pending=InstitutionContext.within(tech,()->reports.submit(draft.id(),new ReportActionRequest(0,null,null)));
        InstitutionContext.within(tech,()->{
            assertThrows(BizException.class,()->reports.review(draft.id(),new ReportActionRequest(pending.version(),"虚构说明",true)));
            assertThrows(BizException.class,()->reports.sign(draft.id(),new ReportActionRequest(pending.version(),null,null)));
            var result=draft.results().get(0);assertThrows(BizException.class,()->context.getBean(LabResultService.class).update(result.id(),new ResultEntryRequest(result.itemId(),"MANUAL",null,null,"5","mmol/L",result.testedAt(),result.version())));return null;
        });
        InstitutionContext.within(new InstitutionContext.Scope(1L,11L,101L,"LAB_DIRECTOR"),()->{assertThrows(BizException.class,()->reports.review(draft.id(),new ReportActionRequest(pending.version(),"虚构说明",true)));return null;});
    }
    @Test void storageFailureRollsBackSignAndRetryCreatesOneObject(){
        var reviewed=reviewed();var director=new InstitutionContext.Scope(1L,11L,103L,"LAB_DIRECTOR");var store=context.getBean(MemoryStore.class);store.fail=true;
        InstitutionContext.within(director,()->{
            assertThrows(BizException.class,()->reports.sign(reviewed.id(),new ReportActionRequest(reviewed.version(),null,null)));
            assertEquals("REVIEWED",reports.detail(reviewed.id()).status());assertEquals(0,store.data.size());
            store.fail=false;var signed=reports.sign(reviewed.id(),new ReportActionRequest(reviewed.version(),null,null));
            assertEquals(signed.version(),reports.sign(reviewed.id(),new ReportActionRequest(reviewed.version(),null,null)).version());assertEquals(1,store.data.size());return null;
        });
    }
    @Test void correctionKeepsOriginalSnapshotAndRevocationBlocksNormalPdf(){
        var reviewed=reviewed();var director=new InstitutionContext.Scope(1L,11L,103L,"LAB_DIRECTOR");
        var signed=InstitutionContext.within(director,()->reports.sign(reviewed.id(),new ReportActionRequest(reviewed.version(),null,null)));
        byte[] original=InstitutionContext.within(tech,()->reports.pdf(signed.id(),"DOWNLOAD"));
        var corrected=InstitutionContext.within(tech,()->reports.correct(signed.id(),new ReportActionRequest(signed.version(),"虚构更正原因",null)));
        assertEquals(2,corrected.edition());assertArrayEquals(original,InstitutionContext.within(tech,()->reports.pdf(signed.id(),"DOWNLOAD")));
        var revoked=InstitutionContext.within(director,()->reports.revoke(signed.id(),new ReportActionRequest(signed.version(),"虚构撤销原因",null)));
        assertEquals("REVOKED",revoked.status());InstitutionContext.within(tech,()->{assertThrows(BizException.class,()->reports.pdf(signed.id(),"DOWNLOAD"));assertEquals(2,reports.versions(signed.id()).size());return null;});
    }
    @Test void concurrentSignRequestsReturnSameVersionAndOnePrivateObject() throws Exception {
        var reviewed=reviewed();var director=new InstitutionContext.Scope(1L,11L,103L,"LAB_DIRECTOR");var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<String> sign=()->{start.await();return InstitutionContext.within(director,()->reports.sign(reviewed.id(),new ReportActionRequest(reviewed.version(),null,null)).pdfDigest());};
        try{var first=pool.submit(sign);var second=pool.submit(sign);start.countDown();assertEquals(first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS));assertEquals(1,context.getBean(MemoryStore.class).data.size());}finally{pool.shutdownNow();}
    }
    @Test void foreignDepartmentInstitutionAndAdminCannotReadReports(){
        var draft=InstitutionContext.within(tech,this::draft);
        for(var scope:List.of(new InstitutionContext.Scope(1L,12L,102L,"LAB_DOCTOR"),new InstitutionContext.Scope(2L,21L,201L,"LAB_TECH"),new InstitutionContext.Scope(1L,null,99L,"SYSTEM_ADMIN")))InstitutionContext.within(scope,()->{assertThrows(BizException.class,()->reports.detail(draft.id()));assertThrows(BizException.class,()->reports.pdf(draft.id(),"PREVIEW"));return null;});
    }
    @Test void embeddedChineseFontRendersMultiplePagesWithoutExternalFonts() throws Exception {
        var draft=InstitutionContext.within(tech,this::draft);
        var snapshot=InstitutionContext.within(tech,()->{
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(context.getBean(javax.sql.DataSource.class));String encrypted=jdbc.queryForObject("SELECT encrypted_snapshot FROM lab_report WHERE id=?",String.class,draft.id());return context.getBean(com.zlth.ivd.laboratory.security.ClinicalRecordSupport.class).decrypt(encrypted,ReportSnapshot.class);
        });
        var lines=new ArrayList<ReportSnapshot.Line>();for(int i=0;i<80;i++)lines.add(snapshot.lines().get(0));var multi=new ReportSnapshot(snapshot.number(),snapshot.sampleId(),snapshot.edition(),snapshot.patient(),lines,snapshot.stamps(),"虚构多页排版测试");
        byte[] pdf=context.getBean(com.zlth.ivd.laboratory.pdf.ReportPdfRenderer.class).render(multi,"DRAFT");
        try(var document=org.apache.pdfbox.Loader.loadPDF(pdf)){
            assertTrue(document.getNumberOfPages()>1);String text=new org.apache.pdfbox.text.PDFTextStripper().getText(document);assertTrue(text.contains("草稿预览"));
            for(int page=1;page<=document.getNumberOfPages();page++){var stripper=new org.apache.pdfbox.text.PDFTextStripper();stripper.setStartPage(page);stripper.setEndPage(page);var pageText=stripper.getText(document);assertTrue(pageText.contains("IVD 检验报告"),"Repeated title missing on page "+page);assertTrue(pageText.contains("报告编号：FICTION-R-001"),"Repeated identity missing on page "+page);}
            var renderer=new org.apache.pdfbox.rendering.PDFRenderer(document);var output=java.nio.file.Path.of("../../output/pdf").toAbsolutePath().normalize();java.nio.file.Files.createDirectories(output);java.nio.file.Files.write(output.resolve("synthetic-lab-multipage.pdf"),pdf);
            javax.imageio.ImageIO.write(renderer.renderImageWithDPI(0,110),"png",output.resolve("synthetic-lab-first.png").toFile());javax.imageio.ImageIO.write(renderer.renderImageWithDPI(document.getNumberOfPages()-1,110),"png",output.resolve("synthetic-lab-last.png").toFile());
        }
    }
    private com.zlth.ivd.laboratory.vo.ReportDetailVO reviewed(){
        var draft=InstitutionContext.within(tech,this::draft);var pending=InstitutionContext.within(tech,()->reports.submit(draft.id(),new ReportActionRequest(0,null,null)));
        return InstitutionContext.within(new InstitutionContext.Scope(1L,11L,102L,"LAB_DOCTOR"),()->reports.review(draft.id(),new ReportActionRequest(pending.version(),"虚构风险确认说明",true)));
    }
    @Test void draftEditorsSubmittersAndEvidenceBindersCannotReviewTheirOwnContribution(){
        var draft=InstitutionContext.within(tech,this::draft);var doctor=new InstitutionContext.Scope(1L,11L,102L,"LAB_DOCTOR");
        var edited=InstitutionContext.within(doctor,()->reports.update(draft.id(),new ReportRequest(draft.number(),draft.sampleId(),draft.results().stream().map(row->row.id()).toList(),draft.version())));
        InstitutionContext.within(doctor,()->{context.getBean(ResultEvidenceService.class).bind(draft.results().get(0).id(),new ResultEvidenceRequest(0,51L,61L,71L));return null;});
        var pending=InstitutionContext.within(tech,()->reports.submit(draft.id(),new ReportActionRequest(edited.version(),null,null)));
        assertTrue(pending.entryActorIds().contains(102L));
        InstitutionContext.within(doctor,()->{assertThrows(BizException.class,()->reports.review(draft.id(),new ReportActionRequest(pending.version(),"虚构自审说明",true)));return null;});
    }
    @Configuration static class TestStorage {
        @Bean MemoryStore objects(){return new MemoryStore();}
    }
    static class MemoryStore implements ReportObjectStore {
        final Map<String,byte[]> data=new java.util.concurrent.ConcurrentHashMap<>();boolean fail;
        public void put(String key,byte[] encrypted){if(fail)throw new BizException(50000,"虚构存储故障");data.put(key,encrypted.clone());}
        public byte[] get(String key){return data.get(key).clone();}
        public void remove(String key){data.remove(key);}
    }
}
