package com.zlth.ivd.laboratory.importing;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.inventory.dto.DeviceRegistryRequest;
import com.zlth.ivd.inventory.service.*;
import com.zlth.ivd.laboratory.dto.*;
import com.zlth.ivd.laboratory.service.*;
import com.zlth.ivd.system.dto.InstitutionRequest;
import com.zlth.ivd.system.service.InstitutionService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
/** Delegates to business services, never foreign mappers; a report and its results share one transaction. */
@Component @RequiredArgsConstructor
public class ImportRowExecutor {
    private final PatientService patients;
    private final SampleService samples;
    private final LabCatalogService catalog;
    private final LabResultService results;
    private final ReportService reports;
    private final DeviceDirectoryService devices;
    private final DeviceRegistryService registry;
    private final DeviceCapabilityService capabilities;
    private final InstitutionService institutions;
    private final jakarta.validation.Validator validator;
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public void execute(ImportResource resource,List<ImportDocument.Row> group){
        if(resource==ImportResource.REPORTS){
            var first=group.get(0).values();String number=required(first,"reportNumber"),sampleNumber=required(first,"sampleNumber");var sample=samples.requireNumber(sampleNumber);var ids=new ArrayList<Long>();
            for(var row:group){if(!Objects.equals(row.values().get("reportNumber"),number) || !Objects.equals(row.values().get("sampleNumber"),sampleNumber))throw new BizException(40001,"同份报告必须使用同一个编号和样本");ids.add(results.create(sample.id(),resultRequest(row.values())).id());}
            reports.create(new ReportRequest(number,sample.id(),ids,null));return;
        }
        if(group.size()!=1){for(var row:group)execute(resource,List.of(row));return;}var value=group.get(0).values();
        switch(resource){
            case PATIENTS -> patients.create(new PatientRequest(required(value,"number"),required(value,"name"),required(value,"sex"),date(value,"birthDate"),null));
            case SAMPLES -> samples.create(new SampleRequest(patients.requireNumber(required(value,"patientNumber")).id(),required(value,"number"),required(value,"specimenType"),datetime(value,"collectedAt")));
            case RESULTS -> results.create(samples.requireNumber(required(value,"sampleNumber")).id(),resultRequest(value));
            case ITEMS -> catalog.createItem(new TestItemRequest(required(value,"code"),required(value,"name"),required(value,"methodology"),required(value,"resultType"),value.get("unit"),bool(value,"active"),null,bool(value,"deviceRequired")));
            case INTERVALS -> catalog.createInterval(new IntervalRequest(catalog.requireCode(required(value,"itemCode")).id(),device(value),required(value,"kind"),required(value,"sex"),integer(value,"minAgeDays"),integer(value,"maxAgeDays"),decimal(value,"lowerBound"),decimal(value,"upperBound"),nullable(value,"normalText"),date(value,"validFrom"),date(value,"validTo"),bool(value,"active"),null));
            case DEVICES -> registry.create(new DeviceRegistryRequest(required(value,"code"),required(value,"name"),nullable(value,"model"),nullable(value,"manufacturer"),id(value,"locationId",true),id(value,"departmentId",false),bool(value,"active"),date(value,"lastCalibrationDate"),date(value,"nextCalibrationDate"),null));
            case CAPABILITIES -> capabilities.set(devices.requireOwnedCode(required(value,"deviceCode")).id(),new DeviceCapabilityRequest(catalog.requireCode(required(value,"itemCode")).id(),bool(value,"active"),null));
            case INSTITUTIONS -> institutions.create(validated(new InstitutionRequest(required(value,"code"),required(value,"name"),required(value,"type"),nullable(value,"contactPerson"),nullable(value,"contactPhone"),nullable(value,"address"),nullable(value,"capabilities"),bool(value,"status")?1:0)));
            default -> throw new IllegalArgumentException("Unsupported resource");
        }
    }
    public String duplicateKey(ImportResource resource,ImportDocument.Row row){var v=row.values();return switch(resource){
        case PATIENTS,SAMPLES -> required(v,"number");
        case ITEMS,DEVICES,INSTITUTIONS -> required(v,"code");
        case CAPABILITIES -> required(v,"deviceCode")+"|"+required(v,"itemCode");
        case RESULTS -> required(v,"sampleNumber")+"|"+required(v,"itemCode");
        case REPORTS -> required(v,"reportNumber")+"|"+required(v,"itemCode");
        case INTERVALS -> String.join("|",v.values());
    };}
    private ResultEntryRequest resultRequest(Map<String,String> value){
        Long testing=null;String code=nullable(value,"testingInstitutionCode");
        if(code!=null)testing=institutions.options().stream().filter(v->v.code().equals(code)).findFirst().orElseThrow(()->new BizException(40400,"检测机构编码不存在或无权引用")).id();
        var item=catalog.requireCode(required(value,"itemCode"));if(!Objects.equals(item.unit(),value.get("unit")))throw new BizException(40001,"导入单位与项目单位不一致，不自动换算");
        return new ResultEntryRequest(item.id(),required(value,"executionMode"),device(value),testing,required(value,"result"),value.get("unit"),datetime(value,"testedAt"),null);
    }
    private Long device(Map<String,String> value){String code=nullable(value,"deviceCode");return code==null?null:devices.requireOwnedCode(code).id();}
    private <T> T validated(T value){if(!validator.validate(value).isEmpty())throw new BizException(40001,"导入机构字段校验失败");return value;}
    private String nullable(Map<String,String> value,String key){String data=value.get(key);return data==null || data.isBlank()?null:data.trim();}
    private String required(Map<String,String> value,String key){String data=nullable(value,key);if(data==null)throw new BizException(40001,"导入必填字段缺失");return data;}
    private Boolean bool(Map<String,String> value,String key){String data=required(value,key);return switch(data){case "TRUE","1" -> true;case "FALSE","0" -> false;default -> throw new BizException(40001,"导入布尔字段须为 TRUE/FALSE 或 1/0");};}
    private Integer integer(Map<String,String> value,String key){try{return Integer.valueOf(required(value,key));}catch(NumberFormatException error){throw new BizException(40001,"导入整数无效");}}
    private Long id(Map<String,String> value,String key,boolean required){String data=required?required(value,key):nullable(value,key);if(data==null)return null;try{long id=Long.parseLong(data);if(id<=0)throw new NumberFormatException();return id;}catch(NumberFormatException error){throw new BizException(40001,"导入引用 ID 无效");}}
    private BigDecimal decimal(Map<String,String> value,String key){String data=nullable(value,key);try{return data==null?null:new BigDecimal(data);}catch(NumberFormatException error){throw new BizException(40001,"导入数值无效");}}
    private LocalDate date(Map<String,String> value,String key){String data=nullable(value,key);try{return data==null?null:data.length()==10?LocalDate.parse(data):LocalDateTime.parse(data).toLocalDate();}catch(java.time.format.DateTimeParseException error){throw new BizException(40001,"导入日期无效");}}
    private LocalDateTime datetime(Map<String,String> value,String key){try{return LocalDateTime.parse(required(value,key));}catch(java.time.format.DateTimeParseException error){throw new BizException(40001,"导入时间须为本地 ISO 日期时间");}}
}
