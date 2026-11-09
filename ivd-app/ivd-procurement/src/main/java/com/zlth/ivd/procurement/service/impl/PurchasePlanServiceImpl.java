package com.zlth.ivd.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.catalog.service.CatalogReferenceService;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.enums.ApprovalStatusEnum;
import com.zlth.ivd.common.enums.PurchaseOrderStatusEnum;
import com.zlth.ivd.common.enums.ShortageStatusEnum;
import com.zlth.ivd.common.enums.SettlementModeEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.license.entity.Supplier;
import com.zlth.ivd.license.service.SupplierReferenceService;
import com.zlth.ivd.procurement.dto.CreateManualOrderRequest;
import com.zlth.ivd.procurement.dto.CreatePurchasePlanRequest;
import com.zlth.ivd.procurement.entity.*;
import com.zlth.ivd.procurement.mapper.*;
import com.zlth.ivd.procurement.service.ProcurementService;
import com.zlth.ivd.procurement.service.PurchasePlanService;
import com.zlth.ivd.procurement.vo.PurchasePlanVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor=Exception.class)
public class PurchasePlanServiceImpl implements PurchasePlanService {
    private static final Set<String> STATUSES=Set.of("DRAFT","PENDING_REVIEW","APPROVED","REJECTED","ORDERED");
    private final PurchasePlanMapper plans;
    private final PurchasePlanItemMapper items;
    private final PurchasePlanEventMapper events;
    private final PurchaseOrderMapper orders;
    private final ShortageOrderMapper shortages;
    private final ShortageOrderItemMapper shortageItems;
    private final SmartReplenishSuggestionMapper suggestions;
    private final CatalogReferenceService catalogs;
    private final SupplierReferenceService suppliers;
    private final ProcurementService procurement;
    private final ObjectMapper json;

    @Override @Transactional(readOnly=true)
    public PageVO<PurchasePlanVO> list(int page,int size,String status,String keyword) {
        role("SYSTEM_ADMIN","PURCHASER","STOREKEEPER","LAB_DIRECTOR","FINANCE");
        if(page<1 || size<1 || size>100)throw new BizException(40001,"采购计划分页参数无效");
        if(status!=null && !status.isBlank() && !STATUSES.contains(status))
            throw new BizException(40001,"采购计划状态无效");
        if(keyword!=null && keyword.length()>100)throw new BizException(40001,"关键词过长");
        var query=new LambdaQueryWrapper<PurchasePlan>();
        if(status!=null && !status.isBlank())query.eq(PurchasePlan::getStatus,status);
        if(keyword!=null && !keyword.isBlank())query.like(PurchasePlan::getPlanNo,keyword.strip());
        query.orderByDesc(PurchasePlan::getCreatedAt,PurchasePlan::getId);
        var result=plans.selectPage(new Page<>(page,size),query);
        return new PageVO<>(result.getTotal(),page,size,result.getRecords().stream().map(this::view).toList());
    }

    @Override @Transactional(readOnly=true)
    public PurchasePlanVO get(Long id) {
        role("SYSTEM_ADMIN","PURCHASER","STOREKEEPER","LAB_DIRECTOR","FINANCE");
        var plan=plans.selectById(id);
        if(plan==null)throw new BizException(40400,"采购计划不存在或不可访问");
        return view(plan);
    }

