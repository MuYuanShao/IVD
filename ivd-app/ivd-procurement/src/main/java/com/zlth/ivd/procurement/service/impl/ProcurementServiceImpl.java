package com.zlth.ivd.procurement.service.impl;

import com.zlth.ivd.common.utils.IdGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.enums.ConsumptionRecordTypeEnum;
import com.zlth.ivd.common.enums.MaterialTypeEnum;
import com.zlth.ivd.common.enums.PurchaseOrderStatusEnum;
import com.zlth.ivd.common.enums.RequisitionStatusEnum;
import com.zlth.ivd.common.enums.ShortageStatusEnum;
import com.zlth.ivd.common.enums.UdiStatusEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.catalog.service.CatalogReferenceService;
import com.zlth.ivd.consumption.entity.ConsumptionRecord;
import com.zlth.ivd.consumption.service.ProcurementConsumptionService;
import com.zlth.ivd.license.entity.Supplier;
import com.zlth.ivd.license.service.SupplierReferenceService;
import com.zlth.ivd.inventory.entity.ReagentBatch;
import com.zlth.ivd.inventory.entity.Warehouse;
import com.zlth.ivd.inventory.service.ProcurementInventoryService;
import com.zlth.ivd.procurement.dto.CreateManualOrderRequest;
import com.zlth.ivd.procurement.dto.DirectReceiveRequest;
import com.zlth.ivd.procurement.dto.PickAndShipRequest;
import com.zlth.ivd.procurement.dto.ReceiveItemRequest;
import com.zlth.ivd.procurement.entity.*;
import com.zlth.ivd.procurement.vo.PurchaseOrderVO;
import com.zlth.ivd.procurement.vo.PurchaseRequisitionVO;
import com.zlth.ivd.procurement.vo.ShortageOrderVO;
import com.zlth.ivd.procurement.mapper.*;
import com.zlth.ivd.procurement.service.ProcurementService;
import com.zlth.ivd.system.service.ResearchProjectService;
import com.zlth.ivd.system.entity.SysDept;
import com.zlth.ivd.system.entity.SysUser;
import com.zlth.ivd.system.service.ProcurementOrgService;
import com.zlth.ivd.udi.service.UniqueCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class ProcurementServiceImpl implements ProcurementService {

    private static void requirePositive(BigDecimal quantity, String field) {
        if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale()>2)
            throw new BizException(40001, field + "必须大于零");
    }

    private static final Logger log = LoggerFactory.getLogger(ProcurementServiceImpl.class);

    private final PurchaseRequisitionMapper reqMapper;
    private final ResearchProjectService researchProjectService;
    private final PurchaseRequisitionItemMapper reqItemMapper;
    private final ShortageOrderMapper shortageMapper;
    private final ShortageOrderItemMapper shortageItemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final ProcurementInventoryService inventoryService;
    private final CatalogReferenceService catalogService;
    private final ProcurementConsumptionService consumptionService;
    private final SmartReplenishSuggestionMapper suggestionMapper;
    private final ProcurementOrgService orgService;
    private final UniqueCodeService uniqueCodeService;
    private final SupplierReferenceService supplierService;
    @org.springframework.beans.factory.annotation.Autowired
    private SmartReplenishRunMapper replenishRuns;
    @org.springframework.beans.factory.annotation.Autowired
    private PurchasePlanMapper purchasePlanMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private com.zlth.ivd.common.audit.OperationAuditLogWriter domainAudits;

    public ProcurementServiceImpl(ResearchProjectService researchProjectService, PurchaseRequisitionMapper reqMapper,
                                  PurchaseRequisitionItemMapper reqItemMapper,
                                  ShortageOrderMapper shortageMapper,
                                  ShortageOrderItemMapper shortageItemMapper,
                                  PurchaseOrderMapper orderMapper,
                                  PurchaseOrderItemMapper orderItemMapper,
                                  ProcurementInventoryService inventoryService,
                                  CatalogReferenceService catalogService,
                                  ProcurementConsumptionService consumptionService,
                                  SmartReplenishSuggestionMapper suggestionMapper,
                                  ProcurementOrgService orgService,
                                  UniqueCodeService uniqueCodeService,
                                  SupplierReferenceService supplierService) {
        this.reqMapper = reqMapper;
        this.researchProjectService = researchProjectService;
        this.reqItemMapper = reqItemMapper;
        this.shortageMapper = shortageMapper;
        this.shortageItemMapper = shortageItemMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.inventoryService = inventoryService;
        this.catalogService = catalogService;
        this.consumptionService = consumptionService;
        this.suggestionMapper = suggestionMapper;
        this.orgService = orgService;
        this.uniqueCodeService = uniqueCodeService;
        this.supplierService = supplierService;
    }

    // ── 申领单 ──
    @Override
    public PageVO<PurchaseRequisitionVO> listRequisitions(int page, int pageSize, String status, String keyword) {
        LambdaQueryWrapper<PurchaseRequisition> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            String[] statuses = status.split(",");
            if (statuses.length == 1) {
                qw.eq(PurchaseRequisition::getStatus, RequisitionStatusEnum.valueOf(statuses[0].trim()));
            } else {
                List<RequisitionStatusEnum> statusList = new ArrayList<>();
                for (String s : statuses) {
                    statusList.add(RequisitionStatusEnum.valueOf(s.trim()));
                }
                qw.in(PurchaseRequisition::getStatus, statusList);
            }
        }
        if (keyword != null) qw.like(PurchaseRequisition::getReqNo, keyword);
        qw.orderByDesc(PurchaseRequisition::getCreatedAt);
        Page<PurchaseRequisition> p = reqMapper.selectPage(new Page<>(page, pageSize), qw);

        Set<Long> reqIds = p.getRecords().stream().map(PurchaseRequisition::getId).collect(Collectors.toSet());
        List<PurchaseRequisitionItem> allItems = reqIds.isEmpty() ? Collections.emptyList()
                : reqItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequisitionItem>().in(PurchaseRequisitionItem::getReqId, reqIds));
        Map<Long, List<PurchaseRequisitionItem>> itemsMap = allItems.stream()
                .collect(Collectors.groupingBy(PurchaseRequisitionItem::getReqId));

        // 批量查询科室和用户名称
        Set<Long> deptIds = p.getRecords().stream().map(PurchaseRequisition::getFromDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> userIds = p.getRecords().stream().map(PurchaseRequisition::getApplicantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNameMap = batchGetDeptNames(deptIds);
        Map<Long, String> userNameMap = batchGetUserNames(userIds);

        List<PurchaseRequisitionVO> records = new ArrayList<>();
        for (PurchaseRequisition req : p.getRecords()) {
            PurchaseRequisitionVO vo = new PurchaseRequisitionVO();
            vo.setId(req.getId()); vo.setReqNo(req.getReqNo());
            vo.setResearchProjectId(req.getResearchProjectId()); vo.setResearchProjectName(req.getResearchProjectName());
            vo.setFromDeptId(req.getFromDeptId());
            vo.setDeptName(deptNameMap.getOrDefault(req.getFromDeptId(), "科室#" + req.getFromDeptId()));
            vo.setToWarehouseId(req.getToWarehouseId() != null ? req.getToWarehouseId().intValue() : null);
            vo.setApplicantId(req.getApplicantId() != null ? req.getApplicantId().intValue() : null);
            vo.setApplicantName(userNameMap.getOrDefault(req.getApplicantId(), "用户#" + (req.getApplicantId() != null ? req.getApplicantId() : "-")));
            vo.setUrgency(req.getUrgency()); vo.setStatus(req.getStatus());
            vo.setReviewerId(req.getReviewerId() != null ? req.getReviewerId().intValue() : null); vo.setReviewTime(req.getReviewTime());
            vo.setReviewComment(req.getReviewComment()); vo.setRemark(req.getRemark());
            vo.setCreatedAt(req.getCreatedAt()); vo.setUpdatedAt(req.getUpdatedAt());

            List<PurchaseRequisitionItem> items = itemsMap.getOrDefault(req.getId(), Collections.emptyList());
BigDecimal totalQty = items.stream().map(i -> i.getQtyRequested()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalAmount = items.stream().map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal shippedQty = items.stream().map(i -> i.getQtyFulfilled()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal shortageQty = items.stream().map(i -> i.getQtyShortage()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            vo.setTotalQty(totalQty); vo.setTotalAmount(totalAmount);
            vo.setShippedQty(shippedQty); vo.setShortageQty(shortageQty);
            records.add(vo);
        }
        return new PageVO<>(p.getTotal(), page, pageSize, records);
    }

    @Override
    public PurchaseRequisition createRequisition(PurchaseRequisition req) {
        if(req==null || req.getFromDeptId()==null || req.getItems()==null || req.getItems().isEmpty())
            throw new BizException(40001,"申领科室和明细必填");
        authorizeDepartment(req.getFromDeptId());
        var department=orgService.findDept(req.getFromDeptId());
        if(department==null || !Integer.valueOf(1).equals(department.getStatus())) throw new BizException(40001,"申领科室不存在或已停用");
        for(var item:req.getItems()) { if(item==null || item.getReagentId()==null) throw new BizException(40001,"请选择申领试剂");requirePositive(item.getQtyRequested(),"申领数量"); }
        req.setResearchProjectName(researchProjectService.requireActiveName(req.getResearchProjectId()));
        String no = "REQ-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
                String.format("%03d", System.currentTimeMillis() % 1000);
        req.setReqNo(no);
        req.setStatus(RequisitionStatusEnum.DRAFT);
        // 强制使用 JWT 当前用户作为申请人，防止前端伪造 applicantId
        req.setApplicantId(SecurityUtils.getCurrentUserId());
        // 根据申领科室解析目标科室库（toWarehouseId），前端未传入时自动匹配
        if (req.getToWarehouseId() == null && req.getFromDeptId() != null) {
            Warehouse deptWh = inventoryService.findActiveDeptWarehouse(req.getFromDeptId());
            if (deptWh != null) {
                req.setToWarehouseId(deptWh.getId());
                log.info("自动匹配目标库房: deptId={} → warehouseId={} ({})",
                        req.getFromDeptId(), deptWh.getId(), deptWh.getName());
            }
        }
        inventoryService.requireDepartmentWarehouse(req.getToWarehouseId(),req.getFromDeptId());
        reqMapper.insert(req);
        log.info("申领单创建: reqNo={}, applicantId={}, toWarehouseId={}", no, req.getApplicantId(), req.getToWarehouseId());

        // 持久化申领明细
        List<PurchaseRequisitionItem> items = req.getItems();
        if (items != null && !items.isEmpty()) {
            // 批量查询试剂目录，填充规格/单价/厂家等信息
            Set<Long> reagentIds = items.stream()
                    .map(PurchaseRequisitionItem::getReagentId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
            if (!reagentIds.isEmpty()) {
                List<ReagentCatalog> catalogs = catalogService.findByIds(
                        reagentIds.stream().map(Long::valueOf).collect(Collectors.toList()));
                for (ReagentCatalog c : catalogs) catalogMap.put(c.getId(), c);
            }

            int lineNo = 1;
            BigDecimal totalQty = BigDecimal.ZERO;
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (PurchaseRequisitionItem item : items) {
                item.setReqId(req.getId());
                item.setLineNo(lineNo++);
                item.setQtyShortage(BigDecimal.ZERO);
                item.setQtyFulfilled(BigDecimal.ZERO);
                if (item.getStockOnHand() == null) item.setStockOnHand(BigDecimal.ZERO);

                // 从目录填充试剂信息
                if (item.getReagentId() != null) {
                    ReagentCatalog cat = catalogMap.get(item.getReagentId().longValue());
                    if(cat==null || cat.getApprovalStatus()!=com.zlth.ivd.common.enums.ApprovalStatusEnum.APPROVED)
                        throw new BizException(40001,"申领试剂目录不存在、未批准或不可访问");
                    if (cat != null) {
                        if (item.getSpecification() == null) item.setSpecification(cat.getSpecification());
                        item.setUnitPrice(cat.getPricePerUnit());
                        item.setReagentName(cat.getGenericName());
                        item.setGenericName(cat.getGenericName());
                        item.setManufacturer(cat.getManufacturer());
                        item.setUnit(cat.getUnit());
                    }
                }
                item.setAmount(null);
                if (item.getQtyRequested() != null && item.getUnitPrice() != null) {
                    item.setAmount(item.getQtyRequested().multiply(item.getUnitPrice()));
                }
                reqItemMapper.insert(item);

                if (item.getQtyRequested() != null) totalQty = totalQty.add(item.getQtyRequested());
                if (item.getAmount() != null) totalAmount = totalAmount.add(item.getAmount());
            }

            log.info("申领明细已保存: reqId={}, itemCount={}, totalQty={}, totalAmount={}",
                    req.getId(), items.size(), totalQty, totalAmount);
        }
        return req;
    }

    @Override
    public List<PurchaseRequisitionItem> getRequisitionItems(Long reqId) {
        // 明细本身没有科室字段，必须先经过父单的机构/科室可见性校验。
        PurchaseRequisition requisition = reqMapper.selectById(reqId);
        if (requisition == null) throw new BizException(40400, "申领单不存在或无权访问");
        authorizeDepartment(requisition.getFromDeptId());
        List<PurchaseRequisitionItem> items = reqItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseRequisitionItem>()
                        .eq(PurchaseRequisitionItem::getReqId, reqId)
                        .orderByAsc(PurchaseRequisitionItem::getLineNo));
        if (items == null) items = Collections.emptyList();

        // 填充试剂目录信息
        if (!items.isEmpty()) {
            Set<Long> reagentIds = items.stream()
                    .map(PurchaseRequisitionItem::getReagentId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!reagentIds.isEmpty()) {
                Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
        List<ReagentCatalog> catalogs = catalogService.findByIds(
                        reagentIds.stream().map(Long::valueOf).collect(Collectors.toList()));
                for (ReagentCatalog c : catalogs) catalogMap.put(c.getId(), c);
                for (PurchaseRequisitionItem item : items) {
                    if (item.getReagentId() != null) {
                        ReagentCatalog cat = catalogMap.get(item.getReagentId().longValue());
                        if (cat != null) {
                            item.setReagentName(cat.getGenericName());
                            item.setGenericName(cat.getGenericName());
                            item.setManufacturer(cat.getManufacturer());
                            item.setUnit(cat.getUnit());
                        }
                    }
                }
            }
        }
        return items;
    }

    @Override
    public PurchaseRequisitionVO getRequisitionDetail(Long id) {
        PurchaseRequisition req = reqMapper.selectById(id);
        if (req == null) throw new BizException(40400, "申领单不存在");

        List<PurchaseRequisitionItem> items = reqItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseRequisitionItem>().eq(PurchaseRequisitionItem::getReqId, id));
        if (items == null) items = Collections.emptyList();

        // 填充试剂目录信息（名称、通用名、厂家、单位等）
        if (!items.isEmpty()) {
            Set<Long> reagentIds = items.stream().map(PurchaseRequisitionItem::getReagentId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!reagentIds.isEmpty()) {
                Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
                List<ReagentCatalog> catalogs = catalogService.findByIds(
                        reagentIds.stream().map(Long::valueOf).collect(Collectors.toList()));
                for (ReagentCatalog c : catalogs) catalogMap.put(c.getId(), c);
                for (PurchaseRequisitionItem item : items) {
                    if (item.getReagentId() != null) {
                        ReagentCatalog cat = catalogMap.get(item.getReagentId().longValue());
                        if (cat != null) {
                            item.setReagentName(cat.getGenericName());
                            item.setGenericName(cat.getGenericName());
                            item.setManufacturer(cat.getManufacturer());
                            item.setUnit(cat.getUnit());
                        }
                    }
                }
            }
        }

        // 查询科室和用户名称
        SysDept dept = req.getFromDeptId() != null ? orgService.findDept(req.getFromDeptId()) : null;
        String deptName = dept != null ? dept.getDeptName()
                : req.getFromDeptId() != null ? "科室#" + req.getFromDeptId() : "-";
        SysUser applicant = req.getApplicantId() != null ? orgService.findUser(req.getApplicantId()) : null;
        String applicantName = applicant != null ? applicant.getRealName()
                : req.getApplicantId() != null ? "用户#" + req.getApplicantId() : "-";

        PurchaseRequisitionVO vo = new PurchaseRequisitionVO();
        vo.setId(req.getId()); vo.setReqNo(req.getReqNo());
        vo.setResearchProjectId(req.getResearchProjectId()); vo.setResearchProjectName(req.getResearchProjectName());
        vo.setFromDeptId(req.getFromDeptId());
        vo.setDeptName(deptName);
        vo.setToWarehouseId(req.getToWarehouseId() != null ? req.getToWarehouseId().intValue() : null);
        vo.setApplicantId(req.getApplicantId() != null ? req.getApplicantId().intValue() : null);
        vo.setApplicantName(applicantName);
        vo.setUrgency(req.getUrgency()); vo.setStatus(req.getStatus());
        vo.setReviewerId(req.getReviewerId() != null ? req.getReviewerId().intValue() : null); vo.setReviewTime(req.getReviewTime());
        vo.setReviewComment(req.getReviewComment()); vo.setRemark(req.getRemark());
        vo.setCreatedAt(req.getCreatedAt()); vo.setUpdatedAt(req.getUpdatedAt());

        BigDecimal totalQty = items.stream().map(i -> i.getQtyRequested()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = items.stream().map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shippedQty = items.stream().map(i -> i.getQtyFulfilled()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shortageQty = items.stream().map(i -> i.getQtyShortage()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setTotalQty(totalQty); vo.setTotalAmount(totalAmount);
        vo.setShippedQty(shippedQty); vo.setShortageQty(shortageQty);
        vo.setItems(items);

        return vo;
    }

    @Override
    public PurchaseRequisitionVO reviewRequisition(Long id, String action, String comment) {
        PurchaseRequisition req = lockRequisitionState(id);
        if (req.getStatus() != RequisitionStatusEnum.PENDING_REVIEW) {
            throw new BizException(40000, "当前状态不允许审核，仅待审核状态的申领单可审核");
        }

        Long reviewerId = SecurityUtils.getCurrentUserId();
        req.setReviewerId(reviewerId);
        req.setReviewTime(LocalDateTime.now());
        req.setReviewComment(comment);

        if ("APPROVE".equalsIgnoreCase(action)) {
            req.setStatus(RequisitionStatusEnum.REVIEWED);
            log.info("申领单审核通过: reqNo={}, reviewerId={}", req.getReqNo(), reviewerId);
        } else if ("REJECT".equalsIgnoreCase(action)) {
            req.setStatus(RequisitionStatusEnum.DRAFT);
            log.info("申领单审核驳回: reqNo={}, reviewerId={}", req.getReqNo(), reviewerId);
        } else {
            throw new BizException(40000, "审核动作无效，仅支持 APPROVE / REJECT");
        }

        reqMapper.updateById(req);

        // 返回最新详情
        return getRequisitionDetail(id);
    }

    @Override
    public PurchaseRequisitionVO submitRequisition(Long id) {
        PurchaseRequisition req = lockRequisitionState(id);
        authorizeDepartment(req.getFromDeptId());
        if (req.getStatus() != RequisitionStatusEnum.DRAFT) {
            throw new BizException(40000, "仅草稿状态的申领单可提交审批");
        }
        req.setStatus(RequisitionStatusEnum.PENDING_REVIEW);
        reqMapper.updateById(req);
        log.info("申领单提交审批: reqNo={}, status: DRAFT → PENDING_REVIEW", req.getReqNo());
        return getRequisitionDetail(id);
    }

    @Override
    public PurchaseRequisitionVO cancelRequisition(Long id, String reason) {
        PurchaseRequisition req = lockRequisitionState(id);
        if (req.getStatus() != RequisitionStatusEnum.DRAFT) {
            throw new BizException(40000, "仅草稿状态的申领单可作废");
        }
        req.setStatus(RequisitionStatusEnum.CANCELLED);
        req.setReviewComment(reason != null ? reason : "用户主动作废");
        reqMapper.updateById(req);
        log.info("申领单已作废: reqNo={}, reason={}", req.getReqNo(), reason);
        return getRequisitionDetail(id);
    }

    private PurchaseRequisition lockRequisitionState(Long id) {
        // 状态必须来自加锁读，不能使用事务中可能已经建立的普通读快照。
        PurchaseRequisition req = reqMapper.selectOne(new LambdaQueryWrapper<PurchaseRequisition>()
                .eq(PurchaseRequisition::getId, id).last("FOR UPDATE"));
        if (req == null) throw new BizException(40400, "申领单不存在");
        return req;
    }

    // ── 拣货发货（核心闭环）──
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseRequisitionVO pickAndShip(Long requisitionId, PickAndShipRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty())
            throw new BizException(40001, "请选择拣货明细");
        for (var pick : request.getItems()) {
            if (pick == null || pick.getReqItemId() == null || pick.getSourceBatchId() == null)
                throw new BizException(40001, "拣货明细及源批次不能为空");
            requirePositive(pick.getPickQty(), "拣货数量");
        }
        PurchaseRequisition req = lockRequisitionState(requisitionId);
        if (req.getStatus() != RequisitionStatusEnum.REVIEWED
                && req.getStatus() != RequisitionStatusEnum.PICKING
                && req.getStatus() != RequisitionStatusEnum.PARTIAL_FULFILLED
                && req.getStatus() != RequisitionStatusEnum.SHIPPED) {
            throw new BizException(40000, "当前状态不允许拣货发货，仅已审核/拣选中/部分发货/待签收状态可操作");
        }

        Long targetWarehouseId = request.getTargetWarehouseId();
        if (targetWarehouseId == null) {
            targetWarehouseId = req.getToWarehouseId();
        }
        if (targetWarehouseId == null) throw new BizException(40001, "申领单缺少目标仓库");
        inventoryService.requireDepartmentWarehouse(targetWarehouseId,req.getFromDeptId());

        // 首次拣货：REVIEWED → PICKING
        if (req.getStatus() == RequisitionStatusEnum.REVIEWED) {
            req.setStatus(RequisitionStatusEnum.PICKING);
            req.setUpdatedAt(LocalDateTime.now());
            reqMapper.updateById(req);
        }

        List<PurchaseRequisitionItem> allItems = reqItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseRequisitionItem>().eq(PurchaseRequisitionItem::getReqId, requisitionId));
        Map<Long, PurchaseRequisitionItem> itemMap = allItems.stream()
                .collect(Collectors.toMap(PurchaseRequisitionItem::getId, i -> i));

        // Use the same source-lock ordering for picking and receipt across different requisitions.
        for (PickAndShipRequest.PickItem pickItem : request.getItems().stream()
                .sorted(Comparator.comparing(PickAndShipRequest.PickItem::getSourceBatchId)).toList()) {
            PurchaseRequisitionItem reqItem = itemMap.get(pickItem.getReqItemId());
            if (reqItem == null) {
                throw new BizException(40001, "拣货明细不属于当前申领单");
            }

            BigDecimal pickQty = pickItem.getPickQty() != null ? pickItem.getPickQty() : BigDecimal.ONE;
            BigDecimal requested = reqItem.getQtyRequested() != null ? reqItem.getQtyRequested() : BigDecimal.ZERO;
            BigDecimal alreadyFulfilled = reqItem.getQtyFulfilled() != null ? reqItem.getQtyFulfilled() : BigDecimal.ZERO;
            BigDecimal remaining = requested.subtract(alreadyFulfilled);
            if (pickQty.compareTo(remaining) > 0) {
                throw new BizException(40000, "拣货数量超过申领未满足量: reqItemId=" + pickItem.getReqItemId());
            }

            // 拣配仅占用可用量，实物和唯一码仍留在中心库。
            ReagentBatch sourceBatch = reserveSourceBatch(pickItem.getSourceBatchId(), reqItem.getReagentId(), pickQty);
            writeTransferRecord(sourceBatch, pickQty, targetWarehouseId, requisitionId,reqItem.getId());

            // 4. 更新申领明细 fulfillment
            reqItem.setQtyFulfilled(alreadyFulfilled.add(pickQty));
            reqItemMapper.updateById(reqItem);
        }

        // 5. 判定申领单最终状态
        List<PurchaseRequisitionItem> updatedItems = reqItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseRequisitionItem>().eq(PurchaseRequisitionItem::getReqId, requisitionId));
        boolean allFulfilled = updatedItems.stream().allMatch(i -> {
            BigDecimal fulfilled = i.getQtyFulfilled() != null ? i.getQtyFulfilled() : BigDecimal.ZERO;
            BigDecimal r = i.getQtyRequested() != null ? i.getQtyRequested() : BigDecimal.ZERO;
            return fulfilled.compareTo(r) >= 0;
        });
        boolean anyFulfilled = updatedItems.stream().anyMatch(i -> {
            BigDecimal fulfilled = i.getQtyFulfilled() != null ? i.getQtyFulfilled() : BigDecimal.ZERO;
            return fulfilled.compareTo(BigDecimal.ZERO) > 0;
        });

        if (allFulfilled) {
            req.setStatus(RequisitionStatusEnum.SHIPPED);
        } else if (anyFulfilled) {
            req.setStatus(RequisitionStatusEnum.PARTIAL_FULFILLED);
        }
        req.setUpdatedAt(LocalDateTime.now());
        reqMapper.updateById(req);

        log.info("拣货发货完成: reqId={}, pickedItems={}, status={}", requisitionId, request.getItems().size(), req.getStatus());
        return getRequisitionDetail(requisitionId);
    }

    @Override
    public PurchaseRequisitionVO receiveRequisition(Long id) {
        PurchaseRequisition req = lockRequisitionState(id);
        authorizeDepartment(req.getFromDeptId());
        if (req.getStatus() != RequisitionStatusEnum.SHIPPED
                && req.getStatus() != RequisitionStatusEnum.PARTIAL_FULFILLED) {
            throw new BizException(40000, "当前状态不允许签收，仅待签收/部分发货状态可签收");
        }

        // ★ 签收时将在途库存真正入二级库：查询拣货发货阶段的 TRANSFER 记录，逐笔 upsert 目标仓库批次
        // 优先使用 transfer record 中记录的目标仓库（pickAndShip 阶段指定的 targetWarehouseId），
        // 回退到申领单的 toWarehouseId
        Long defaultTargetWarehouseId = req.getToWarehouseId();
        if (defaultTargetWarehouseId == null) throw new BizException(40001, "申领单缺少签收仓库");
        if (defaultTargetWarehouseId != null) {
            List<ConsumptionRecord> transferRecords = consumptionService.findUnreceivedTransferRecords(id);
            if (transferRecords == null || transferRecords.isEmpty())
                throw new BizException(40900, "没有待签收的发货流水");
            if (transferRecords != null && !transferRecords.isEmpty()) {
                for (ConsumptionRecord tr : transferRecords.stream()
                        .sorted(Comparator.comparing(ConsumptionRecord::getBatchId, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(ConsumptionRecord::getId)).toList()) {
                    requirePositive(tr.getQuantity(), "发货流水数量");
                    // 每笔 transfer record 可能有不同的目标仓库，逐笔解析
                    Long trTargetWarehouseId = tr.getWarehouseId() != null
                            ? tr.getWarehouseId() : defaultTargetWarehouseId;
                    // 从源仓库查找对应的批次（即使库存已清零，批次记录仍存在）
                    inventoryService.requireDepartmentWarehouse(trTargetWarehouseId,req.getFromDeptId());
                    ReagentBatch sourceBatch = tr.getBatchId()!=null ? inventoryService.lockBatch(tr.getBatchId()) :
                            inventoryService.findUniqueLegacyBatch(tr.getReagentId(), tr.getBatchNo(), tr.getFromWarehouseId());
                    if (sourceBatch != null && tr.getBatchId() == null) sourceBatch = inventoryService.lockBatch(sourceBatch.getId());
                    if (sourceBatch == null) {
                        throw new BizException(40001, "签收入库源批次不存在，请核对发货流水");
                    }
                    if(!Objects.equals(sourceBatch.getReagentId(),tr.getReagentId())
                            || !Objects.equals(sourceBatch.getBatchNo(),tr.getBatchNo())
                            || !Objects.equals(sourceBatch.getWarehouseId(),tr.getFromWarehouseId()))
                        throw new BizException(40900,"签收来源批次与发货证据不一致");
                    inventoryService.requireWarehouse(sourceBatch.getWarehouseId());
                    if (sourceBatch.getUdiStatus() == null || !Set.of(UdiStatusEnum.IN_STOCK, UdiStatusEnum.IN_USE, UdiStatusEnum.TRANSFERRED).contains(sourceBatch.getUdiStatus())
                            || !com.zlth.ivd.common.utils.MaterialExpiryPolicy.usable(sourceBatch.getMaterialType(),sourceBatch.getExpiryDate(),LocalDate.now())
                            || (sourceBatch.getOpenExpiryDate()!=null && sourceBatch.getOpenExpiryDate().isBefore(LocalDate.now())))
                        throw new BizException(40001, "发货来源已受限或过期，请先核对在途物料");
                    var receiptCatalog = catalogService.findById(sourceBatch.getReagentId());
                    if (receiptCatalog == null) throw new BizException(40400, "试剂目录不存在");
                    boolean managed = Integer.valueOf(1).equals(receiptCatalog.getEnableUniqueCode());
                    if (managed && tr.getQuantity().stripTrailingZeros().scale() > 0)
                        throw new BizException(40001, "唯一码管控物料签收数量必须为整数");
                    if (Boolean.TRUE.equals(tr.getStockDeductOnReceipt()))
                        postReservedSourceBatch(tr, sourceBatch);
                    Long targetBatchId = upsertTargetBatch(sourceBatch, trTargetWarehouseId, tr.getQuantity());
                    consumptionService.confirmTransferReceipt(tr.getId(), targetBatchId);
                    log.info("签收入库: batchNo={}, qty={}, targetWarehouseId={}, targetBatchId={}",
                            tr.getBatchNo(), tr.getQuantity(), trTargetWarehouseId, targetBatchId);

                    // 唯一码全流程流转：签收时转移对应数量的唯一码到目标批次
                    if (targetBatchId != null && tr.getQuantity() != null
                            && tr.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                        int codeCount = tr.getQuantity().intValue();
                        int transferred = uniqueCodeService.transferUniqueCodes(
                                sourceBatch.getId(), targetBatchId, codeCount);
                        if (managed && transferred != codeCount)
                            throw new BizException(40900, "唯一码数量与签收数量不一致，操作已回滚");
                        log.info("唯一码已转移: sourceBatchId={}, targetBatchId={}, count={}",
                                sourceBatch.getId(), targetBatchId, codeCount);
                    }
                }
            }
        }

        List<PurchaseRequisitionItem> receiptItems = reqItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseRequisitionItem>().eq(PurchaseRequisitionItem::getReqId, id));
        boolean complete = !receiptItems.isEmpty() && receiptItems.stream().allMatch(item ->
                item.getQtyFulfilled() != null && item.getQtyRequested() != null
                        && item.getQtyFulfilled().compareTo(item.getQtyRequested()) >= 0);
        req.setStatus(complete ? RequisitionStatusEnum.FULFILLED : RequisitionStatusEnum.PARTIAL_FULFILLED);
        req.setReceivedAt(LocalDateTime.now());
        req.setUpdatedAt(LocalDateTime.now());
        reqMapper.updateById(req);
        log.info("科室签收完成: reqId={}, reqNo={}", id, req.getReqNo());
        return getRequisitionDetail(id);
    }

    private ReagentBatch reserveSourceBatch(Long batchId, Long reagentId, BigDecimal qty) {
        ReagentBatch sourceBatch = inventoryService.lockBatch(batchId);
        if (sourceBatch == null) throw new BizException(40400, "批次不存在");
        if (!Objects.equals(reagentId, sourceBatch.getReagentId()))
            throw new BizException(40001, "源批次试剂与申领明细不一致");
        var pickingCatalog = catalogService.findById(reagentId);
        if (pickingCatalog == null) throw new BizException(40400, "试剂目录不存在");
        if (Integer.valueOf(1).equals(pickingCatalog.getEnableUniqueCode()) && qty.stripTrailingZeros().scale() > 0)
            throw new BizException(40001, "唯一码管控物料必须按完整单位拣货");
        if (!Integer.valueOf(1).equals(inventoryService.requireWarehouse(sourceBatch.getWarehouseId()).getLevel()))
            throw new BizException(40001, "申领拣货必须来自本机构中心库");
        if (sourceBatch.getUdiStatus() != UdiStatusEnum.IN_STOCK
                || !com.zlth.ivd.common.utils.MaterialExpiryPolicy.usable(sourceBatch.getMaterialType(), sourceBatch.getExpiryDate(), LocalDate.now()))
            throw new BizException(40001, "源批次不可用或已过期");
        if (sourceBatch.getOpenExpiryDate() != null && sourceBatch.getOpenExpiryDate().isBefore(LocalDate.now()))
            throw new BizException(40001, "源批次已超过开瓶稳定期，不能拣货");
        if (sourceBatch.getAvailableStock().compareTo(qty) < 0)
            throw new BizException(40001, "中心库可用量不足（已扣除预占）");
        sourceBatch.setReservedStock(sourceBatch.getReservedStock().add(qty));
        if (!inventoryService.updateBatch(sourceBatch)) throw new BizException(40900, "库存预占发生并发修改");
        return sourceBatch;
    }

    private void postReservedSourceBatch(ConsumptionRecord transfer, ReagentBatch batch) {
        BigDecimal quantity = transfer.getQuantity();
        if (transfer.getBatchId()==null || batch.getUdiStatus()!=UdiStatusEnum.IN_STOCK
                || batch.getReservedStock().compareTo(quantity)<0 || batch.getAvailableStock().signum()<0)
            throw new BizException(40900, "发货流水的中心库预占不足或库存状态已变化，请核对后签收");
        batch.setReservedStock(batch.getReservedStock().subtract(quantity));
        batch.setStock(batch.getStock().subtract(quantity));
        if (batch.getStock().signum()==0) batch.setUdiStatus(UdiStatusEnum.TRANSFERRED);
        if (!inventoryService.updateBatch(batch)) throw new BizException(40900, "签收扣减库存发生并发修改");
    }

    /**
     * 签收入库：将源批次库存拷贝/合并到目标仓库，返回目标批次 ID（供唯一码转移）
     */
    private Long upsertTargetBatch(ReagentBatch sourceBatch, Long targetWarehouseId, BigDecimal qty) {
        return inventoryService.receiveIntoWarehouse(sourceBatch,targetWarehouseId,qty).getId();
    }

    /**
     * 写入 TRANSFER consumption_record 审计追踪
     */
    private void writeTransferRecord(ReagentBatch sourceBatch, BigDecimal qty, Long targetWarehouseId, Long requisitionId,Long requisitionItemId) {
        ConsumptionRecord record = new ConsumptionRecord();
        record.setId(IdGenerator.nextId());
        record.setRecordType(ConsumptionRecordTypeEnum.TRANSFER.getCode());
        record.setBatchNo(sourceBatch.getBatchNo());
        record.setBatchId(sourceBatch.getId());
        record.setPurchaseOrderItemId(sourceBatch.getOriginPurchaseOrderItemId());
        record.setSettlementMode(sourceBatch.getSettlementMode());
        record.setRequisitionItemId(requisitionItemId);
        record.setReagentId(sourceBatch.getReagentId());
        record.setMaterialType(sourceBatch.getMaterialType() != null ? sourceBatch.getMaterialType().name() : "REAGENT");
        record.setQuantity(qty);
        record.setUnit(sourceBatch.getUnit());
        record.setUnitPrice(sourceBatch.getUnitPrice());
        record.setAmount(sourceBatch.getUnitPrice() != null ? sourceBatch.getUnitPrice().multiply(qty) : null);
        record.setWarehouseId(targetWarehouseId);
        record.setFromWarehouseId(sourceBatch.getWarehouseId());
        record.setRequisitionId(requisitionId);
        record.setStockDeductOnReceipt(true);
        record.setOperatorId(SecurityUtils.getCurrentOperatorIdOrNull());
        record.setOccurredAt(LocalDateTime.now());
        record.setCreatedAt(LocalDateTime.now());
        consumptionService.appendRecord(record);
    }

    // ── 缺货单 ──
    @Override
    public PageVO<ShortageOrderVO> listShortages(int page, int pageSize, String status, String keyword) {
        LambdaQueryWrapper<ShortageOrder> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) qw.eq(ShortageOrder::getStatus, ShortageStatusEnum.valueOf(status));
        if (keyword != null && !keyword.isEmpty()) qw.and(w -> w.like(ShortageOrder::getShortageNo, keyword));
        qw.orderByDesc(ShortageOrder::getCreatedAt,ShortageOrder::getId);
        Page<ShortageOrder> p = shortageMapper.selectPage(new Page<>(page, pageSize), qw);

        Set<Long> reqIds = p.getRecords().stream().map(ShortageOrder::getReqId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> reqNoMap = new HashMap<>();
        if (!reqIds.isEmpty()) {
            List<PurchaseRequisition> reqs = reqMapper.selectBatchIds(reqIds);
            for (PurchaseRequisition r : reqs) reqNoMap.put(r.getId(), r.getReqNo());
        }

        Set<Long> shortageIds = p.getRecords().stream().map(ShortageOrder::getId).collect(Collectors.toSet());
        List<ShortageOrderItem> allItems = shortageIds.isEmpty() ? Collections.emptyList()
                : shortageItemMapper.selectList(new LambdaQueryWrapper<ShortageOrderItem>().in(ShortageOrderItem::getShortageOrderId, shortageIds));
        Map<Long, List<ShortageOrderItem>> itemsMap = allItems.stream()
                .collect(Collectors.groupingBy(ShortageOrderItem::getShortageOrderId));

        Set<Long> reagentIds = allItems.stream().map(ShortageOrderItem::getReagentId).collect(Collectors.toSet());
        Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
        if (!reagentIds.isEmpty()) {
            List<ReagentCatalog> catalogs = catalogService.findByIds(
                    reagentIds.stream().map(Long::valueOf).collect(Collectors.toList()));
            for (ReagentCatalog cat : catalogs) catalogMap.put(cat.getId(), cat);
        }

        // 批量查询科室名称
        Set<Long> deptIds = p.getRecords().stream().map(ShortageOrder::getFromDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNameMap = batchGetDeptNames(deptIds);

        // 批量查询供应商名称
        Set<Long> shortageSupplierIds = p.getRecords().stream().map(ShortageOrder::getSuggestedSupplierId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> shortageSupplierNameMap = new HashMap<>();
        if (!shortageSupplierIds.isEmpty()) {
        List<Supplier> suppliers = supplierService.findByIds(shortageSupplierIds);
            for (Supplier s : suppliers) shortageSupplierNameMap.put(s.getId(), s.getName());
        }

        List<ShortageOrderVO> records = new ArrayList<>();
        for (ShortageOrder so : p.getRecords()) {
            ShortageOrderVO vo = new ShortageOrderVO();
            vo.setId(so.getId()); vo.setShortageNo(so.getShortageNo());
            vo.setReqId(so.getReqId());
            vo.setSourceReqNo(reqNoMap.getOrDefault(so.getReqId(), "-"));
            vo.setFromDeptId(so.getFromDeptId());
            vo.setDeptName(deptNameMap.getOrDefault(so.getFromDeptId(), "科室#" + so.getFromDeptId()));
            vo.setStatus(so.getStatus()); vo.setUrgency(so.getUrgency());
            vo.setSuggestedSupplierId(so.getSuggestedSupplierId() != null ? so.getSuggestedSupplierId().intValue() : null);
            vo.setSuggestedSupplier(shortageSupplierNameMap.getOrDefault(so.getSuggestedSupplierId(),
                    so.getSuggestedSupplierId() != null ? "供应商#" + so.getSuggestedSupplierId() : "-"));
            vo.setPoId(so.getPoId());
            vo.setCreatedAt(so.getCreatedAt()); vo.setUpdatedAt(so.getUpdatedAt());

            List<ShortageOrderItem> items = itemsMap.getOrDefault(so.getId(), Collections.emptyList());
            if (!items.isEmpty()) {
                ShortageOrderItem first = items.get(0);
                ReagentCatalog cat = catalogMap.get(Long.valueOf(first.getReagentId()));
                vo.setReagentName(cat != null ? cat.getGenericName() : "试剂#" + first.getReagentId());
                vo.setSpec(cat != null ? cat.getSpecification() : "-");
                BigDecimal totalShortage = items.stream().map(ShortageOrderItem::getQtyShortage).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
                vo.setShortageQty(totalShortage);
                BigDecimal estAmount = items.stream().map(i -> {
                BigDecimal qty = i.getQtyShortage() != null ? i.getQtyShortage() : BigDecimal.ZERO;
                BigDecimal price = i.getEstUnitPrice() != null ? i.getEstUnitPrice() : BigDecimal.ZERO;
                return qty.multiply(price);
            }).reduce(BigDecimal.ZERO, BigDecimal::add);
                vo.setEstimatedAmount(estAmount);
            }
            records.add(vo);
        }
        return new PageVO<>(p.getTotal(), page, pageSize, records);
    }

    @Override
    public List<ShortageOrderItem> getShortageItems(Long shortageId) {
        List<ShortageOrderItem> items = shortageItemMapper.selectList(
                new LambdaQueryWrapper<ShortageOrderItem>()
                        .eq(ShortageOrderItem::getShortageOrderId, shortageId)
                        .orderByAsc(ShortageOrderItem::getLineNo));
        if (items == null) items = Collections.emptyList();

        // 填充试剂目录信息
        if (!items.isEmpty()) {
            Set<Long> reagentIds = items.stream()
                    .map(ShortageOrderItem::getReagentId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!reagentIds.isEmpty()) {
                Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
        List<ReagentCatalog> catalogs = catalogService.findByIds(
                        reagentIds.stream().map(Long::valueOf).collect(Collectors.toList()));
                for (ReagentCatalog c : catalogs) catalogMap.put(c.getId(), c);
                for (ShortageOrderItem item : items) {
                    if (item.getReagentId() != null) {
                        ReagentCatalog cat = catalogMap.get(Long.valueOf(item.getReagentId()));
                        if (cat != null) {
                            item.setReagentName(cat.getGenericName());
                            item.setGenericName(cat.getGenericName());
                            item.setSpecification(cat.getSpecification());
                            item.setManufacturer(cat.getManufacturer());
                            item.setUnit(cat.getUnit());
                        }
                    }
                    if (item.getQtyShortage() != null && item.getEstUnitPrice() != null) {
                        item.setEstAmount(item.getQtyShortage().multiply(item.getEstUnitPrice()));
                    }
                }
            }
        }
        return items;
    }

    @Override
    public ShortageOrderVO getShortageDetail(Long id) {
        ShortageOrder so = shortageMapper.selectById(id);
        if (so == null) throw new BizException(40400, "缺货单不存在");

        List<ShortageOrderItem> items = shortageItemMapper.selectList(
                new LambdaQueryWrapper<ShortageOrderItem>().eq(ShortageOrderItem::getShortageOrderId, id));
        if (items == null) items = Collections.emptyList();

        // 查询科室名称
        SysDept shortageDept = so.getFromDeptId() != null ? orgService.findDept(so.getFromDeptId()) : null;
        String deptName = shortageDept != null ? shortageDept.getDeptName()
                : so.getFromDeptId() != null ? "科室#" + so.getFromDeptId() : "-";

        ShortageOrderVO vo = new ShortageOrderVO();
        vo.setId(so.getId()); vo.setShortageNo(so.getShortageNo());
        vo.setReqId(so.getReqId());
        vo.setSourceReqNo(so.getReqId() != null ? "REQ#" + so.getReqId() : "-");
        vo.setFromDeptId(so.getFromDeptId());
        vo.setDeptName(deptName);
        vo.setStatus(so.getStatus()); vo.setUrgency(so.getUrgency());
        vo.setSuggestedSupplierId(so.getSuggestedSupplierId() != null ? so.getSuggestedSupplierId().intValue() : null);
        if (so.getSuggestedSupplierId() != null) {
            Supplier supplier = supplierService.findById(Long.valueOf(so.getSuggestedSupplierId()));
            vo.setSuggestedSupplier(supplier != null ? supplier.getName() : "供应商#" + so.getSuggestedSupplierId());
        } else {
            vo.setSuggestedSupplier("-");
        }
        vo.setPoId(so.getPoId());
        vo.setCreatedAt(so.getCreatedAt()); vo.setUpdatedAt(so.getUpdatedAt());

        if (!items.isEmpty()) {
            ShortageOrderItem first = items.get(0);
            // Try to get catalog info
            Set<Long> reagentIds = items.stream().map(ShortageOrderItem::getReagentId).collect(Collectors.toSet());
            Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
            if (!reagentIds.isEmpty()) {
                List<ReagentCatalog> catalogs = catalogService.findByIds(
                        reagentIds.stream().map(Long::valueOf).collect(Collectors.toList()));
                for (ReagentCatalog cat : catalogs) catalogMap.put(cat.getId(), cat);
            }
            ReagentCatalog cat = catalogMap.get(Long.valueOf(first.getReagentId()));
            vo.setReagentName(cat != null ? cat.getGenericName() : "试剂#" + first.getReagentId());
            vo.setSpec(cat != null ? cat.getSpecification() : "-");
            BigDecimal totalShortage = items.stream().map(ShortageOrderItem::getQtyShortage).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
            vo.setShortageQty(totalShortage);
            BigDecimal estAmount = items.stream().map(i -> {
                BigDecimal qty = i.getQtyShortage() != null ? i.getQtyShortage() : BigDecimal.ZERO;
                BigDecimal price = i.getEstUnitPrice() != null ? i.getEstUnitPrice() : BigDecimal.ZERO;
                return qty.multiply(price);
            }).reduce(BigDecimal.ZERO, BigDecimal::add);
            vo.setEstimatedAmount(estAmount);
        }
        vo.setItems(items);

        return vo;
    }

    @Override
    public ShortageOrder createShortageFromRequisition(Long reqId) {
        PurchaseRequisition req = reqMapper.selectById(reqId);
        if (req == null) throw new BizException(40400, "申领单不存在");

        // 事件驱动：查询申领明细，逐项比对库存，自动计算缺货量
        List<PurchaseRequisitionItem> reqItems = reqItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseRequisitionItem>().eq(PurchaseRequisitionItem::getReqId, reqId));
        if (reqItems == null || reqItems.isEmpty()) throw new BizException(40000, "申领单元明细，无法生成缺货单");

        // 确定目标仓库（中心库），用于查询真实库存
        Long targetWarehouseId = inventoryService.findActiveCentralWarehouse().getId();

        List<PurchaseRequisitionItem> shortItems = new ArrayList<>();
        for (PurchaseRequisitionItem item : reqItems) {
            // BUGFIX 1: 从 reagent_batch 查询真实库存，而非使用申领创建时初始化的 ZERO
            BigDecimal stock = BigDecimal.ZERO;
            if (item.getReagentId() != null) {
                List<ReagentBatch> batches = inventoryService.findInStockBatches(
                        item.getReagentId().longValue(), targetWarehouseId);
                if (batches != null && !batches.isEmpty()) {
                    stock = batches.stream()
                        .map(ReagentBatch::getAvailableStock)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                }
            }
            // 回写真实库存到申领明细
            item.setStockOnHand(stock);
            reqItemMapper.updateById(item);

            BigDecimal requested = item.getQtyRequested() != null ? item.getQtyRequested() : BigDecimal.ZERO;
            BigDecimal shortage = requested.subtract(stock);
            if (shortage.compareTo(BigDecimal.ZERO) > 0) {
                item.setQtyShortage(shortage);
                shortItems.add(item);
            }
        }
        if (shortItems.isEmpty()) {
            throw new BizException(40000, "当前库存满足所有申领需求，无缺货");
        }

        String no = "SH-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
                String.format("%03d", System.currentTimeMillis() % 1000);
        ShortageOrder so = new ShortageOrder();
        so.setShortageNo(no);
        so.setReqId(reqId);
        so.setFromDeptId(req.getFromDeptId());
        so.setUrgency(req.getUrgency());
        so.setStatus(ShortageStatusEnum.PENDING_PROCUREMENT);
        shortageMapper.insert(so);

        // 写入缺货明细到 shortage_order_item 表
        int lineNo = 1;
        for (PurchaseRequisitionItem item : shortItems) {
            ShortageOrderItem si = new ShortageOrderItem();
            si.setShortageOrderId(so.getId());
            si.setLineNo(lineNo++);
            si.setReqItemId(item.getId());
            si.setReagentId(item.getReagentId());
            si.setQtyShortage(item.getQtyShortage());
            si.setEstUnitPrice(item.getUnitPrice());
            shortageItemMapper.insert(si);
        }
        log.info("缺货单创建: shortageNo={}, reqId={}, itemCount={}", no, reqId, shortItems.size());
        return so;
    }

    // ── 采购订单 ──
    @Override
    public PageVO<PurchaseOrderVO> listOrders(int page, int pageSize, String status, String keyword) {
        LambdaQueryWrapper<PurchaseOrder> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) qw.eq(PurchaseOrder::getStatus, PurchaseOrderStatusEnum.valueOf(status));
        if (keyword != null) qw.like(PurchaseOrder::getPoNo, keyword);
        qw.orderByDesc(PurchaseOrder::getCreatedAt,PurchaseOrder::getId);
        Page<PurchaseOrder> p = orderMapper.selectPage(new Page<>(page, pageSize), qw);

        Set<Long> poIds = p.getRecords().stream().map(PurchaseOrder::getId).collect(Collectors.toSet());
        List<PurchaseOrderItem> allItems = poIds.isEmpty() ? Collections.emptyList()
                : orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>().in(PurchaseOrderItem::getPoId, poIds));
        Map<Long, List<PurchaseOrderItem>> itemsMap = allItems.stream()
                .collect(Collectors.groupingBy(PurchaseOrderItem::getPoId));

        Set<Long> reagentIds = allItems.stream().map(PurchaseOrderItem::getReagentId).collect(Collectors.toSet());
        Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
        if (!reagentIds.isEmpty()) {
            List<ReagentCatalog> catalogs = catalogService.findByIds(
                    reagentIds.stream().map(Long::valueOf).collect(Collectors.toList()));
            for (ReagentCatalog cat : catalogs) catalogMap.put(cat.getId(), cat);
        }

        // 批量查询供应商名称
        Set<Integer> supplierIds = p.getRecords().stream().map(PurchaseOrder::getSupplierId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> supplierNameMap = batchGetSupplierNames(supplierIds);

        List<PurchaseOrderVO> records = new ArrayList<>();
        for (PurchaseOrder po : p.getRecords()) {
            PurchaseOrderVO vo = new PurchaseOrderVO();
            vo.setId(po.getId()); vo.setPoNo(po.getPoNo());
            vo.setSupplierId(po.getSupplierId());
            vo.setSupplierName(supplierNameMap.getOrDefault(po.getSupplierId(),
                    po.getSupplierId() != null ? "供应商#" + po.getSupplierId() : "-"));
            vo.setSourceType(po.getSourceType());
            vo.setPlanId(po.getPlanId());vo.setApprovalOrigin(po.getApprovalOrigin());
            vo.setShortageOrderId(po.getShortageOrderId()); vo.setReqId(po.getReqId());
            vo.setAcceptanceMethod(po.getAcceptanceMethod());
            vo.setTotalAmount(po.getTotalAmount()); vo.setStatus(po.getStatus());
            vo.setOrderDate(po.getOrderDate());
            vo.setExpectedDeliveryDate(po.getExpectedDeliveryDate());
            vo.setReceivedQuantity(po.getReceivedQuantity());
            vo.setCreatedAt(po.getCreatedAt()); vo.setUpdatedAt(po.getUpdatedAt());

            List<PurchaseOrderItem> items = itemsMap.getOrDefault(po.getId(), Collections.emptyList());
            if (!items.isEmpty()) {
                PurchaseOrderItem first = items.get(0);
                ReagentCatalog cat = catalogMap.get(Long.valueOf(first.getReagentId()));
                vo.setReagentName(cat != null ? cat.getGenericName() : "试剂#" + first.getReagentId());
                vo.setSpecification(cat != null ? cat.getSpecification() : "-");
                // 数量 = 所有明细行数量汇总
                BigDecimal totalQty = items.stream()
                        .map(i -> i.getQtyOrdered() != null ? i.getQtyOrdered() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                vo.setOrderQuantity(totalQty);
                vo.setUnitPrice(first.getUnitPrice());
            }
            vo.setItems(items);
            records.add(vo);
        }
        return new PageVO<>(p.getTotal(), page, pageSize, records);
    }

    @Override
    public PurchaseOrderVO getOrderDetail(Long id) {
        PurchaseOrder po = orderMapper.selectById(id);
        if (po == null) throw new BizException(40400, "采购订单不存在");

        List<PurchaseOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getPoId, id));
        if (items == null) items = Collections.emptyList();

        PurchaseOrderVO vo = new PurchaseOrderVO();
        vo.setId(po.getId()); vo.setPoNo(po.getPoNo());
        vo.setSupplierId(po.getSupplierId());
        if (po.getSupplierId() != null) {
            Supplier supplier = supplierService.findById(Long.valueOf(po.getSupplierId()));
            vo.setSupplierName(supplier != null ? supplier.getName() : "供应商#" + po.getSupplierId());
        } else {
            vo.setSupplierName("-");
        }
        vo.setSourceType(po.getSourceType());
        vo.setPlanId(po.getPlanId());vo.setApprovalOrigin(po.getApprovalOrigin());
        vo.setShortageOrderId(po.getShortageOrderId()); vo.setReqId(po.getReqId());
        vo.setTotalAmount(po.getTotalAmount()); vo.setStatus(po.getStatus());
        vo.setOrderDate(po.getOrderDate());
        vo.setExpectedDeliveryDate(po.getExpectedDeliveryDate());
        vo.setReceivedQuantity(po.getReceivedQuantity());
        vo.setAcceptanceMethod(po.getAcceptanceMethod());
        vo.setCreatedAt(po.getCreatedAt()); vo.setUpdatedAt(po.getUpdatedAt());

        if (!items.isEmpty()) {
            PurchaseOrderItem first = items.get(0);
            Set<Long> reagentIds = items.stream().map(PurchaseOrderItem::getReagentId).collect(Collectors.toSet());
            Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
            if (!reagentIds.isEmpty()) {
                List<ReagentCatalog> catalogs = catalogService.findByIds(
                        reagentIds.stream().map(Long::valueOf).collect(Collectors.toList()));
                for (ReagentCatalog cat : catalogs) catalogMap.put(cat.getId(), cat);
            }
            ReagentCatalog cat = catalogMap.get(Long.valueOf(first.getReagentId()));
            vo.setReagentName(cat != null ? cat.getGenericName() : "试剂#" + first.getReagentId());
            vo.setSpecification(cat != null ? cat.getSpecification() : "-");
            // 数量 = 所有明细行数量汇总
            BigDecimal totalQty = items.stream()
                    .map(i -> i.getQtyOrdered() != null ? i.getQtyOrdered() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            vo.setOrderQuantity(totalQty);
            vo.setUnitPrice(first.getUnitPrice());
        }
        vo.setItems(items);

        return vo;
    }

    @Override
    public PurchaseOrder createOrderFromShortage(Long shortageId, Integer supplierId, String expectedDeliveryDate, String settlementMode, String remark) {
        if (shortageId == null || supplierId == null || supplierId <= 0)
            throw new BizException(40001, "缺货单与供应商必填");
        // 先锁定合法来源，再校验未转单状态；重复请求不能产生另一张采购义务。
        ShortageOrder so = shortageMapper.selectOne(new LambdaQueryWrapper<ShortageOrder>()
                .eq(ShortageOrder::getId, shortageId).last("FOR UPDATE"));
        if (so == null) throw new BizException(40400, "缺货单不存在或无权访问");
        if (so.getStatus() != ShortageStatusEnum.PENDING_PROCUREMENT || so.getPoId() != null)
            throw new BizException(40900, "该缺货单已处理，不能重复生成采购单");
        requireActiveSupplier(supplierId);
        List<ShortageOrderItem> shItems = shortageItemMapper.selectList(new LambdaQueryWrapper<ShortageOrderItem>()
                .eq(ShortageOrderItem::getShortageOrderId, shortageId).last("FOR UPDATE"));
        if (shItems == null || shItems.isEmpty()) throw new BizException(40001, "缺货单没有可采购的明细");
        Map<Long, ReagentCatalog> sourceCatalogs = new HashMap<>();
        for (var sourceItem : shItems) {
            requirePositive(sourceItem.getQtyShortage(), "缺货数量");
            if (sourceItem.getEstUnitPrice() == null || sourceItem.getEstUnitPrice().signum() < 0)
                throw new BizException(40001, "缺货来源价格不明，请先核对，不能按零金额建单");
            var catalog = catalogService.lockForBusiness(sourceItem.getReagentId());
            if (catalog == null || catalog.getApprovalStatus() != com.zlth.ivd.common.enums.ApprovalStatusEnum.APPROVED
                    || catalog.getMaterialType() == null) throw new BizException(40001, "采购目录不存在、未批准或类型未维护");
            sourceCatalogs.put(catalog.getId(), catalog);
        }
        LocalDate orderDate = LocalDate.now();
        String no = reservePurchaseNumbers(orderDate, 1).next();
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNo(no);
        po.setSupplierId(supplierId);
        po.setSourceType("SHORTAGE");
        po.setApprovalOrigin("PLAN_REQUIRED");
        var methods=sourceCatalogs.values().stream().map(c->c.getMaterialType().entersUdiStateMachine()?"SCAN":"MANUAL").distinct().toList();
        po.setAcceptanceMethod(methods.size()==1?methods.get(0):"MIXED");
        po.setSettlementMode(com.zlth.ivd.common.enums.SettlementModeEnum.normalizeNewOrder(settlementMode));
        po.setSourceId(shortageId);
        po.setShortageOrderId(shortageId);
        po.setReqId(so.getReqId());
        po.setStatus(PurchaseOrderStatusEnum.DRAFT);
        po.setOrderDate(orderDate);
        if (expectedDeliveryDate != null && !expectedDeliveryDate.isEmpty()) {
            po.setExpectedDeliveryDate(LocalDate.parse(expectedDeliveryDate));
        }
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());
        orderMapper.insert(po);

        // BUGFIX 2: 从缺货明细复制到采购订单明细
        if (shItems != null && !shItems.isEmpty()) {
            int lineNo = 1;
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (ShortageOrderItem si : shItems) {
                PurchaseOrderItem poi = new PurchaseOrderItem();
                poi.setPoId(po.getId());
                poi.setLineNo(lineNo++);
                poi.setReagentId(si.getReagentId());
                poi.setSupplierId(supplierId);
                poi.setMaterialType(sourceCatalogs.get(si.getReagentId()).getMaterialType().name());
                poi.setQtyOrdered(si.getQtyShortage());
                BigDecimal unitPrice = si.getEstUnitPrice();
                poi.setUnitPrice(unitPrice);
                poi.setAmount(si.getQtyShortage() != null ? si.getQtyShortage().multiply(unitPrice) : BigDecimal.ZERO);
                poi.setQtyReceived(BigDecimal.ZERO);
                poi.setVersion(0);
                poi.setCreatedAt(LocalDateTime.now());
                orderItemMapper.insert(poi);
                totalAmount = totalAmount.add(poi.getAmount());
            }
            po.setTotalAmount(totalAmount);
            po.setReceivedQuantity(BigDecimal.ZERO);
            orderMapper.updateById(po);
        }

        // 更新缺货单关联
        so.setPoId(po.getId());
        so.setStatus(ShortageStatusEnum.PURCHASING);
        shortageMapper.updateById(so);
        appendShortageTransition("SHORTAGE_CONVERTED",so.getId(),po.getId(),"PENDING_PROCUREMENT","PURCHASING");
        log.info("采购订单创建: poNo={}, shortageId={}", no, shortageId);
        return po;
    }

    @Override
    public List<PurchaseOrder> createManualOrder(CreateManualOrderRequest request) {
        if(request==null)throw new BizException(40001,"采购参数不能为空");
        List<CreateManualOrderRequest.ManualOrderItem> allItems = request.getItems();
        if (allItems == null || allItems.isEmpty()) {
            throw new BizException(40000, "采购明细不能为空");
        }
        for(var item:allItems){if(item==null || item.getReagentId()==null)throw new BizException(40001,"采购试剂必填");requirePositive(item.getQuantity(),"采购数量");}

        // 0. 批量查询所有试剂目录（供后续取值：单价 + 配送商→供应商解析）
        Set<Long> allReagentIds = allItems.stream()
                .map(CreateManualOrderRequest.ManualOrderItem::getReagentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ReagentCatalog> allCatalogMap = new HashMap<>();
        if (!allReagentIds.isEmpty()) {
            List<ReagentCatalog> catalogs = catalogService.findByIds(allReagentIds);
            for (ReagentCatalog c : catalogs) allCatalogMap.put(c.getId(), c);
        }
        for(var item:allItems){var catalog=catalogService.lockForBusiness(item.getReagentId());
            if(catalog!=null)allCatalogMap.put(item.getReagentId(),catalog);
            if(catalog==null || catalog.getMaterialType()==null || catalog.getApprovalStatus()!=com.zlth.ivd.common.enums.ApprovalStatusEnum.APPROVED
                    || catalog.getPricePerUnit()==null || catalog.getPricePerUnit().signum()<0)
                throw new BizException(40001,"采购试剂目录须已批准并维护非负单价");
            if(item.getSupplierId()==null && request.getSupplierId()!=null)item.setSupplierId(request.getSupplierId());
        }

        // 1. 从试剂目录的 distributor 字段解析缺失的 supplierId
        //    ReagentCatalog.distributor 存的是配送商名称，需要反查 Supplier 表得到 ID
        for (CreateManualOrderRequest.ManualOrderItem item : allItems) {
            Supplier supplier = resolvePurchaseSupplier(item.getSupplierId(), allCatalogMap.get(item.getReagentId()).getDistributor());
            item.setSupplierId(Math.toIntExact(supplier.getId()));
        }

        // 2. 按已验证的供应商分组
        record OrderGroup(Integer supplierId,String method) {}
        Map<OrderGroup, List<CreateManualOrderRequest.ManualOrderItem>> grouped = new LinkedHashMap<>();
        for (CreateManualOrderRequest.ManualOrderItem item : allItems) {
            String method=allCatalogMap.get(item.getReagentId()).getMaterialType().entersUdiStateMachine()?"SCAN":"MANUAL";
            grouped.computeIfAbsent(new OrderGroup(item.getSupplierId(),method), k -> new ArrayList<>()).add(item);
        }

        List<PurchaseOrder> results = new ArrayList<>();
        LocalDate orderDate = parsePurchaseDate(request.getOrderDate(),LocalDate.now());
        LocalDate expectedDate = parsePurchaseDate(request.getExpectedDeliveryDate(),null);
        if(orderDate.isAfter(LocalDate.now()) || (expectedDate!=null && expectedDate.isBefore(orderDate)))
            throw new BizException(40001,"下单日期不能为未来日期，预计到货不能早于下单日期");
        var numbers = reservePurchaseNumbers(orderDate, grouped.size());

        for (Map.Entry<OrderGroup, List<CreateManualOrderRequest.ManualOrderItem>> entry : grouped.entrySet()) {
            Integer supplierId = entry.getKey().supplierId();
            List<CreateManualOrderRequest.ManualOrderItem> groupItems = entry.getValue();

            // 1. 先插入 PO 头（获取雪花 ID）
            String no = numbers.next();
            PurchaseOrder po = new PurchaseOrder();
            po.setPoNo(no);
            po.setSourceType("MANUAL");
            po.setApprovalOrigin("PLAN_REQUIRED");
            po.setAcceptanceMethod(entry.getKey().method());
            po.setSupplierId(supplierId);
            po.setSettlementMode(com.zlth.ivd.common.enums.SettlementModeEnum.normalizeNewOrder(request.getSettlementMode()));
            po.setStatus(PurchaseOrderStatusEnum.DRAFT);
            po.setOrderDate(orderDate);
            po.setExpectedDeliveryDate(expectedDate);
            po.setTotalAmount(BigDecimal.ZERO);
            po.setReceivedQuantity(BigDecimal.ZERO);
            po.setCreatedAt(LocalDateTime.now());
            po.setUpdatedAt(LocalDateTime.now());
            orderMapper.insert(po);  // 必须先插入，po.getId() 才会被赋值

            // 2. 创建采购明细（单价/配送商等从批量查询的 allCatalogMap 取值）
            BigDecimal totalAmount = BigDecimal.ZERO;
            BigDecimal totalQty = BigDecimal.ZERO;
            int line = 1;
            for (CreateManualOrderRequest.ManualOrderItem item : groupItems) {
                PurchaseOrderItem poi = new PurchaseOrderItem();
                poi.setPoId(po.getId());
                poi.setLineNo(line++);
                poi.setReagentId(item.getReagentId());
                poi.setSupplierId(supplierId);
                poi.setMaterialType(allCatalogMap.get(item.getReagentId()).getMaterialType().name());
                poi.setQtyOrdered(item.getQuantity());

                ReagentCatalog cat = allCatalogMap.get(item.getReagentId());
                BigDecimal unitPrice = (cat != null && cat.getPricePerUnit() != null)
                        ? cat.getPricePerUnit() : BigDecimal.ZERO;
                poi.setUnitPrice(unitPrice);
                poi.setAmount(unitPrice.multiply(item.getQuantity()));
                poi.setQtyReceived(BigDecimal.ZERO);
                poi.setVersion(0);
                poi.setCreatedAt(LocalDateTime.now());
                orderItemMapper.insert(poi);

                totalQty = totalQty.add(item.getQuantity());
                totalAmount = totalAmount.add(poi.getAmount());
            }

            // 4. 回写 PO 总金额（订购数量由 listOrders 从明细实时汇总，不需要在 PO 头冗余存储）
            po.setTotalAmount(totalAmount);
            orderMapper.updateById(po);

            log.info("手动采购订单创建: poNo={}, supplierId={}, items={}, totalQty={}, totalAmount={}",
                    no, supplierId, groupItems.size(), totalQty, totalAmount);
            results.add(po);
        }

        log.info("手动采购订单拆分完成: 共 {} 张订单", results.size());
        return results;
    }

    // ── 验收 ──
    @Override
    public void receiveOrder(ReceiveItemRequest request) {
        if (request == null) throw new BizException(40001, "验收参数不能为空");
        requirePositive(request.getQtyReceived(), "验收数量");
        if(request.getBatchNo()!=null)request.setBatchNo(request.getBatchNo().trim());
        // BUGFIX 10: 先取出订单明细并校验归属
        PurchaseOrderItem item = orderItemMapper.selectById(request.getItemId());
        if (item == null) throw new BizException(40400, "采购订单明细不存在");
        PurchaseOrder order=orderMapper.lockById(item.getPoId());
        if(order==null)throw new BizException(40400,"采购订单不存在");
        if(order.getStatus()==PurchaseOrderStatusEnum.CANCELLED || order.getStatus()==PurchaseOrderStatusEnum.COMPLETED)
            throw new BizException(40001,"已取消或已完成的订单不能继续验收");
        if(!"LEGACY".equals(order.getApprovalOrigin()) && !"DIRECT_RECEIPT".equals(order.getApprovalOrigin())) {
            if(!"PLAN".equals(order.getApprovalOrigin()) || order.getPlanId()==null
                    || !Set.of(PurchaseOrderStatusEnum.SENT,PurchaseOrderStatusEnum.PARTIAL_RECEIVED).contains(order.getStatus()))
                throw new BizException(40900,"采购订单未完成计划审批转单，不能验收");
            var approvedPlan=purchasePlanMapper.selectById(order.getPlanId());
            if(approvedPlan==null || !"ORDERED".equals(approvedPlan.getStatus()) || approvedPlan.getReviewedBy()==null)
                throw new BizException(40900,"采购计划批准依据不完整，不能验收");
        }
        requireActiveSupplier(order.getSupplierId());
        ReagentCatalog receivedCatalog = requireApprovedReceiptCatalog(item.getReagentId());
        String receiptUdiDi = resolveReceiptUdiDi(receivedCatalog, request.getUdiDi());
        boolean pendingUdi = receiptUdiDi == null && receivedCatalog.getMaterialType().entersUdiStateMachine();
        request.setBatchNo(validateReceipt(request.getBatchNo(),request.getExpiryDate(),request.getProductionDate(),receivedCatalog.getMaterialType()));
        if(item.getUnitPrice()==null || item.getUnitPrice().signum()<0)throw new BizException(40001,"验收来源单价未维护或为负，请先核对采购明细");
        if(Integer.valueOf(1).equals(receivedCatalog.getEnableUniqueCode()) && request.getQtyReceived().stripTrailingZeros().scale()>0)
            throw new BizException(40001,"唯一码管控物料须按完整单位验收");
        Long warehouseId=request.getWarehouseId()!=null?request.getWarehouseId():inventoryService.findActiveCentralWarehouse().getId();
        if(!Integer.valueOf(1).equals(inventoryService.requireWarehouse(warehouseId).getLevel()))throw new BizException(40001,"采购验收必须进入本机构中心库");
        if (request.getOrderId() != null && !request.getOrderId().equals(item.getPoId())) {
            throw new BizException(40000, "订单明细不属于该采购订单");
        }
        request.setOrderId(item.getPoId());

        BigDecimal qty = request.getQtyReceived() != null ? request.getQtyReceived() : BigDecimal.ONE;

        // BUGFIX 8+9: 乐观锁更新采购明细已收数量（独立重试循环，不掺杂批次操作）
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            PurchaseOrderItem freshItem = orderItemMapper.lockById(request.getItemId());
            if (freshItem == null) throw new BizException(40400, "采购订单明细不存在");

            // ★ 每次重试都从 freshItem 读取最新的 ordered，防止并发编辑
            BigDecimal ordered = freshItem.getQtyOrdered() != null ? freshItem.getQtyOrdered() : BigDecimal.ZERO;
            BigDecimal currentReceived = freshItem.getQtyReceived() != null ? freshItem.getQtyReceived() : BigDecimal.ZERO;
            BigDecimal newReceived = currentReceived.add(qty);
            if (newReceived.compareTo(ordered) > 0) {
                throw new BizException(40000, "收货数量超过订购数量");
            }
            freshItem.setQtyReceived(newReceived);
            int itemUpdated = orderItemMapper.updateById(freshItem);
            if (itemUpdated > 0) break;
            if (attempt == maxRetries - 1) {
                throw new BizException(50000, "验收明细更新失败：超过最大重试次数");
            }
            log.warn("验收明细更新乐观锁冲突，重试 ({}/{})", attempt + 1, maxRetries);
        }

        // 创建/更新试剂批次记录（入库）— 使用独立的批次重试，失败不回退 item
        if (request.getBatchNo() != null && !request.getBatchNo().isBlank()) {
            var source=new ReagentBatch();source.setBatchNo(request.getBatchNo().trim());source.setReagentId(item.getReagentId());
            source.setMaterialType(receivedCatalog.getMaterialType());source.setUnit(receivedCatalog.getUnit());
            source.setUdiDi(receiptUdiDi);
            if (pendingUdi) source.setUdiStatus(UdiStatusEnum.PENDING_UDI);
            source.setManufactureDate(request.getProductionDate());source.setExpiryDate(request.getExpiryDate());
            source.setUnitPrice(item.getUnitPrice());source.setOriginPurchaseOrderItemId(item.getId());
            source.setSettlementMode(order.getSettlementMode());
            var savedBatch = inventoryService.receiveIntoWarehouse(source, warehouseId, qty);
            if (!Objects.equals(normalizeReceiptUdiDi(savedBatch.getUdiDi()), receiptUdiDi))
                throw new BizException(40900, "在库批次 UDI-DI 与本次验收不一致，请先核对历史记录");
            Long savedBatchId = savedBatch.getId();

            // BUGFIX 6: 写入 INBOUND consumption_record 审计追踪
            ConsumptionRecord cr = new ConsumptionRecord();
            cr.setId(IdGenerator.nextId());
            cr.setRecordType(ConsumptionRecordTypeEnum.INBOUND.getCode());
            cr.setBatchNo(request.getBatchNo());
            cr.setBatchId(savedBatchId);
            cr.setPurchaseOrderItemId(item.getId());
            cr.setSettlementMode(order.getSettlementMode());
            cr.setUnit(receivedCatalog.getUnit());
            cr.setReagentId(item.getReagentId().longValue());
            cr.setMaterialType(receivedCatalog.getMaterialType().name());
            cr.setQuantity(qty);
            cr.setUnitPrice(item.getUnitPrice());
            cr.setAmount(item.getUnitPrice() != null ? item.getUnitPrice().multiply(qty) : null);
            cr.setWarehouseId(warehouseId);
            cr.setOperatorId(SecurityUtils.getCurrentOperatorIdOrNull());
            cr.setOccurredAt(LocalDateTime.now());
            cr.setCreatedAt(LocalDateTime.now());
            consumptionService.appendRecord(cr);

            // 按入库数量生成唯一码（仅当试剂目录开启唯一码管控时）
            if (!pendingUdi && Integer.valueOf(1).equals(receivedCatalog.getEnableUniqueCode())) {
                int codeCount = qty.intValue();
                uniqueCodeService.generateAndSave(codeCount, savedBatchId,
                        item.getReagentId().longValue(), request.getBatchNo(), receiptUdiDi);
            }
        }

        // 更新采购订单状态
        updateOrderStatus(request.getOrderId());

        log.info("验收收货完成: orderId={}, itemId={}, qty={}, batchNo={}",
                request.getOrderId(), request.getItemId(), qty, request.getBatchNo());
    }

    /** 根据所有明细的收货情况更新订单状态 */
    private void updateOrderStatus(Long orderId) {
        PurchaseOrder po = orderMapper.lockById(orderId);
        if (po == null) return;

        List<PurchaseOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getPoId, orderId).last("FOR UPDATE"));

        boolean allReceived = items.stream().allMatch(i -> {
            BigDecimal received = i.getQtyReceived() != null ? i.getQtyReceived() : BigDecimal.ZERO;
            BigDecimal ordered = i.getQtyOrdered() != null ? i.getQtyOrdered() : BigDecimal.ZERO;
            return received.compareTo(ordered) >= 0;
        });
        boolean anyReceived = items.stream().anyMatch(i -> {
            BigDecimal received = i.getQtyReceived() != null ? i.getQtyReceived() : BigDecimal.ZERO;
            return received.compareTo(BigDecimal.ZERO) > 0;
        });

        if (allReceived) {
            po.setStatus(PurchaseOrderStatusEnum.COMPLETED);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrderStatusEnum.PARTIAL_RECEIVED);
        }

        // 同步更新 PO 头的已收货总数量
        BigDecimal totalReceived = items.stream()
                .map(i -> i.getQtyReceived() != null ? i.getQtyReceived() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        po.setReceivedQuantity(totalReceived);
        orderMapper.updateById(po);
        if (allReceived && po.getShortageOrderId() != null) {
            ShortageOrder source = shortageMapper.selectOne(new LambdaQueryWrapper<ShortageOrder>()
                    .eq(ShortageOrder::getId, po.getShortageOrderId()).last("FOR UPDATE"));
            if (source == null || (!"PLAN".equals(po.getApprovalOrigin()) && !Objects.equals(source.getPoId(), po.getId()))
                    || source.getStatus() != ShortageStatusEnum.PURCHASING)
                throw new BizException(40900, "采购与缺货来源关联不一致，请先核对");
            var related=orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                    .eq(PurchaseOrder::getShortageOrderId,source.getId()).last("FOR UPDATE"));
            if(related.stream().allMatch(order->order.getStatus()==PurchaseOrderStatusEnum.COMPLETED
                    || order.getStatus()==PurchaseOrderStatusEnum.CANCELLED)) {
                source.setStatus(ShortageStatusEnum.COMPLETED);
                shortageMapper.updateById(source);
                appendShortageTransition("SHORTAGE_COMPLETED",source.getId(),po.getId(),"PURCHASING","COMPLETED");
            }
        }
    }

    /** 新状态与不可变领域审计同一事务，写审计失败不能返回业务成功。 */
    private void appendShortageTransition(String action,Long shortageId,Long purchaseOrderId,String from,String to) {
        domainAudits.appendDomain(com.zlth.ivd.common.audit.OperationAuditEntry.builder()
                .operatorId(SecurityUtils.getCurrentOperatorIdOrNull()).operatorName(SecurityUtils.getCurrentUsername())
                .module("procurement").action(action).handler("ProcurementServiceImpl")
                .reason("shortageId="+shortageId+",purchaseOrderId="+purchaseOrderId+",from="+from+",to="+to)
                .success(true).durationMs(0L).occurredAt(LocalDateTime.now()).build());
    }

    // ── 人工验收直收（无采购订单直接验收入库）──
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> receiveDirect(DirectReceiveRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty())
            throw new BizException(40001, "验收明细不能为空");
        for (var item : request.getItems()) {
            if (item == null || item.getReagentId() == null) throw new BizException(40001, "请选择验收试剂");
            requirePositive(item.getQtyReceived(), "验收数量");
            var catalog = requireApprovedReceiptCatalog(item.getReagentId());
            resolveReceiptUdiDi(catalog, item.getUdiDi());
            item.setBatchNo(validateReceipt(item.getBatchNo(),item.getExpiryDate(),item.getProductionDate(),catalog.getMaterialType()));
            if(item.getUnitPrice()!=null && item.getUnitPrice().signum()<0)throw new BizException(40001,"单价不得为负");
        }
        Integer supplierId = request.getSupplierId();
        requireActiveSupplier(supplierId);
        Long warehouseId = request.getWarehouseId()!=null?request.getWarehouseId():inventoryService.findActiveCentralWarehouse().getId();
        if(!Integer.valueOf(1).equals(inventoryService.requireWarehouse(warehouseId).getLevel()))throw new BizException(40001,"验收必须进入中心库");

        // 1. 自动创建 MANUAL 采购订单
        PurchaseOrder po = new PurchaseOrder();
        po.setId(IdGenerator.nextId());
        po.setSupplierId(supplierId);
        po.setSourceType("MANUAL");
        po.setApprovalOrigin("DIRECT_RECEIPT");
        po.setAcceptanceMethod("MANUAL");
        po.setSettlementMode(com.zlth.ivd.common.enums.SettlementModeEnum.normalizeNewOrder(request.getSettlementMode()));
        po.setStatus(PurchaseOrderStatusEnum.PARTIAL_RECEIVED);
        po.setOrderDate(LocalDate.now());
        po.setExpectedDeliveryDate(LocalDate.now());
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());

        po.setPoNo(reservePurchaseNumbers(po.getOrderDate(), 1).next());
        orderMapper.insert(po);

        // 2. 创建订单明细 + 直接入库
        int successCount = 0;
        List<ReceiveItemRequest> receiptRequests=new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalReceivedQty = BigDecimal.ZERO;

        for (DirectReceiveRequest.DirectReceiveItem item : request.getItems()) {
            if (item.getReagentId() == null) continue;
            requirePositive(item.getQtyReceived(), "验收数量");
            BigDecimal qty = item.getQtyReceived();

            // 查试剂目录获取单价
            BigDecimal unitPrice = item.getUnitPrice();
            if (unitPrice == null) {
                ReagentCatalog catalog = catalogService.findById(item.getReagentId().longValue());
                unitPrice = catalog != null ? catalog.getPricePerUnit() : null;
            }
            if(unitPrice==null || unitPrice.signum()<0)throw new BizException(40001,"验收单价未维护或为负");

            // 2.1 创建订单明细
            PurchaseOrderItem poi = new PurchaseOrderItem();
            poi.setId(IdGenerator.nextId());
            poi.setPoId(po.getId());
            poi.setLineNo(successCount + 1);
            poi.setReagentId(item.getReagentId());
            poi.setSupplierId(supplierId);
            poi.setMaterialType("REAGENT");
            poi.setQtyOrdered(qty);
            poi.setUnitPrice(unitPrice);
            poi.setAmount(unitPrice.multiply(qty));
            poi.setQtyReceived(BigDecimal.ZERO);
            poi.setVersion(0);
            poi.setCreatedAt(LocalDateTime.now());
            orderItemMapper.insert(poi);

            totalAmount = totalAmount.add(unitPrice.multiply(qty));
            totalReceivedQty = totalReceivedQty.add(qty);

            var receipt=new ReceiveItemRequest();receipt.setOrderId(po.getId());receipt.setItemId(poi.getId());
            receipt.setQtyReceived(qty);receipt.setBatchNo(item.getBatchNo());receipt.setExpiryDate(item.getExpiryDate());
            receipt.setProductionDate(item.getProductionDate());receipt.setUdiDi(item.getUdiDi());receipt.setWarehouseId(warehouseId);
            receiptRequests.add(receipt);
            successCount++;
        }

        // 3. 更新 PO 总金额、已收货数量和状态
        po.setTotalAmount(totalAmount);
        po.setReceivedQuantity(BigDecimal.ZERO);
        po.setStatus(PurchaseOrderStatusEnum.PARTIAL_RECEIVED);
        orderMapper.updateById(po);
        for(var receipt:receiptRequests)receiveOrder(receipt);
        po=orderMapper.selectById(po.getId());

        log.info("人工验收直收完成: poNo={}, items={}, totalAmount={}", po.getPoNo(), successCount, totalAmount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("poId", po.getId());
        result.put("poNo", po.getPoNo());
        result.put("successCount", successCount);
        result.put("totalAmount", totalAmount);
        return result;
    }

    // ── 统计 ──
    @Override
    public Map<String, Long> getRequisitionStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", reqMapper.selectCount(null));
        stats.put("pending", reqMapper.selectCount(new LambdaQueryWrapper<PurchaseRequisition>().eq(PurchaseRequisition::getStatus, RequisitionStatusEnum.PENDING_REVIEW)));
        stats.put("picking", reqMapper.selectCount(new LambdaQueryWrapper<PurchaseRequisition>().eq(PurchaseRequisition::getStatus, RequisitionStatusEnum.PICKING)));
        stats.put("completed", reqMapper.selectCount(new LambdaQueryWrapper<PurchaseRequisition>().in(PurchaseRequisition::getStatus, RequisitionStatusEnum.FULFILLED, RequisitionStatusEnum.PARTIAL_FULFILLED)));
        stats.put("shortage", reqMapper.selectCount(new LambdaQueryWrapper<PurchaseRequisition>().eq(PurchaseRequisition::getStatus, RequisitionStatusEnum.SHORTAGE_PENDING)));
        return stats;
    }

    @Override
    public Map<String, Long> getShortageStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", shortageMapper.selectCount(null));
        stats.put("pending", shortageMapper.selectCount(new LambdaQueryWrapper<ShortageOrder>().eq(ShortageOrder::getStatus, ShortageStatusEnum.PENDING_PROCUREMENT)));
        stats.put("purchasing", shortageMapper.selectCount(new LambdaQueryWrapper<ShortageOrder>().eq(ShortageOrder::getStatus, ShortageStatusEnum.PURCHASING)));
        stats.put("completed", shortageMapper.selectCount(new LambdaQueryWrapper<ShortageOrder>().eq(ShortageOrder::getStatus, ShortageStatusEnum.COMPLETED)));
        return stats;
    }

    @Override
    public Map<String, Long> getOrderStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", orderMapper.selectCount(null));
        stats.put("pending", orderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getStatus, PurchaseOrderStatusEnum.DRAFT)));
        stats.put("partial", orderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getStatus, PurchaseOrderStatusEnum.PARTIAL_RECEIVED)));
        stats.put("completed", orderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getStatus, PurchaseOrderStatusEnum.COMPLETED)));
        stats.put("cancelled", orderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getStatus, PurchaseOrderStatusEnum.CANCELLED)));
        return stats;
    }

    @Override
    public PageVO<Map<String, Object>> listDeptRequisitions(int page, int pageSize, Long deptId) {
        LambdaQueryWrapper<PurchaseRequisition> qw = new LambdaQueryWrapper<>();
        if (deptId != null) qw.eq(PurchaseRequisition::getFromDeptId, deptId);
        qw.orderByDesc(PurchaseRequisition::getCreatedAt);
        Page<PurchaseRequisition> p = reqMapper.selectPage(new Page<>(page, pageSize), qw);

        // 批量查 items
        Set<Long> reqIds = p.getRecords().stream().map(PurchaseRequisition::getId).collect(Collectors.toSet());
        List<PurchaseRequisitionItem> allItems = reqIds.isEmpty() ? Collections.emptyList()
                : reqItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequisitionItem>().in(PurchaseRequisitionItem::getReqId, reqIds));
        Map<Long, List<PurchaseRequisitionItem>> itemsMap = allItems.stream()
                .collect(Collectors.groupingBy(PurchaseRequisitionItem::getReqId));

        // 批量查试剂名称
        Set<Long> reagentIds = allItems.stream().map(PurchaseRequisitionItem::getReagentId).collect(Collectors.toSet());
        Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
        if (!reagentIds.isEmpty()) {
            List<ReagentCatalog> catalogs = catalogService.findByIds(
                    reagentIds.stream().map(Long::valueOf).collect(Collectors.toList()));
            for (ReagentCatalog c : catalogs) catalogMap.put(c.getId(), c);
        }

        // 批量查询用户名称
        Set<Long> userIds = p.getRecords().stream().map(PurchaseRequisition::getApplicantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNameMap = batchGetUserNames(userIds);

        // 组装返回
        List<Map<String, Object>> records = new ArrayList<>();
        for (PurchaseRequisition req : p.getRecords()) {
            List<PurchaseRequisitionItem> items = itemsMap.getOrDefault(req.getId(), Collections.emptyList());
            for (PurchaseRequisitionItem item : items) {
                Map<String, Object> row = new LinkedHashMap<>();
                ReagentCatalog cat = catalogMap.get(Long.valueOf(item.getReagentId()));
                row.put("reqNo", req.getReqNo());
                row.put("reagentName", cat != null ? cat.getGenericName() : "试剂#" + item.getReagentId());
                row.put("qty", item.getQtyRequested());
                row.put("source", req.getUrgency() != null && req.getUrgency().name().contains("URGENT") ? "紧急请领" : "常规请领");
                row.put("applicant", userNameMap.getOrDefault(req.getApplicantId(), "用户#" + (req.getApplicantId() != null ? req.getApplicantId() : "-")));
                row.put("status", req.getStatus() != null ? req.getStatus().getLabel() : "-");
                row.put("createdAt", req.getCreatedAt() != null ? req.getCreatedAt().toString().replace("T", " ") : null);
                records.add(row);
            }
            // 若无明细，至少返回申领单本身
            if (items.isEmpty()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("reqNo", req.getReqNo());
                row.put("reagentName", "-");
                row.put("qty", BigDecimal.ZERO);
                row.put("source", req.getUrgency() != null && req.getUrgency().name().contains("URGENT") ? "紧急请领" : "常规请领");
                row.put("applicant", userNameMap.getOrDefault(req.getApplicantId(), "用户#" + (req.getApplicantId() != null ? req.getApplicantId() : "-")));
                row.put("status", req.getStatus() != null ? req.getStatus().getLabel() : "-");
                row.put("createdAt", req.getCreatedAt() != null ? req.getCreatedAt().toString().replace("T", " ") : null);
                records.add(row);
            }
        }

        return new PageVO<>(p.getTotal(), page, pageSize, records);
    }

    @Override
    @Transactional(readOnly=true)
    public List<Map<String, Object>> getSmartReplenishSuggestions(int days) {
        validateAnalysisDays(days);
        var runs=replenishRuns.selectList(new LambdaQueryWrapper<SmartReplenishRun>()
                .eq(SmartReplenishRun::getAnalysisDays,days).orderByDesc(SmartReplenishRun::getId).last("LIMIT 1"));
        if(runs.isEmpty())return List.of();
        var persisted=suggestionMapper.selectList(new LambdaQueryWrapper<SmartReplenishSuggestion>()
                .eq(SmartReplenishSuggestion::getBatchNo,runs.get(0).getBatchNo())
                .eq(SmartReplenishSuggestion::getStatus,"PENDING")
                .orderByDesc(SmartReplenishSuggestion::getSuggestedQty).orderByAsc(SmartReplenishSuggestion::getId));
        return suggestionViews(persisted);
    }

    @Override
    public List<Map<String, Object>> recalculateSmartReplenishSuggestions(int days) {
        validateAnalysisDays(days);
        var scope=com.zlth.ivd.common.security.InstitutionContext.require();
        com.zlth.ivd.common.security.InstitutionContext.requireUserActor();
        if(!Set.of("SYSTEM_ADMIN","PURCHASER","STOREKEEPER").contains(scope.role()))
            throw new BizException(40300,"无补货重算权限");
        LocalDateTime until=LocalDateTime.now();
        LocalDateTime since=until.minusDays(days);
        Long centralId=inventoryService.findActiveCentralWarehouse().getId();
        Map<Long,BigDecimal> stockMap=new HashMap<>();
        for(var batch:inventoryService.listAllBatches()) {
            if(batch.getReagentId()==null || batch.getStock()==null || batch.getStock().signum()<=0
                    || !Objects.equals(batch.getWarehouseId(),centralId) || batch.getUdiStatus()!=UdiStatusEnum.IN_STOCK
                    || !com.zlth.ivd.common.utils.MaterialExpiryPolicy.usable(batch.getMaterialType(),batch.getExpiryDate(),until.toLocalDate())
                    || (batch.getOpenExpiryDate()!=null && batch.getOpenExpiryDate().isBefore(until.toLocalDate())))continue;
            stockMap.merge(batch.getReagentId(),batch.getAvailableStock(),BigDecimal::add);
        }
        Map<Long,BigDecimal> consumed=new HashMap<>();
        for(var record:consumptionService.findConsumptionRecords()) {
            if(!"CONSUME".equals(record.getRecordType()) || record.getReagentId()==null
                    || record.getQuantity()==null || record.getQuantity().signum()<=0
                    || record.getOccurredAt()==null || record.getOccurredAt().isBefore(since)
                    || !record.getOccurredAt().isBefore(until))continue;
            consumed.merge(record.getReagentId(),record.getQuantity(),BigDecimal::add);
        }
        Map<Long,BigDecimal> committed=new HashMap<>();
        addOutstanding(committed,orderMapper.outstandingQuantities());
        addOutstanding(committed,purchasePlanMapper.committedQuantities());
        var run=new SmartReplenishRun();run.setAnalysisDays(days);run.setCreatedAt(until);run.setUpdatedAt(until);
        replenishRuns.insert(run);
        String batchNo="SR-"+until.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format(Locale.ROOT,"%04d",run.getId());
        run.setBatchNo(batchNo);replenishRuns.updateById(run);
        List<SmartReplenishSuggestion> persisted=new ArrayList<>();
        for(Long reagentId:new TreeSet<>(consumed.keySet())) {
            var catalog=catalogService.findById(reagentId);
            if(catalog==null || catalog.getApprovalStatus()!=com.zlth.ivd.common.enums.ApprovalStatusEnum.APPROVED)continue;
            BigDecimal daily=consumed.get(reagentId).divide(BigDecimal.valueOf(days),8,java.math.RoundingMode.HALF_UP);
            BigDecimal target=daily.multiply(BigDecimal.valueOf(30)).max(BigDecimal.TEN);
            BigDecimal current=stockMap.getOrDefault(reagentId,BigDecimal.ZERO);
            BigDecimal outstanding=committed.getOrDefault(reagentId,BigDecimal.ZERO);
            BigDecimal quantity=target.subtract(current).subtract(outstanding).setScale(0,java.math.RoundingMode.CEILING);
            if(quantity.signum()<=0)continue;
            if(quantity.compareTo(new BigDecimal("100000000"))>=0)throw new BizException(40001,"补货需求超出数量范围，请核对消耗证据");
            var item=new SmartReplenishSuggestion();item.setReagentId(reagentId);
            item.setReagentName(catalog.getGenericName());item.setSpecification(catalog.getSpecification());
            item.setManufacturer(catalog.getManufacturer());item.setUnit(catalog.getUnit());
            item.setSupplier(catalog.getDistributor());item.setAnalysisDays(days);item.setBatchNo(batchNo);
            item.setCurrentStock(current);item.setDailyConsumption(daily.setScale(2,java.math.RoundingMode.HALF_UP));
            item.setSafetyStock(daily.multiply(BigDecimal.TEN).setScale(0,java.math.RoundingMode.CEILING));
            item.setSuggestedQty(quantity);item.setStatus("PENDING");item.setCreatedAt(until);item.setUpdatedAt(until);
            suggestionMapper.insert(item);persisted.add(item);
        }
        persisted.sort(Comparator.comparing(SmartReplenishSuggestion::getSuggestedQty).reversed());
        log.info("补货重算完成: days={}, suggestions={}, batch={}",days,persisted.size(),batchNo);
        return suggestionViews(persisted);
    }

    private void validateAnalysisDays(int days) {
        if(days<1 || days>365)throw new BizException(40001,"分析天数须为1至365");
    }

    private void addOutstanding(Map<Long,BigDecimal> totals,List<Map<String,Object>> rows) {
        if(rows==null)return;
        for(var row:rows) {
            Object id=row.get("reagentId");Object quantity=row.get("quantity");
            if(id instanceof Number number && quantity!=null)
                totals.merge(number.longValue(),new BigDecimal(quantity.toString()),BigDecimal::add);
        }
    }

    private List<Map<String,Object>> suggestionViews(List<SmartReplenishSuggestion> rows) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(var row:rows) {
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("id",row.getId().toString());item.put("reagentId",row.getReagentId());
            item.put("reagentName",row.getReagentName());item.put("specification",row.getSpecification());
            item.put("manufacturer",row.getManufacturer());item.put("currentStock",row.getCurrentStock());
            item.put("safetyStock",row.getSafetyStock());item.put("dailyConsumption",row.getDailyConsumption());
            item.put("suggestedQty",row.getSuggestedQty());item.put("unit",row.getUnit());item.put("supplier",row.getSupplier());
            item.put("analysisDays",row.getAnalysisDays());item.put("batchNo",row.getBatchNo());item.put("status",row.getStatus());
            result.add(item);
        }
        return result;
    }

    @Override
    public List<PurchaseOrder> createOrdersFromReplenish(List<Map<String, Object>> input) {
        // Compatibility service path creates non-receivable PLAN_REQUIRED drafts only.
        // Public callers use PurchasePlanService and approved plan conversion.
        if(input==null || input.isEmpty() || input.size()>500)throw new BizException(40001,"请选择1至500条补货建议");
        record Conversion(Long reagentId,Integer supplierId,BigDecimal quantity){}
        Map<Long,Conversion> conversions=new TreeMap<>();
        for(var row:input) {
            if(row==null)throw new BizException(40001,"补货明细不能为空");
            try {
                Long id=Long.valueOf(Objects.toString(row.get("id"),""));
                Long reagent=Long.valueOf(Objects.toString(row.get("reagentId"),""));
                var quantity=new BigDecimal(Objects.toString(row.get("suggestedQty"),""));
                requirePositive(quantity,"补货数量");
                if(id<=0 || reagent<=0 || quantity.compareTo(new BigDecimal("100000000"))>=0)
                    throw new BizException(40001,"补货编号或数量无效");
                Integer supplier=row.get("supplierId")==null?null:Integer.valueOf(row.get("supplierId").toString());
                if(conversions.putIfAbsent(id,new Conversion(reagent,supplier,quantity))!=null)
                    throw new BizException(40001,"同一补货建议不能重复选择");
            }catch(NumberFormatException invalid){throw new BizException(40001,"补货参数格式无效");}
        }
        List<CreateManualOrderRequest.ManualOrderItem> rows=new ArrayList<>();
        for(var entry:conversions.entrySet()) {
            var suggestion=suggestionMapper.selectOne(new LambdaQueryWrapper<SmartReplenishSuggestion>()
                    .eq(SmartReplenishSuggestion::getId,entry.getKey()).last("FOR UPDATE"));
            if(suggestion==null)throw new BizException(40400,"补货建议不存在或不可访问");
            if(!"PENDING".equals(suggestion.getStatus()) || !Objects.equals(suggestion.getReagentId(),entry.getValue().reagentId()))
                throw new BizException(40900,"补货来源已变化或试剂不匹配");
            var catalog=requireApprovedReceiptCatalog(suggestion.getReagentId());
            if(catalog.getPricePerUnit()==null || catalog.getPricePerUnit().signum()<0)
                throw new BizException(40001,"补货目录未维护非负单价");
            var supplier=resolvePurchaseSupplier(entry.getValue().supplierId(),catalog.getDistributor());
            var item=new CreateManualOrderRequest.ManualOrderItem();item.setReagentId(suggestion.getReagentId());
            item.setSupplierId(Math.toIntExact(supplier.getId()));item.setQuantity(entry.getValue().quantity());rows.add(item);
        }
        var request=new CreateManualOrderRequest();request.setItems(rows);request.setSettlementMode("调拨结算");
        var result=createManualOrder(request);
        for(var order:result){order.setSourceType("SMART_REPLENISH");orderMapper.updateById(order);}
        for(Long id:conversions.keySet()) {
            var changed=new SmartReplenishSuggestion();changed.setStatus("GENERATED");changed.setUpdatedAt(LocalDateTime.now());
            if(suggestionMapper.update(changed,new LambdaQueryWrapper<SmartReplenishSuggestion>()
                    .eq(SmartReplenishSuggestion::getId,id).eq(SmartReplenishSuggestion::getStatus,"PENDING"))!=1)
                throw new BizException(40900,"补货状态转换冲突");
        }
        return result;
    }

    private Iterator<String> reservePurchaseNumbers(LocalDate orderDate, int count) {
        if (count < 1 || count > 9999) throw new BizException(40001, "本次采购单数量超出发号范围");
        String day = orderDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        orderMapper.advanceNumberSequence(day, count);
        int last = orderMapper.currentNumberSequence(day);
        if (last > 9999) throw new BizException(40900, "当前机构今日采购单号已用尽");
        return java.util.stream.IntStream.rangeClosed(last - count + 1, last)
                .mapToObj(number -> String.format(Locale.ROOT, "PO-%s-%04d", day, number)).iterator();
    }

    private LocalDate parsePurchaseDate(String value,LocalDate fallback) {
        if(value==null || value.isBlank())return fallback;
        try{return LocalDate.parse(value);}
        catch(java.time.format.DateTimeParseException invalid){throw new BizException(40001,"采购日期格式须为YYYY-MM-DD");}
    }

    private Supplier requireActiveSupplier(Integer supplierId) {
        if (supplierId == null || supplierId <= 0) throw new BizException(40001, "请选择启用的供应商");
        Supplier supplier = supplierService.findById(supplierId.longValue());
        if (supplier == null) throw new BizException(40300, "供应商不存在或不可访问");
        if (!Boolean.TRUE.equals(supplier.getIsActive())) throw new BizException(40001, "供应商已停用");
        supplierService.requireProcurementEligible(supplierId.longValue());
        return supplier;
    }

    private Supplier resolvePurchaseSupplier(Integer supplierId, String distributor) {
        if (supplierId != null) return requireActiveSupplier(supplierId);
        if (distributor != null && !distributor.isBlank()) {
            String name = distributor.trim();
            List<Supplier> matches = supplierService.findAll().stream()
                    .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                    .filter(s -> name.equals(Objects.toString(s.getName(), "").trim())
                            || name.equals(Objects.toString(s.getShortName(), "").trim()))
                    .toList();
            if (matches.size() == 1) return requireActiveSupplier(Math.toIntExact(matches.get(0).getId()));
        }
        throw new BizException(40001, "未匹配到唯一启用的机构供应商，请核对目录配送商");
    }

    // ── 私有辅助方法 ──

    private String resolveReceiptUdiDi(ReagentCatalog catalog, String supplied) {
        String expected = normalizeReceiptUdiDi(catalog.getUdiDi());
        String actual = normalizeReceiptUdiDi(supplied);
        if (actual != null && !actual.equals(expected))
            throw new BizException(40001, expected == null
                    ? "试剂目录未维护可核对的 UDI-DI，请先完善目录"
                    : "来货 UDI-DI 与采购试剂目录不匹配");
        return expected;
    }

    private String normalizeReceiptUdiDi(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private ReagentCatalog requireApprovedReceiptCatalog(Long reagentId) {
        ReagentCatalog catalog = catalogService.lockForBusiness(reagentId);
        if (catalog == null) throw new BizException(40400, "验收试剂目录不存在");
        if (catalog.getApprovalStatus() != com.zlth.ivd.common.enums.ApprovalStatusEnum.APPROVED
                || catalog.getMaterialType() == null)
            throw new BizException(40001, "验收试剂目录须已批准并维护物料类型");
        return catalog;
    }

    private String validateReceipt(String batchNo,LocalDate expiry,LocalDate production,MaterialTypeEnum type) {
        if (type == null) throw new BizException(40001, "物料类型未维护");
        boolean tracked = type.entersUdiStateMachine();
        if(batchNo != null && batchNo.length()>64)throw new BizException(40001,"验收批号不得超过64字");
        if (tracked && (batchNo == null || batchNo.isBlank())) throw new BizException(40001, "该物料验收必须填写批号");
        if ((tracked && expiry == null) || (expiry != null && expiry.isBefore(LocalDate.now())))
            throw new BizException(40001,"请填写未过期的有效期");
        if(production!=null && (production.isAfter(LocalDate.now()) || (expiry != null && production.isAfter(expiry))))
            throw new BizException(40001,"生产日期不得晚于今天或有效期");
        if (batchNo != null && !batchNo.isBlank()) return batchNo.strip();
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        orderMapper.advanceVirtualBatch(day);
        int number = orderMapper.currentVirtualBatch(day);
        if (number > 999) throw new BizException(40900, "当前机构今日虚拟批号已用尽");
        return String.format(Locale.ROOT, "BATCH-CONS-%s-%03d", day, number);
    }
    private void authorizeDepartment(Long departmentId) {
        com.zlth.ivd.common.security.InstitutionContext.current()
                .filter(scope->Set.of("LAB_TECH","LAB_DOCTOR","LAB_DIRECTOR").contains(scope.role()))
                .ifPresent(scope->{if(scope.departmentId()==null || !Objects.equals(scope.departmentId(),departmentId))
                    throw new BizException(40300,"仅允许操作授权科室的申领或签收");});
    }
    private void authorizeRequisition(Long id) {
        reqMapper.lockById(id);var req=reqMapper.selectById(id);
        if(req==null)throw new BizException(40400,"申领单不存在");authorizeDepartment(req.getFromDeptId());
    }
    @Override
    public PurchaseRequisition lockRequisitionForSettlement(Long id) {
        authorizeRequisition(id);return reqMapper.selectById(id);
    }
    @Override
    public List<PurchaseRequisition> pendingReceipts() {
        return reqMapper.selectList(new LambdaQueryWrapper<PurchaseRequisition>()
                .in(PurchaseRequisition::getStatus,RequisitionStatusEnum.SHIPPED,RequisitionStatusEnum.PARTIAL_FULFILLED)
                .orderByAsc(PurchaseRequisition::getId));
    }
    @Override
    public List<ConsumptionRecord> receivedTransfers(Long id) {
        authorizeRequisition(id);
        var items=reqItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequisitionItem>().eq(PurchaseRequisitionItem::getReqId,id));
        var itemById=items.stream().collect(Collectors.toMap(PurchaseRequisitionItem::getId,item->item));
        var result=new ArrayList<ConsumptionRecord>();
        for(var movement:consumptionService.findReceivedTransferRecords(id)) {
            var item=itemById.get(movement.getRequisitionItemId());
            if(item==null && movement.getSettlementMode()==null)continue;
            if(item==null || !Objects.equals(item.getReagentId(),movement.getReagentId()))
                throw new BizException(40900,"签收流水与申领明细不一致");
            result.add(movement);
        }
        return result;
    }

    /** 批量查询科室名称 */
    private Map<Long, String> batchGetDeptNames(Set<Long> deptIds) {
        if (deptIds.isEmpty()) return Collections.emptyMap();
        List<SysDept> depts = orgService.findDepts(deptIds);
        return depts.stream().collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));
    }

    /** 批量查询供应商名称 */
    private Map<Integer, String> batchGetSupplierNames(Set<Integer> supplierIds) {
        if (supplierIds.isEmpty()) return Collections.emptyMap();
        List<Supplier> suppliers = supplierService.findByIds(
                supplierIds.stream().map(Long::valueOf).collect(Collectors.toList()));
        return suppliers.stream()
                .collect(Collectors.toMap(s -> s.getId().intValue(), Supplier::getName, (a, b) -> a));
    }

    /** 批量查询用户真实姓名 */
    private Map<Long, String> batchGetUserNames(Set<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        List<SysUser> users = orgService.findUsers(userIds);
        return users.stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
    }
}
