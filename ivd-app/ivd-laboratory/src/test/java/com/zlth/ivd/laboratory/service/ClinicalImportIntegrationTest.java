package com.zlth.ivd.laboratory.service;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.laboratory.importing.ImportResource;
import com.zlth.ivd.laboratory.service.impl.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.laboratory.dto.*;
import java.time.LocalDateTime;
class ClinicalImportIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private ClinicalImportService imports;
    private final InstitutionContext.Scope tech=new InstitutionContext.Scope(1L,11L,101L,"LAB_TECH");
    @BeforeEach void setup(){
        context=new AnnotationConfigApplicationContext();context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("ivd.isolation.enabled","true")));
        context.register(PatientIntegrationTest.Config.class,SampleServiceImpl.class,LabResultServiceImpl.class,DeviceCapabilityServiceImpl.class,ResultEvidenceServiceImpl.class,ReportServiceImpl.class,ReportIntegrationTest.TestStorage.class,com.zlth.ivd.laboratory.pdf.ReportPdfRenderer.class,ClinicalImportServiceImpl.class,com.zlth.ivd.laboratory.importing.XlsxImportCodec.class,com.zlth.ivd.laboratory.importing.ImportRowExecutor.class);
        context.refresh();imports=context.getBean(ClinicalImportService.class);
    }
    @AfterEach void cleanup(){context.close();assertTrue(InstitutionContext.current().isEmpty());}
    @Test void patientPreflightRollsBackAndConfirmedBatchIsIdempotent() throws Exception {
        InstitutionContext.within(tech,()->{
            byte[] file=workbook(ImportResource.PATIENTS,List.of(List.of("FICTION-IMPORT-P","虚构导入患者","UNKNOWN","")));
            var batch=imports.preflight(ImportResource.PATIENTS,file);assertEquals("READY",batch.status());assertEquals(0,context.getBean(PatientService.class).list(1,20).getTotal());
            var executed=imports.execute(batch.id());assertEquals(1,executed.succeeded());assertEquals(1,imports.execute(batch.id()).succeeded());assertEquals(1,context.getBean(PatientService.class).list(1,20).getTotal());return null;
        });
    }
    @Test void formulasNumericCodesUnknownHeadersAndDuplicateKeysAreRejected(){
        InstitutionContext.within(tech,()->{
            var bytes=workbook(ImportResource.PATIENTS,List.of(List.of("FICTION-IMPORT-P","虚构患者","UNKNOWN","")));
            for(String mutation:List.of("formula","numeric","unknown")){
                byte[] changed=mutate(bytes,mutation);assertThrows(BizException.class,()->imports.preflight(ImportResource.PATIENTS,changed));
            }
            var duplicated=imports.preflight(ImportResource.PATIENTS,workbook(ImportResource.PATIENTS,List.of(List.of("FICTION-P","虚构患者一","UNKNOWN",""),List.of("FICTION-P","虚构患者二","UNKNOWN",""))));
            assertEquals("INVALID",duplicated.status());assertThrows(BizException.class,()->imports.execute(duplicated.id()));assertFalse(duplicated.errors().toString().contains("虚构患者"));return null;
        });
    }
    @Test void rowLevelExecutionConflictsDoNotDuplicateOtherSuccessfulRows(){
        InstitutionContext.within(tech,()->{
            var file=workbook(ImportResource.PATIENTS,List.of(List.of("FICTION-FIRST","虚构患者一","UNKNOWN",""),List.of("FICTION-SECOND","虚构患者二","UNKNOWN","")));
            var batch=imports.preflight(ImportResource.PATIENTS,file);context.getBean(PatientService.class).create(new PatientRequest("FICTION-FIRST","虚构已存在患者","UNKNOWN",null,null));
            var executed=imports.execute(batch.id());assertEquals(1,executed.succeeded());assertEquals(1,executed.errors().size());assertEquals(1,imports.execute(batch.id()).succeeded());assertEquals(2,context.getBean(PatientService.class).list(1,20).getTotal());return null;
        });
    }
    @Test void reportAndAllItsResultRowsRollbackTogetherWhenReferencesChangeAfterPreflight(){
        InstitutionContext.within(tech,()->{
            var patient=context.getBean(PatientService.class).create(new PatientRequest("FICTION-REPORT-P","虚构患者","UNKNOWN",null,null));var samples=context.getBean(SampleService.class);
            var sample=samples.create(new SampleRequest(patient.id(),"FICTION-REPORT-S","血清",LocalDateTime.now().minusHours(1)));samples.receive(sample.id(),new SampleActionRequest(0,null));
            String testedAt=LocalDateTime.now().minusMinutes(1).toString();var file=workbook(ImportResource.REPORTS,List.of(
                    List.of("FICTION-REPORT","FICTION-REPORT-S","K","INSTRUMENT","OWN","","4.2","mmol/L",testedAt),
                    List.of("FICTION-REPORT","FICTION-REPORT-S","TEXT","MANUAL","","","阴性","",testedAt)));
            var batch=imports.preflight(ImportResource.REPORTS,file);assertEquals("READY",batch.status());
            InstitutionContext.within(new InstitutionContext.Scope(1L,11L,103L,"LAB_DIRECTOR"),()->{var catalog=context.getBean(LabCatalogService.class);var item=catalog.item(3L);catalog.updateItem(item.id(),new TestItemRequest(item.code(),item.name(),item.methodology(),item.resultType(),item.unit(),false,item.version()));return null;});
            var executed=imports.execute(batch.id());assertEquals(0,executed.succeeded());assertEquals(2,executed.errors().size());assertEquals(0,context.getBean(LabResultService.class).list(sample.id(),1,20).getTotal());assertEquals(0,context.getBean(ReportService.class).list(1,20,sample.id()).getTotal());return null;
        });
    }
    @Test void reportImportCreatesDraftWithoutReviewSignOrPdfAndWrongUnitsCannotImport(){
        InstitutionContext.within(tech,()->{
            var patient=context.getBean(PatientService.class).create(new PatientRequest("FICTION-REPORT-P","虚构患者","UNKNOWN",null,null));var samples=context.getBean(SampleService.class);
            var sample=samples.create(new SampleRequest(patient.id(),"FICTION-REPORT-S","血清",LocalDateTime.now().minusHours(1)));samples.receive(sample.id(),new SampleActionRequest(0,null));String testedAt=LocalDateTime.now().minusMinutes(1).toString();
            var invalid=imports.preflight(ImportResource.REPORTS,workbook(ImportResource.REPORTS,List.of(List.of("FICTION-REPORT","FICTION-REPORT-S","K","INSTRUMENT","OWN","","4.2","WRONG-UNIT",testedAt))));assertEquals("INVALID",invalid.status());
            var batch=imports.preflight(ImportResource.REPORTS,workbook(ImportResource.REPORTS,List.of(List.of("FICTION-REPORT","FICTION-REPORT-S","K","INSTRUMENT","OWN","","4.2","mmol/L",testedAt))));
            assertEquals(1,imports.execute(batch.id()).succeeded());assertEquals("DRAFT",context.getBean(ReportService.class).list(1,20,sample.id()).getRecords().get(0).status());assertEquals(0,context.getBean(ReportIntegrationTest.MemoryStore.class).data.size());return null;
        });
    }
    @Test void importBatchesCannotBeReadOrExecutedFromAnotherDepartmentOrInstitution(){
        var batch=InstitutionContext.within(tech,()->imports.preflight(ImportResource.PATIENTS,workbook(ImportResource.PATIENTS,List.of(List.of("FICTION-P","虚构患者","UNKNOWN","")))));
        for(var scope:List.of(new InstitutionContext.Scope(1L,12L,101L,"LAB_TECH"),new InstitutionContext.Scope(2L,21L,101L,"LAB_TECH"),new InstitutionContext.Scope(1L,11L,102L,"LAB_DOCTOR")))InstitutionContext.within(scope,()->{assertThrows(BizException.class,()->imports.detail(batch.id()));assertThrows(BizException.class,()->imports.execute(batch.id()));return null;});
    }
    @Test void allResourcesHaveRealTextHeadersAndReorderedHeadersStillReadCorrectly(){
        var codec=context.getBean(com.zlth.ivd.laboratory.importing.XlsxImportCodec.class);
        for(var resource:ImportResource.values())try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(codec.template(resource)))){assertEquals(resource.columns().size(),book.getSheetAt(0).getRow(0).getLastCellNum());}catch(IOException error){throw new UncheckedIOException(error);}
        InstitutionContext.within(tech,()->{
            byte[] original=workbook(ImportResource.PATIENTS,List.of(List.of("FICTION-REORDER","虚构重排患者","UNKNOWN","")));
            try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(original));var output=new ByteArrayOutputStream()){
                for(int n=0;n<=1;n++){var row=book.getSheetAt(0).getRow(n);String first=row.getCell(0).getStringCellValue();row.getCell(0).setCellValue(row.getCell(1).getStringCellValue());row.getCell(1).setCellValue(first);}book.write(output);
                var batch=imports.preflight(ImportResource.PATIENTS,output.toByteArray());assertEquals("READY",batch.status());assertEquals(1,imports.execute(batch.id()).succeeded());return null;
            }catch(IOException error){throw new UncheckedIOException(error);}
        });
    }
    private byte[] mutate(byte[] bytes,String mutation){
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(bytes));var output=new ByteArrayOutputStream()){
            var sheet=book.getSheetAt(0);switch(mutation){case "formula" -> sheet.getRow(1).getCell(0).setCellFormula("1+1");case "numeric" -> {sheet.getRow(1).getCell(0).setCellType(org.apache.poi.ss.usermodel.CellType.NUMERIC);sheet.getRow(1).getCell(0).setCellValue(123);}case "unknown" -> sheet.getRow(0).getCell(0).setCellValue("未知列");}book.write(output);return output.toByteArray();
        }catch(IOException error){throw new UncheckedIOException(error);}
    }
    static byte[] workbook(ImportResource resource,List<List<String>> rows){
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var output=new ByteArrayOutputStream()){
            var sheet=book.createSheet(resource.name());var header=sheet.createRow(0);for(int i=0;i<resource.columns().size();i++)header.createCell(i).setCellValue(resource.columns().get(i).header());
            for(int n=0;n<rows.size();n++){var row=sheet.createRow(n+1);for(int i=0;i<rows.get(n).size();i++)row.createCell(i).setCellValue(rows.get(n).get(i));}book.write(output);return output.toByteArray();
        }catch(IOException error){throw new UncheckedIOException(error);}
    }
}
