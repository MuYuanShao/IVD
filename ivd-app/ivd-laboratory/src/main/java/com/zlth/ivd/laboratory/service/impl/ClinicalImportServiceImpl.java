package com.zlth.ivd.laboratory.service.impl;
import com.zlth.ivd.laboratory.importing.ImportResource;
import com.zlth.ivd.laboratory.vo.ImportBatchVO;
import com.zlth.ivd.laboratory.service.ClinicalImportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.laboratory.entity.*;
import com.zlth.ivd.laboratory.mapper.*;
import com.zlth.ivd.laboratory.importing.*;
import com.zlth.ivd.laboratory.security.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Service @RequiredArgsConstructor
public class ClinicalImportServiceImpl implements ClinicalImportService {
    private final LabImportBatchMapper mapper;
    private final LabImportUnitMapper units;
    private final XlsxImportCodec codec;
    private final ImportRowExecutor executor;
    private final ClinicalRecordSupport records;
    private final PlatformTransactionManager transactions;
    public record Envelope(String index,Long actorId,Long departmentId,ImportDocument document) { }
    public record Outcome(Long batchId,List<ImportBatchVO.RowError> errors) { }
    private record Claim(LabImportBatch batch,String executionKey,boolean owned) { }
    public byte[] template(ImportResource resource){authorize(resource);return tx(()->{byte[] file=codec.template(resource);records.audit("laboratory-import","TEMPLATE",null);return file;});}
    public ImportBatchVO preflight(ImportResource resource,byte[] bytes){
        authorize(resource);var scope=InstitutionContext.require();var document=codec.read(resource,bytes);
        String index=records.index("import-file",resource+"|"+scope.actorId()+"|"+scope.departmentId()+"|"+hash(bytes));
        var prior=tx(()->mapper.selectOne(ownedQuery().eq(LabImportBatch::getContentIndex,index)));
        if(prior!=null && prior.getStatus().equals("EXECUTED"))return detail(prior.getId());
        var errors=new ArrayList<ImportBatchVO.RowError>();var keys=new HashSet<String>();
        for(var row:document.rows()){
            try{String key=executor.duplicateKey(resource,row);if(!keys.add(resource.clinical()?key:key.toUpperCase(Locale.ROOT)))errors.add(new ImportBatchVO.RowError(row.row(),"KEY","工作簿包含重复业务键"));}
            catch(RuntimeException error){errors.add(new ImportBatchVO.RowError(row.row(),"ROW","必填字段缺失或格式无效"));}
        }
        if(errors.isEmpty())for(var group:groups(document,true)){
            try{var template=transaction();template.execute(status->{executor.execute(resource,group);status.setRollbackOnly();return null;});}
            catch(RuntimeException error){errors.addAll(rowErrors(group,error));}
        }
        return tx(()->{
            var value=mapper.selectOne(ownedQuery().eq(LabImportBatch::getContentIndex,index).last("FOR UPDATE"));
            if(value!=null && value.getStatus().equals("EXECUTED"))return view(value);
            if(value!=null && value.getStatus().equals("EXECUTING"))throw new BizException(40900,"导入批次正在执行，不能重新预检");
            boolean created=value==null;if(created){value=new LabImportBatch();value.setDepartmentId(scope.departmentId());value.setActorId(scope.actorId());value.setResource(resource.name());value.setContentIndex(index);value.setCreatedAt(LocalDateTime.now());}
            value.setStatus(errors.isEmpty()?"READY":"INVALID");value.setRowCount(document.rows().size());value.setSucceeded(0);value.setExpiresAt(LocalDateTime.now().plusHours(24));value.setUpdatedAt(LocalDateTime.now());value.setEncryptedDocument(records.encrypt(new Envelope(index,scope.actorId(),scope.departmentId(),document)));
            if(created){try{mapper.insert(value);}catch(org.springframework.dao.DuplicateKeyException error){throw new BizException(40900,"相同文件已有预检批次，请刷新后查询");}}
            value.setEncryptedOutcome(records.encrypt(new Outcome(value.getId(),List.copyOf(errors))));mapper.updateById(value);records.audit("laboratory-import","PREFLIGHT",value.getId());return view(value);
        });
    }
    public ImportBatchVO execute(Long id){
        var claim=tx(()->{
            var batch=owned(id,true);authorize(ImportResource.valueOf(batch.getResource()));
            if(batch.getStatus().equals("EXECUTED"))return new Claim(batch,null,false);
            if(batch.getStatus().equals("EXECUTING") && batch.getLeaseUntil()!=null && batch.getLeaseUntil().isAfter(LocalDateTime.now()))return new Claim(batch,null,false);
            if(!Set.of("READY","EXECUTING").contains(batch.getStatus()))throw new BizException(40900,"批次存在预检错误，不能确认执行");
            if(batch.getStatus().equals("READY") && batch.getExpiresAt().isBefore(LocalDateTime.now()))throw new BizException(40900,"预检已超过24小时，请重新预检");
            String key=UUID.randomUUID().toString();batch.setExecutionKey(key);batch.setLeaseUntil(LocalDateTime.now().plusMinutes(15));batch.setStatus("EXECUTING");batch.setUpdatedAt(LocalDateTime.now());mapper.updateById(batch);records.audit("laboratory-import","CONFIRM",id);return new Claim(batch,key,true);
        });
        if(!claim.owned())return detail(id);
        var document=document(claim.batch());
        for(var group:groups(document,false)){
            String key=records.index("import-unit",id+"|"+group.stream().map(row->Integer.toString(row.row())).collect(java.util.stream.Collectors.joining(",")));
            try{tx(()->{
                var batch=lease(id,claim.executionKey());if(receipt(id,key)!=null)return null;
                executor.execute(document.resource(),group);writeReceipt(batch,key,group,"SUCCEEDED",List.of());heartbeat(batch);return null;
            });}catch(RuntimeException failure){
                tx(()->{var batch=lease(id,claim.executionKey());if(receipt(id,key)==null)writeReceipt(batch,key,group,"FAILED",rowErrors(group,failure));heartbeat(batch);return null;});
            }
        }
        return tx(()->{var batch=lease(id,claim.executionKey());var all=unitRows(id);var errors=all.stream().flatMap(unit->outcome(unit.getEncryptedOutcome(),id).errors().stream()).toList();int succeeded=all.stream().filter(unit->unit.getStatus().equals("SUCCEEDED")).mapToInt(LabImportUnit::getRowCount).sum();batch.setStatus("EXECUTED");batch.setSucceeded(succeeded);batch.setEncryptedOutcome(records.encrypt(new Outcome(id,errors)));batch.setUpdatedAt(LocalDateTime.now());mapper.updateById(batch);records.audit("laboratory-import","EXECUTE",id);return view(batch);});
    }
    public ImportBatchVO detail(Long id){return tx(()->{var value=owned(id,false);authorize(ImportResource.valueOf(value.getResource()));records.audit("laboratory-import","DETAIL",id);return view(value);});}
    private List<List<ImportDocument.Row>> groups(ImportDocument document,boolean precheck){
        if(document.resource()!=ImportResource.REPORTS && !(precheck && document.resource()==ImportResource.INTERVALS))return document.rows().stream().map(List::of).toList();
        String field=document.resource()==ImportResource.REPORTS?"reportNumber":"itemCode";var groups=new LinkedHashMap<String,List<ImportDocument.Row>>();for(var row:document.rows())groups.computeIfAbsent(row.values().get(field),ignored->new ArrayList<>()).add(row);return groups.values().stream().map(List::copyOf).toList();
    }
    private void authorize(ImportResource resource){
        if(resource.clinical()){ClinicalAccess.requireClinical();return;}ClinicalAccess.requireMasterWriter();
        if(resource==ImportResource.INSTITUTIONS && !InstitutionContext.require().role().equals("SYSTEM_ADMIN"))throw new BizException(40300,"仅管理员可导入检测机构");
    }
    private TransactionTemplate transaction(){var template=new TransactionTemplate(transactions);template.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);template.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);return template;}
    private <T> T tx(Supplier<T> operation){return transaction().execute(status->operation.get());}
    private LambdaQueryWrapper<LabImportBatch> ownedQuery(){var scope=InstitutionContext.require();InstitutionContext.requireUserActor();var q=new LambdaQueryWrapper<LabImportBatch>().eq(LabImportBatch::getInstitutionId,scope.institutionId()).eq(LabImportBatch::getActorId,scope.actorId());if(scope.departmentId()==null)q.isNull(LabImportBatch::getDepartmentId);else q.eq(LabImportBatch::getDepartmentId,scope.departmentId());return q;}
    private LabImportBatch owned(Long id,boolean lock){var q=ownedQuery().eq(LabImportBatch::getId,id);if(lock)q.last("FOR UPDATE");var value=mapper.selectOne(q);if(value==null)throw new BizException(40400,"导入批次不存在或无权访问");return value;}
    private LabImportBatch lease(Long id,String key){var value=owned(id,true);if(!value.getStatus().equals("EXECUTING") || !Objects.equals(value.getExecutionKey(),key))throw new BizException(40900,"批次执行权已变更，请刷新后查询");return value;}
    private void heartbeat(LabImportBatch value){value.setLeaseUntil(LocalDateTime.now().plusMinutes(15));value.setUpdatedAt(LocalDateTime.now());mapper.updateById(value);}
    private LambdaQueryWrapper<LabImportUnit> unitQuery(Long id){return new LambdaQueryWrapper<LabImportUnit>().eq(LabImportUnit::getInstitutionId,InstitutionContext.require().institutionId()).eq(LabImportUnit::getBatchId,id);}
    private LabImportUnit receipt(Long id,String key){return units.selectOne(unitQuery(id).eq(LabImportUnit::getUnitKey,key));}
    private List<LabImportUnit> unitRows(Long id){return units.selectList(unitQuery(id).orderByAsc(LabImportUnit::getId));}
    private void writeReceipt(LabImportBatch batch,String key,List<ImportDocument.Row> group,String status,List<ImportBatchVO.RowError> errors){var unit=new LabImportUnit();unit.setDepartmentId(batch.getDepartmentId());unit.setBatchId(batch.getId());unit.setUnitKey(key);unit.setStatus(status);unit.setRowCount(group.size());unit.setEncryptedOutcome(records.encrypt(new Outcome(batch.getId(),errors)));unit.setCreatedAt(LocalDateTime.now());units.insert(unit);}
    private ImportDocument document(LabImportBatch value){var envelope=records.decrypt(value.getEncryptedDocument(),Envelope.class);if(!Objects.equals(envelope.index(),value.getContentIndex()) || !Objects.equals(envelope.actorId(),value.getActorId()) || !Objects.equals(envelope.departmentId(),value.getDepartmentId()) || !envelope.document().resource().name().equals(value.getResource()) || envelope.document().rows().size()!=value.getRowCount())throw new BizException(40900,"导入文档归属或完整性校验失败");return envelope.document();}
    private Outcome outcome(String encrypted,Long id){var outcome=records.decrypt(encrypted,Outcome.class);if(!Objects.equals(outcome.batchId(),id))throw new BizException(40900,"导入结果归属校验失败");return outcome;}
    private ImportBatchVO view(LabImportBatch value){document(value);int succeeded=value.getSucceeded();List<ImportBatchVO.RowError> errors;if(value.getStatus().equals("EXECUTING")){var units=unitRows(value.getId());succeeded=units.stream().filter(unit->unit.getStatus().equals("SUCCEEDED")).mapToInt(LabImportUnit::getRowCount).sum();errors=units.stream().flatMap(unit->outcome(unit.getEncryptedOutcome(),value.getId()).errors().stream()).toList();}else errors=outcome(value.getEncryptedOutcome(),value.getId()).errors();return new ImportBatchVO(value.getId(),value.getResource(),value.getStatus(),value.getRowCount(),succeeded,errors);}
    private List<ImportBatchVO.RowError> rowErrors(List<ImportDocument.Row> group,RuntimeException failure){String message=failure instanceof BizException error?switch(error.getErrorCode()){case 40300,40400 -> "引用不存在、已停用或没有本机构科室权限";case 40900 -> "重复业务键、状态或引用冲突";case 40001 -> "必填字段、日期、单位或枚举校验失败";default -> "业务处理失败，未提交该事务单元";}:"处理失败，未提交该事务单元；不显示原始资料";return group.stream().map(row->new ImportBatchVO.RowError(row.row(),"ROW",message)).toList();}
    private String hash(byte[] value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
