package com.zlth.ivd.laboratory.service;

import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.laboratory.dto.*;
import com.zlth.ivd.laboratory.service.impl.SampleServiceImpl;
import com.zlth.ivd.laboratory.service.impl.LabResultServiceImpl;
import java.time.LocalDateTime;
import java.util.Map;
import com.zlth.ivd.common.exception.BizException;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.junit.jupiter.api.Assertions.*;

/** Fictional clinical data only; real services, MyBatis and transaction manager. */
class SampleIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private SampleService samples;
    private PatientService patients;
    private final InstitutionContext.Scope tech=new InstitutionContext.Scope(1L,11L,101L,"LAB_TECH");
    @BeforeEach void setup(){
        context=new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("ivd.isolation.enabled","true")));
        context.register(PatientIntegrationTest.Config.class,SampleServiceImpl.class,LabResultServiceImpl.class,com.zlth.ivd.laboratory.service.impl.DeviceCapabilityServiceImpl.class,com.zlth.ivd.laboratory.service.impl.ResultEvidenceServiceImpl.class);
        context.refresh();samples=context.getBean(SampleService.class);patients=context.getBean(PatientService.class);
    }
    @AfterEach void cleanup(){context.close();assertTrue(InstitutionContext.current().isEmpty());}
    @Test void registeredSampleCanBeReadFromItsAuthorizedPatient(){
        InstitutionContext.within(tech,()->{
            var patient=patients.create(new PatientRequest("FICTION-P-001","虚构患者","FEMALE",null,null));
            var sample=samples.create(new SampleRequest(patient.id(),"FICTION-S-001","血清",LocalDateTime.now().minusHours(1)));
            assertEquals(patient.id(),samples.detail(sample.id()).patientId());
            assertEquals("COLLECTED",samples.detail(sample.id()).status());
            assertEquals(sample.id(),samples.list(1,20,patient.id()).getRecords().get(0).id());return null;
        });
    }
    @Test void receivedPatientSampleCanPersistAnInstrumentResult(){
        InstitutionContext.within(tech,()->{
            var patient=patients.create(new PatientRequest("FICTION-P-001","虚构患者","FEMALE",null,null));
            var sample=samples.create(new SampleRequest(patient.id(),"FICTION-S-001","血清",LocalDateTime.now().minusHours(1)));
            samples.receive(sample.id(),new SampleActionRequest(sample.version(),null));
            var results=context.getBean(LabResultService.class);
            var result=results.create(sample.id(),new ResultEntryRequest(1L,"INSTRUMENT",1L,null,"4.2","mmol/L",LocalDateTime.now().minusMinutes(1),null));
            assertEquals(sample.id(),results.detail(result.id()).sampleId());
            assertEquals(1L,results.detail(result.id()).deviceId());
            assertEquals("MISSING",result.referenceStatus());
            assertEquals(1,results.list(sample.id(),1,20).getTotal());return null;
        });
    }
    @Test void onlyCollectedSamplesCanBeReceivedAndRejectedSamplesCannotProduceResults(){
        InstitutionContext.within(tech,()->{
            var patient=patients.create(new PatientRequest("FICTION-P-001","虚构患者","FEMALE",null,null));
            var sample=samples.create(new SampleRequest(patient.id(),"FICTION-S-001","血清",LocalDateTime.now().minusHours(1)));
            var results=context.getBean(LabResultService.class);
            assertThrows(BizException.class,()->results.create(sample.id(),entry(1L,"INSTRUMENT",1L,null,null)));
            assertThrows(BizException.class,()->samples.reject(sample.id(),new SampleActionRequest(0," ")));
            var rejected=samples.reject(sample.id(),new SampleActionRequest(0,"虚构拒收原因"));
            assertEquals("REJECTED",rejected.status());
            assertThrows(BizException.class,()->samples.receive(sample.id(),new SampleActionRequest(rejected.version(),null)));
            assertThrows(BizException.class,()->results.create(sample.id(),entry(1L,"INSTRUMENT",1L,null,null)));return null;
        });
    }
    @Test void foreignDepartmentInstitutionAndRevokedPatientCannotReadOrWriteSamplesOrResults(){
        var patient=InstitutionContext.within(tech,()->patients.create(new PatientRequest("FICTION-P-001","虚构患者","FEMALE",null,null)));
        var sample=InstitutionContext.within(tech,()->samples.create(new SampleRequest(patient.id(),"FICTION-S-001","血清",LocalDateTime.now().minusHours(1))));
        var results=context.getBean(LabResultService.class);
        var result=InstitutionContext.within(tech,()->{samples.receive(sample.id(),new SampleActionRequest(0,null));return results.create(sample.id(),entry(1L,"INSTRUMENT",1L,null,null));});
        for(var scope:List.of(new InstitutionContext.Scope(1L,12L,102L,"LAB_TECH"),new InstitutionContext.Scope(2L,21L,201L,"LAB_TECH"),new InstitutionContext.Scope(1L,11L,99L,"SYSTEM_ADMIN"))){
            InstitutionContext.within(scope,()->{
                assertThrows(BizException.class,()->samples.detail(sample.id()));
                assertThrows(BizException.class,()->results.detail(result.id()));
                assertThrows(BizException.class,()->results.versions(result.id()));
                assertThrows(BizException.class,()->samples.create(new SampleRequest(patient.id(),"FICTION-S-002","血清",LocalDateTime.now().minusHours(1))));return null;
            });
        }
        InstitutionContext.within(new InstitutionContext.Scope(1L,null,99L,"SYSTEM_ADMIN"),()->{patients.setDepartmentAccess(patient.id(),new PatientDepartmentRequest(11L,false,"REVOCATION"));return null;});
        InstitutionContext.within(tech,()->{
            assertEquals(0,samples.list(1,20,null).getTotal());
            assertThrows(BizException.class,()->results.detail(result.id()));
            assertThrows(BizException.class,()->results.update(result.id(),entry(1L,"INSTRUMENT",1L,null,0)));return null;
        });
    }
    @Test void instrumentRequiresOwnActiveDeviceAndProjectWhileManualAndExternalRemainExplicit(){
        InstitutionContext.within(tech,()->{
            var sample=receivedSample();var results=context.getBean(LabResultService.class);
            for(Long device:List.of(2L,3L,4L))assertThrows(BizException.class,()->results.create(sample,entry(1L,"INSTRUMENT",device,null,null)));
            assertThrows(BizException.class,()->results.create(sample,entry(1L,"INSTRUMENT",null,null,null)));
            assertThrows(BizException.class,()->results.create(sample,entry(2L,"INSTRUMENT",1L,null,null)));
            assertThrows(BizException.class,()->results.create(sample,entry(1L,"MANUAL",1L,null,null)));
            assertThrows(BizException.class,()->results.create(sample,entry(1L,"EXTERNAL",null,2L,null)));
            assertThrows(BizException.class,()->results.create(sample,entry(1L,"EXTERNAL",null,4L,null)));
            var manual=results.create(sample,entry(1L,"MANUAL",null,null,null));
            assertNull(manual.deviceId());assertEquals("NOT_APPLICABLE",manual.measurementStatus());
            var external=results.update(manual.id(),entry(1L,"EXTERNAL",null,3L,manual.version()));
            assertEquals(3L,external.testingInstitutionId());assertNull(external.deviceId());
            assertThrows(BizException.class,()->results.create(sample,entry(1L,"MANUAL",null,null,null)));return null;
        });
    }
    @Test void recordedIntervalsAndPatientContextDoNotChangeWhenMasterDataChanges(){
        InstitutionContext.within(tech,()->{
            var patient=patients.create(new PatientRequest("FICTION-P-001","虚构患者","FEMALE",LocalDate.of(1990,1,1),null));
            var sample=samples.create(new SampleRequest(patient.id(),"FICTION-S-001","血清",LocalDateTime.now().minusHours(1)));
            samples.receive(sample.id(),new SampleActionRequest(0,null));
            var director=new InstitutionContext.Scope(1L,11L,103L,"LAB_DIRECTOR");
            var catalog=context.getBean(LabCatalogService.class);
            var interval=InstitutionContext.within(director,()->catalog.createInterval(new IntervalRequest(1L,null,"REFERENCE","ANY",0,73000,new BigDecimal("3.5"),new BigDecimal("5.5"),null,LocalDate.now().minusDays(1),LocalDate.now().plusDays(1),true,null)));
            var results=context.getBean(LabResultService.class);var result=results.create(sample.id(),entry(1L,"INSTRUMENT",1L,null,null));
            assertEquals("NORMAL",result.referenceStatus());assertEquals("5.5",result.referenceInterval().upperBound().stripTrailingZeros().toPlainString());
            InstitutionContext.within(director,()->catalog.updateInterval(interval.id(),new IntervalRequest(1L,null,"REFERENCE","ANY",0,73000,new BigDecimal("1"),new BigDecimal("2"),null,interval.validFrom(),interval.validTo(),true,interval.version())));
            assertEquals("NORMAL",results.detail(result.id()).referenceStatus());
            var revised=results.update(result.id(),entry(1L,"INSTRUMENT",1L,null,result.version()));
            assertEquals("HIGH",revised.referenceStatus());
            assertEquals(List.of("NORMAL","HIGH"),results.versions(result.id()).stream().map(r->r.referenceStatus()).toList());
            assertThrows(BizException.class,()->results.update(result.id(),entry(1L,"INSTRUMENT",1L,null,0)));return null;
        });
    }
    @Test void failedRevisionEvidenceCannotCommitResultUpdate(){
        InstitutionContext.within(tech,()->{
            var sample=receivedSample();var results=context.getBean(LabResultService.class);
            var result=results.create(sample,entry(1L,"INSTRUMENT",1L,null,null));
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(context.getBean(javax.sql.DataSource.class));
            jdbc.execute("ALTER TABLE lab_result_revision ADD CONSTRAINT reject_revised_version CHECK(version=0)");
            assertThrows(org.springframework.dao.DataAccessException.class,()->results.update(result.id(),entry(1L,"MANUAL",null,null,0)));
            assertEquals(0,results.detail(result.id()).version());assertEquals(1L,results.detail(result.id()).deviceId());
            assertEquals(1,results.versions(result.id()).size());return null;
        });
    }
    @Test void deviceAndPatientQueriesFollowOnlyAuthorizedSamples(){
        InstitutionContext.within(tech,()->{
            var sample=receivedSample();var results=context.getBean(LabResultService.class);
            var result=results.create(sample,entry(1L,"INSTRUMENT",1L,null,null));
            assertEquals(result.id(),results.search(1L,result.patientId(),1,20).getRecords().get(0).id());
            assertThrows(BizException.class,()->results.search(2L,null,1,20));
            assertEquals(List.of(1L,3L),results.testingInstitutions().stream().map(option->option.id()).sorted().toList());
            var other=new InstitutionContext.Scope(1L,12L,102L,"LAB_TECH");
            InstitutionContext.within(other,()->{assertEquals(0,results.search(null,null,1,20).getTotal());return null;});
            InstitutionContext.within(new InstitutionContext.Scope(1L,null,99L,"SYSTEM_ADMIN"),()->{patients.setDepartmentAccess(result.patientId(),new PatientDepartmentRequest(11L,false,"REVOCATION"));return null;});
            assertEquals(0,results.search(null,null,1,20).getTotal());return null;
        });
    }
    @Test void sameVersionConcurrentResultEditsHaveExactlyOneWinner() throws Exception {
        var results=context.getBean(LabResultService.class);
        var result=InstitutionContext.within(tech,()->results.create(receivedSample(),entry(1L,"INSTRUMENT",1L,null,null)));
        var start=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Boolean> edit=()->{
            start.await();try{return InstitutionContext.within(tech,()->{results.update(result.id(),entry(1L,"MANUAL",null,null,0));return true;});}
            catch(BizException conflict){assertEquals(40900,conflict.getErrorCode());return false;}
        };
        try{
            var first=pool.submit(edit);var second=pool.submit(edit);start.countDown();
            assertNotEquals(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS));
            InstitutionContext.within(tech,()->{assertEquals(2,results.versions(result.id()).size());return null;});
        }finally{pool.shutdownNow();}
    }
    @Test void deviceCapabilityMustBeExplicitlyConfiguredAndCanBeDisabled(){
        var capabilities=context.getBean(DeviceCapabilityService.class);
        var director=new InstitutionContext.Scope(1L,11L,103L,"LAB_DIRECTOR");
        InstitutionContext.within(director,()->{
            var saved=capabilities.set(1L,new DeviceCapabilityRequest(3L,true,null));
            capabilities.requireExecutable(1L,3L);
            capabilities.set(1L,new DeviceCapabilityRequest(3L,false,saved.version()));
            assertThrows(BizException.class,()->capabilities.requireExecutable(1L,3L));return null;
        });
    }
    @Test void evidenceBridgeKeepsRealBatchLoadAndConsumptionIdentity(){
        InstitutionContext.within(tech,()->{
            var results=context.getBean(LabResultService.class);var result=results.create(receivedSample(),entry(1L,"INSTRUMENT",1L,null,null));
            var evidence=context.getBean(ResultEvidenceService.class).bind(result.id(),new ResultEvidenceRequest(result.version(),51L,61L,71L));
            assertEquals(51L,evidence.batch().batchId());assertEquals(61L,evidence.batch().deviceLoadId());assertEquals(71L,evidence.consumption().id());return null;
        });
    }
    @Test void deviceRegistryEnforcesLocationDepartmentAndVersionAndInstrumentRequiredItems(){
        var registry=context.getBean(com.zlth.ivd.inventory.service.DeviceRegistryService.class);
        InstitutionContext.within(new InstitutionContext.Scope(1L,11L,103L,"LAB_DIRECTOR"),()->{
            var request=new com.zlth.ivd.inventory.dto.DeviceRegistryRequest("NEW-DEVICE","虚构新设备",null,null,102L,11L,true,null,null,null);
            var saved=registry.create(request);assertEquals(11L,registry.detail(saved.id()).departmentId());assertEquals(0,saved.version());
            var update=new com.zlth.ivd.inventory.dto.DeviceRegistryRequest("NEW-DEVICE","虚构修订设备",null,null,102L,11L,false,null,null,0);
            assertEquals(1,registry.update(saved.id(),update).version());assertThrows(BizException.class,()->registry.update(saved.id(),update));
            assertThrows(BizException.class,()->registry.create(new com.zlth.ivd.inventory.dto.DeviceRegistryRequest("INVALID-DEVICE","虚构设备",null,null,103L,11L,true,null,null,null)));
            var catalog=context.getBean(LabCatalogService.class);var item=catalog.item(1L);catalog.updateItem(1L,new TestItemRequest(item.code(),item.name(),item.methodology(),item.resultType(),item.unit(),true,item.version(),true));return null;
        });
        InstitutionContext.within(tech,()->{assertThrows(BizException.class,()->context.getBean(LabResultService.class).create(receivedSample(),entry(1L,"MANUAL",null,null,null)));return null;});
    }
    @Test void evidenceBridgeIsIdempotentAndRejectsForeignOrMismatchedReferencesWithoutStockMutation(){
        InstitutionContext.within(tech,()->{
            var result=context.getBean(LabResultService.class).create(receivedSample(),entry(1L,"INSTRUMENT",1L,null,null));var evidence=context.getBean(ResultEvidenceService.class);var request=new ResultEvidenceRequest(result.version(),51L,61L,71L);
            var saved=evidence.bind(result.id(),request);assertEquals(saved.id(),evidence.bind(result.id(),request).id());
            assertThrows(BizException.class,()->evidence.bind(result.id(),new ResultEvidenceRequest(0,52L,62L,72L)));
            assertThrows(BizException.class,()->evidence.bind(result.id(),new ResultEvidenceRequest(0,51L,62L,71L)));
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(context.getBean(javax.sql.DataSource.class));assertEquals(0,new java.math.BigDecimal("10").compareTo(jdbc.queryForObject("SELECT stock FROM reagent_batch WHERE id=51",java.math.BigDecimal.class)));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM consumption_record",Integer.class));return null;
        });
    }
    @Test void batchTraceReturnsLinkedHistoricalResultVersionAfterCurrentResultChanges(){
        InstitutionContext.within(tech,()->{
            var results=context.getBean(LabResultService.class);var result=results.create(receivedSample(),entry(1L,"INSTRUMENT",1L,null,null));var evidence=context.getBean(ResultEvidenceService.class);evidence.bind(result.id(),new ResultEvidenceRequest(0,51L,61L,71L));
            results.update(result.id(),entry(1L,"MANUAL",null,null,0));var trace=evidence.trace(51L,71L,1,20);assertEquals(1,trace.getTotal());assertEquals(0,trace.getRecords().get(0).result().version());assertEquals(1L,trace.getRecords().get(0).result().deviceId());return null;
        });
    }
    private Long receivedSample(){
        var patient=patients.create(new PatientRequest("FICTION-P-001","虚构患者","FEMALE",null,null));
        var sample=samples.create(new SampleRequest(patient.id(),"FICTION-S-001","血清",LocalDateTime.now().minusHours(1)));
        return samples.receive(sample.id(),new SampleActionRequest(0,null)).id();
    }
    private ResultEntryRequest entry(Long item,String mode,Long device,Long testing,Integer version){return new ResultEntryRequest(item,mode,device,testing,"4.2","mmol/L",LocalDateTime.now().minusMinutes(1),version);}
}