    @Override
    public PurchasePlanVO create(CreatePurchasePlanRequest request) {
        long actor=role("SYSTEM_ADMIN","PURCHASER","STOREKEEPER");
        validateHeader(request);
        var plan=new PurchasePlan();
        plan.setSourceType(request.getSourceType());
        plan.setShortageOrderId("SHORTAGE".equals(request.getSourceType())?request.getShortageId():null);
        plan.setStatus("DRAFT");plan.setCreatedBy(actor);
        plan.setCreatedAt(LocalDateTime.now());plan.setUpdatedAt(plan.getCreatedAt());
        header(plan,request);
        List<PurchasePlanItem> rows=resolveRows(request);
        try { plans.insert(plan); }
        catch(DuplicateKeyException duplicate){throw new BizException(40900,"该来源已有采购计划，请查询原计划");}
        plan.setPlanNo("PP-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+plan.getId());
        plans.updateById(plan);
        saveRows(plan.getId(),rows);
        audit(plan,null,"创建采购计划");
        return view(plan);
    }

    @Override
    public PurchasePlanVO update(Long id,CreatePurchasePlanRequest request) {
        long actor=role("SYSTEM_ADMIN","PURCHASER","STOREKEEPER");
        var plan=lock(id);
        if(!Set.of("DRAFT","REJECTED").contains(plan.getStatus()))
            throw new BizException(40900,"仅草稿或已驳回采购计划可以编辑");
        if(!Objects.equals(plan.getCreatedBy(),actor) && !"SYSTEM_ADMIN".equals(InstitutionContext.require().role()))
            throw new BizException(40300,"仅创建人或系统管理员可以编辑计划");
        validateHeader(request);
        if(!Objects.equals(plan.getSourceType(),request.getSourceType())
                || !Objects.equals(plan.getShortageOrderId(),"SHORTAGE".equals(request.getSourceType())?request.getShortageId():null))
            throw new BizException(40001,"不能变更采购计划来源");
        var rows=resolveRows(request);
        String from=plan.getStatus();
        header(plan,request);plan.setStatus("DRAFT");plan.setUpdatedAt(LocalDateTime.now());
        plans.updateById(plan);
        plans.update(null,new LambdaUpdateWrapper<PurchasePlan>().eq(PurchasePlan::getId,id)
                .set(PurchasePlan::getReviewedBy,null).set(PurchasePlan::getReviewedAt,null)
                .set(PurchasePlan::getReviewComment,null));
        plan.setReviewedBy(null);plan.setReviewedAt(null);plan.setReviewComment(null);
        items.delete(new LambdaQueryWrapper<PurchasePlanItem>().eq(PurchasePlanItem::getPlanId,id));
        saveRows(id,rows);audit(plan,from,"修改采购计划");
        return view(plan);
    }

    @Override
    public PurchasePlanVO submit(Long id) {
        long actor=role("SYSTEM_ADMIN","PURCHASER","STOREKEEPER");
        var plan=lock(id);
        if(!Objects.equals(plan.getCreatedBy(),actor) && !"SYSTEM_ADMIN".equals(InstitutionContext.require().role()))
            throw new BizException(40300,"仅创建人或系统管理员可以提交计划");
        if(!Set.of("DRAFT","REJECTED").contains(plan.getStatus()))
            throw new BizException(40900,"当前状态不能提交采购计划");
        validateCurrentRows(plan);
        String from=plan.getStatus();plan.setStatus("PENDING_REVIEW");plan.setUpdatedAt(LocalDateTime.now());
        plans.updateById(plan);audit(plan,from,"提交采购计划审批");return view(plan);
    }

    @Override
    public PurchasePlanVO approve(Long id,String comment) {
        long actor=role("SYSTEM_ADMIN","LAB_DIRECTOR");
        var plan=lock(id);requirePending(plan,actor);
        validateCurrentRows(plan);
        plan.setStatus("APPROVED");review(plan,actor,comment);
        plans.updateById(plan);audit(plan,"PENDING_REVIEW",comment==null?"批准采购计划":comment);
        return view(plan);
    }

    @Override
    public PurchasePlanVO reject(Long id,String comment) {
        long actor=role("SYSTEM_ADMIN","LAB_DIRECTOR");
        var plan=lock(id);requirePending(plan,actor);
        if(comment==null || comment.isBlank())throw new BizException(40001,"驳回原因必填");
        plan.setStatus("REJECTED");review(plan,actor,comment);
        plans.updateById(plan);audit(plan,"PENDING_REVIEW",comment);return view(plan);
    }

    @Override
    public PurchasePlanVO convert(Long id) {
        role("SYSTEM_ADMIN","PURCHASER","STOREKEEPER");
        var plan=lock(id);
        if("ORDERED".equals(plan.getStatus()))return view(plan);
        if(!"APPROVED".equals(plan.getStatus()))throw new BizException(40900,"采购计划未批准，不能转采购订单");
        var rows=validateCurrentRows(plan);
        if(plan.getShortageOrderId()!=null) {
            var shortage=shortages.selectOne(new LambdaQueryWrapper<ShortageOrder>()
                    .eq(ShortageOrder::getId,plan.getShortageOrderId()).last("FOR UPDATE"));
            if(shortage==null || shortage.getStatus()!=ShortageStatusEnum.PENDING_PROCUREMENT || shortage.getPoId()!=null)
                throw new BizException(40900,"缺货来源已处理，不能再次转单");
        }
        for(var row:rows)if(row.getSuggestionId()!=null) {
            var suggestion=suggestions.selectOne(new LambdaQueryWrapper<SmartReplenishSuggestion>()
                    .eq(SmartReplenishSuggestion::getId,row.getSuggestionId()).last("FOR UPDATE"));
            if(suggestion==null || !"PENDING".equals(suggestion.getStatus())
                    || !Objects.equals(suggestion.getReagentId(),row.getReagentId()))
                throw new BizException(40900,"补货建议来源已变化，不能转单");
        }
        var request=new CreateManualOrderRequest();
        request.setOrderDate(plan.getOrderDate().toString());
        request.setExpectedDeliveryDate(plan.getExpectedDeliveryDate()==null?null:plan.getExpectedDeliveryDate().toString());
        request.setSettlementMode(plan.getSettlementMode());request.setRemark(plan.getRemark());
        var requestRows=new ArrayList<CreateManualOrderRequest.ManualOrderItem>();
        for(var row:rows) {
            var item=new CreateManualOrderRequest.ManualOrderItem();item.setReagentId(row.getReagentId());
            item.setSupplierId(row.getSupplierId());item.setQuantity(row.getQuantity());requestRows.add(item);
        }
        request.setItems(requestRows);
        var generated=procurement.createManualOrder(request);
        for(var order:generated) {
            order.setPlanId(plan.getId());order.setApprovalOrigin("PLAN");
            order.setSourceType(plan.getSourceType());order.setStatus(PurchaseOrderStatusEnum.SENT);
            if(plan.getShortageOrderId()!=null) {
                var shortage=shortages.selectById(plan.getShortageOrderId());
                order.setShortageOrderId(plan.getShortageOrderId());order.setReqId(shortage.getReqId());
            }
            orders.updateById(order);
        }
        if(plan.getShortageOrderId()!=null) {
            var shortage=shortages.selectById(plan.getShortageOrderId());
            shortage.setPoId(generated.get(0).getId());shortage.setStatus(ShortageStatusEnum.PURCHASING);
            shortages.updateById(shortage);
        }
        for(var row:rows)if(row.getSuggestionId()!=null) {
            var changed=new SmartReplenishSuggestion();changed.setStatus("GENERATED");changed.setUpdatedAt(LocalDateTime.now());
            if(suggestions.update(changed,new LambdaQueryWrapper<SmartReplenishSuggestion>()
                    .eq(SmartReplenishSuggestion::getId,row.getSuggestionId())
                    .eq(SmartReplenishSuggestion::getStatus,"PENDING"))!=1)
                throw new BizException(40900,"补货来源转换冲突");
        }
        plan.setStatus("ORDERED");plan.setUpdatedAt(LocalDateTime.now());plans.updateById(plan);
        audit(plan,"APPROVED","批准计划转采购订单");return view(plan);
    }

    private List<PurchasePlanItem> resolveRows(CreatePurchasePlanRequest request) {
        var input=new ArrayList<RowInput>();
        if("MANUAL".equals(request.getSourceType())) {
            if(request.getItems()==null || request.getItems().isEmpty())throw new BizException(40001,"采购计划明细不能为空");
            for(var item:request.getItems()) {
                if(item==null)throw new BizException(40001,"计划行不能为空");
                input.add(new RowInput(item.getReagentId(),item.getSupplierId()!=null?item.getSupplierId():request.getSupplierId(),item.getQuantity(),null));
            }
        } else if("SHORTAGE".equals(request.getSourceType())) {
            var shortage=shortages.selectOne(new LambdaQueryWrapper<ShortageOrder>()
                    .eq(ShortageOrder::getId,request.getShortageId()).last("FOR UPDATE"));
            if(shortage==null)throw new BizException(40400,"缺货来源不存在或不可访问");
            if(shortage.getStatus()!=ShortageStatusEnum.PENDING_PROCUREMENT || shortage.getPoId()!=null)
                throw new BizException(40900,"缺货来源已处理");
            Integer supplier=request.getSupplierId()!=null?request.getSupplierId()
                    : shortage.getSuggestedSupplierId()==null?null:Math.toIntExact(shortage.getSuggestedSupplierId());
            for(var item:shortageItems.selectList(new LambdaQueryWrapper<ShortageOrderItem>()
                    .eq(ShortageOrderItem::getShortageOrderId,shortage.getId()).orderByAsc(ShortageOrderItem::getId)))
                input.add(new RowInput(item.getReagentId(),supplier,item.getQtyShortage(),null));
        } else {
            if(request.getSuggestions()==null || request.getSuggestions().isEmpty())throw new BizException(40001,"请选择补货建议");
            var byId=new TreeMap<Long,CreatePurchasePlanRequest.Suggestion>();
            for(var item:request.getSuggestions()) {
                if(item==null || item.getId()==null || byId.putIfAbsent(item.getId(),item)!=null)
                    throw new BizException(40001,"补货建议编号缺失或重复");
            }
            for(var item:byId.values()) {
                var suggestion=suggestions.selectOne(new LambdaQueryWrapper<SmartReplenishSuggestion>()
                        .eq(SmartReplenishSuggestion::getId,item.getId()).last("FOR UPDATE"));
                if(suggestion==null)throw new BizException(40400,"补货建议不存在或不可访问");
                if(!"PENDING".equals(suggestion.getStatus()) || !Objects.equals(suggestion.getReagentId(),item.getReagentId()))
                    throw new BizException(40900,"补货来源已变化或试剂不匹配");
                input.add(new RowInput(item.getReagentId(),item.getSupplierId(),item.getSuggestedQty(),item.getId()));
            }
        }
        if(input.isEmpty() || input.size()>500)throw new BizException(40001,"计划明细须为1至500行");
        // Stable catalog lock order avoids two multi-product plans taking reversed catalog locks.
        Map<Long,ReagentCatalog> catalogRows=new TreeMap<>();
        for(var row:input) {
            if(row.reagentId()==null || row.quantity()==null || row.quantity().signum()<=0
                    || row.quantity().stripTrailingZeros().scale()>2 || row.quantity().compareTo(new BigDecimal("100000000"))>=0)
                throw new BizException(40001,"计划试剂及正数两位数量必填");
            catalogRows.put(row.reagentId(),null);
        }
        catalogRows.replaceAll((id,ignored)->approvedCatalog(id));
        var result=new ArrayList<PurchasePlanItem>();
        for(var row:input) {
            var catalog=catalogRows.get(row.reagentId());
            int supplier=resolveSupplier(row.supplierId(),catalog.getDistributor());
            var item=new PurchasePlanItem();item.setReagentId(row.reagentId());item.setSupplierId(supplier);
            item.setSuggestionId(row.suggestionId());item.setReagentName(catalog.getGenericName());
            item.setMaterialType(catalog.getMaterialType().name());
            item.setAcceptanceMethod(catalog.getMaterialType().entersUdiStateMachine()?"SCAN":"MANUAL");
            item.setQuantity(row.quantity());item.setUnitPrice(catalog.getPricePerUnit());
            item.setAmount(catalog.getPricePerUnit().multiply(row.quantity()).setScale(2,RoundingMode.HALF_UP));
            if(item.getAmount().compareTo(new BigDecimal("10000000000"))>=0)throw new BizException(40001,"计划金额超出支持范围");
            result.add(item);
        }
        return result;
    }

    private List<PurchasePlanItem> validateCurrentRows(PurchasePlan plan) {
        var rows=items.selectList(new LambdaQueryWrapper<PurchasePlanItem>().eq(PurchasePlanItem::getPlanId,plan.getId())
                .orderByAsc(PurchasePlanItem::getLineNo));
        if(rows.isEmpty())throw new BizException(40001,"计划没有采购明细");
        var current=new TreeMap<Long,ReagentCatalog>();rows.forEach(row->current.put(row.getReagentId(),null));
        current.replaceAll((id,ignored)->approvedCatalog(id));
        for(var row:rows) {
            var catalog=current.get(row.getReagentId());
            if(catalog.getPricePerUnit().compareTo(row.getUnitPrice())!=0
                    || !catalog.getMaterialType().name().equals(row.getMaterialType()))
                throw new BizException(40900,"目录价格或类型已变化，请重新编辑计划并提交审批");
            suppliers.requireProcurementEligible(row.getSupplierId().longValue());
        }
        return rows;
    }

    private ReagentCatalog approvedCatalog(Long id) {
        var catalog=catalogs.lockForBusiness(id);
        if(catalog==null || catalog.getApprovalStatus()!=ApprovalStatusEnum.APPROVED || catalog.getMaterialType()==null
                || catalog.getPricePerUnit()==null || catalog.getPricePerUnit().signum()<0)
            throw new BizException(40001,"采购目录须已批准并维护类型与非负单价");
        return catalog;
    }

    private int resolveSupplier(Integer supplierId,String distributor) {
        Supplier supplier=null;
        if(supplierId!=null)supplier=suppliers.findById(supplierId.longValue());
        else if(distributor!=null && !distributor.isBlank()) {
            String name=distributor.strip();
            var matches=suppliers.findAll().stream().filter(s->Boolean.TRUE.equals(s.getIsActive()))
                    .filter(s->name.equals(Objects.toString(s.getName(),"").strip())
                            || name.equals(Objects.toString(s.getShortName(),"").strip())).toList();
            if(matches.size()==1)supplier=matches.get(0);
        }
        if(supplier==null || !Boolean.TRUE.equals(supplier.getIsActive()))
            throw new BizException(40001,"未匹配唯一启用供应商");
        suppliers.requireProcurementEligible(supplier.getId());
        try{return Math.toIntExact(supplier.getId());}
        catch(ArithmeticException invalid){throw new BizException(40001,"供应商编号超出采购接口支持范围");}
    }

    private void validateHeader(CreatePurchasePlanRequest request) {
        if(request==null || !Set.of("MANUAL","SHORTAGE","SMART_REPLENISH").contains(Objects.toString(request.getSourceType(),"")))
            throw new BizException(40001,"采购计划来源无效");
        LocalDate orderDate=request.getOrderDate()==null?LocalDate.now():request.getOrderDate();
        if(orderDate.isAfter(LocalDate.now()) || (request.getExpectedDeliveryDate()!=null && request.getExpectedDeliveryDate().isBefore(orderDate)))
            throw new BizException(40001,"下单日期不能为未来日期，预计到货不能早于下单日期");
        if("SHORTAGE".equals(request.getSourceType()) && request.getShortageId()==null)
            throw new BizException(40001,"缺货来源编号必填");
        if(request.getRemark()!=null && request.getRemark().length()>1000)throw new BizException(40001,"计划备注过长");
    }

    private void header(PurchasePlan plan,CreatePurchasePlanRequest request) {
        plan.setOrderDate(request.getOrderDate()==null?LocalDate.now():request.getOrderDate());
        plan.setExpectedDeliveryDate(request.getExpectedDeliveryDate());
        plan.setSettlementMode(SettlementModeEnum.normalizeNewOrder(request.getSettlementMode()));
        plan.setRemark(request.getRemark());
    }

    private void saveRows(Long id,List<PurchasePlanItem> rows) {
        int number=1;
        for(var row:rows) {
            row.setPlanId(id);row.setLineNo(number++);row.setCreatedAt(LocalDateTime.now());row.setUpdatedAt(row.getCreatedAt());
            try{items.insert(row);}catch(DuplicateKeyException duplicate){throw new BizException(40900,"补货建议已有采购计划，请查询原计划");}
        }
    }

    private PurchasePlan lock(Long id) {
        if(id==null)throw new BizException(40001,"采购计划编号必填");
        var plan=plans.selectOne(new LambdaQueryWrapper<PurchasePlan>().eq(PurchasePlan::getId,id).last("FOR UPDATE"));
        if(plan==null)throw new BizException(40400,"采购计划不存在或不可访问");
        return plan;
    }

    private long role(String... allowed) {
        var scope=InstitutionContext.require();
        if(!Set.of(allowed).contains(scope.role()))throw new BizException(40300,"无采购计划操作权限");
        return InstitutionContext.requireUserActor();
    }

    private void requirePending(PurchasePlan plan,long actor) {
        if(!"PENDING_REVIEW".equals(plan.getStatus()))throw new BizException(40900,"仅待审批计划可以审核");
        if(Objects.equals(actor,plan.getCreatedBy()))throw new BizException(40300,"不能审核本人提交的采购计划");
    }

    private void review(PurchasePlan plan,long actor,String comment) {
        if(comment!=null && comment.length()>1000)throw new BizException(40001,"审批意见过长");
        plan.setReviewedBy(actor);plan.setReviewedAt(LocalDateTime.now());plan.setReviewComment(comment);
        plan.setUpdatedAt(plan.getReviewedAt());
    }

    private void audit(PurchasePlan plan,String from,String reason) {
        var event=new PurchasePlanEvent();event.setPlanId(plan.getId());event.setFromStatus(from);event.setToStatus(plan.getStatus());
        event.setOperatorId(InstitutionContext.requireUserActor());event.setReason(reason);event.setCreatedAt(LocalDateTime.now());
        try{event.setSnapshot(json.writeValueAsString(view(plan)));}
        catch(Exception invalid){throw new BizException(50000,"采购计划审计快照保存失败");}
        events.insert(event);
    }

    private PurchasePlanVO view(PurchasePlan plan) {
        var result=new PurchasePlanVO();BeanUtils.copyProperties(plan,result);result.setShortageId(plan.getShortageOrderId());
        var rows=items.selectList(new LambdaQueryWrapper<PurchasePlanItem>().eq(PurchasePlanItem::getPlanId,plan.getId())
                .orderByAsc(PurchasePlanItem::getLineNo));
        result.setItems(rows.stream().map(row->{var vo=new PurchasePlanVO.Item();BeanUtils.copyProperties(row,vo);return vo;}).toList());
        result.setTotalAmount(rows.stream().map(PurchasePlanItem::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add));
        result.setOrders(orders.selectList(new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getPlanId,plan.getId())
                .orderByAsc(PurchaseOrder::getId)).stream().map(order->{
                    var vo=new PurchasePlanVO.Order();BeanUtils.copyProperties(order,vo);
                    vo.setStatus(order.getStatus()==null?null:order.getStatus().name());return vo;
                }).collect(Collectors.toList()));
        return result;
    }

    private record RowInput(Long reagentId,Integer supplierId,BigDecimal quantity,Long suggestionId) {}
}
