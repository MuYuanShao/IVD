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
        if (quantity == null || quantity.signum() <= 0)
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
            vo.setFromDeptId(req.getFromDeptId() != null ? req.getFromDeptId().intValue() : null);
            vo.setDeptName(deptNameMap.getOrDefault(req.getFromDeptId(), "科室#" + req.getFromDeptId()));
            vo.setToWarehouseId(req.getToWarehouseId() != null ? req.getToWarehouseId().intValue() : null);
            vo.setApplicantId(req.getApplicantId() != null ? req.getApplicantId().intValue() : null);
            vo.setApplicantName(userNameMap.getOrDefault(req.getApplicantId(), "用户#" + (req.getApplicantId() != null ? req.getApplicantId() : "-")));
            vo.setUrgency(req.getUrgency()); vo.setStatus(req.getStatus());
            vo.setReviewerId(req.getReviewerId() != null ? req.getReviewerId().intValue() : null); vo.setReviewTime(req.getReviewTime());
            vo.setReviewComment(req.getReviewComment()); vo.setRemark(req.getRemark());
            vo.setCreatedAt(req.getCreatedAt()); vo.setUpdatedAt(req.getUpdatedAt());

            List<PurchaseRequisitionItem> items = itemsMap.getOrDefault(req.getId(), Collections.emptyList());
            int totalQty = items.stream().mapToInt(i -> i.getQtyRequested() != null ? i.getQtyRequested().intValue() : 0).sum();
            BigDecimal totalAmount = items.stream().map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            int shippedQty = items.stream().mapToInt(i -> i.getQtyFulfilled() != null ? i.getQtyFulfilled().intValue() : 0).sum();
            int shortageQty = items.stream().mapToInt(i -> i.getQtyShortage() != null ? i.getQtyShortage().intValue() : 0).sum();
            vo.setTotalQty(totalQty); vo.setTotalAmount(totalAmount);
            vo.setShippedQty(shippedQty); vo.setShortageQty(shortageQty);
            records.add(vo);
        }
        return new PageVO<>(p.getTotal(), page, pageSize, records);
    }

    @Override
    public PurchaseRequisition createRequisition(PurchaseRequisition req) {
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
                if (item.getQtyShortage() == null) item.setQtyShortage(BigDecimal.ZERO);
                if (item.getQtyFulfilled() == null) item.setQtyFulfilled(BigDecimal.ZERO);
                if (item.getStockOnHand() == null) item.setStockOnHand(BigDecimal.ZERO);

                // 从目录填充试剂信息
                if (item.getReagentId() != null) {
                    ReagentCatalog cat = catalogMap.get(item.getReagentId().longValue());
                    if (cat != null) {
                        if (item.getSpecification() == null) item.setSpecification(cat.getSpecification());
                        if (item.getUnitPrice() == null) item.setUnitPrice(cat.getPricePerUnit());
                        item.setReagentName(cat.getGenericName());
                        item.setGenericName(cat.getGenericName());
                        item.setManufacturer(cat.getManufacturer());
                        item.setUnit(cat.getUnit());
                    }
                }
                if (item.getAmount() == null && item.getQtyRequested() != null && item.getUnitPrice() != null) {
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
        vo.setFromDeptId(req.getFromDeptId() != null ? req.getFromDeptId().intValue() : null);
        vo.setDeptName(deptName);
        vo.setToWarehouseId(req.getToWarehouseId() != null ? req.getToWarehouseId().intValue() : null);
        vo.setApplicantId(req.getApplicantId() != null ? req.getApplicantId().intValue() : null);
        vo.setApplicantName(applicantName);
        vo.setUrgency(req.getUrgency()); vo.setStatus(req.getStatus());
        vo.setReviewerId(req.getReviewerId() != null ? req.getReviewerId().intValue() : null); vo.setReviewTime(req.getReviewTime());
        vo.setReviewComment(req.getReviewComment()); vo.setRemark(req.getRemark());
        vo.setCreatedAt(req.getCreatedAt()); vo.setUpdatedAt(req.getUpdatedAt());

        int totalQty = items.stream().mapToInt(i -> i.getQtyRequested() != null ? i.getQtyRequested().intValue() : 0).sum();
        BigDecimal totalAmount = items.stream().map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        int shippedQty = items.stream().mapToInt(i -> i.getQtyFulfilled() != null ? i.getQtyFulfilled().intValue() : 0).sum();
        int shortageQty = items.stream().mapToInt(i -> i.getQtyShortage() != null ? i.getQtyShortage().intValue() : 0).sum();
        vo.setTotalQty(totalQty); vo.setTotalAmount(totalAmount);
        vo.setShippedQty(shippedQty); vo.setShortageQty(shortageQty);
        vo.setItems(items);

        return vo;
    }

    @Override
    public PurchaseRequisitionVO reviewRequisition(Long id, String action, String comment) {
        PurchaseRequisition req = reqMapper.selectById(id);
        if (req == null) throw new BizException(40400, "申领单不存在");
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
        PurchaseRequisition req = reqMapper.selectById(id);
        if (req == null) throw new BizException(40400, "申领单不存在");
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
        PurchaseRequisition req = reqMapper.selectById(id);
        if (req == null) throw new BizException(40400, "申领单不存在");
        if (req.getStatus() != RequisitionStatusEnum.DRAFT) {
            throw new BizException(40000, "仅草稿状态的申领单可作废");
        }
        req.setStatus(RequisitionStatusEnum.CANCELLED);
        req.setReviewComment(reason != null ? reason : "用户主动作废");
        reqMapper.updateById(req);
        log.info("申领单已作废: reqNo={}, reason={}", req.getReqNo(), reason);
        return getRequisitionDetail(id);
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
        reqMapper.lockById(requisitionId);
        PurchaseRequisition req = reqMapper.selectById(requisitionId);
        if (req == null) throw new BizException(40400, "申领单不存在");
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

        for (PickAndShipRequest.PickItem pickItem : request.getItems()) {
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

            // 1. 扣减源仓库库存（乐观锁重试）— 一级库出库
            ReagentBatch sourceBatch = deductSourceBatchStock(pickItem.getSourceBatchId(), reqItem.getReagentId(), pickQty);

            // 2. 目标仓库不在此处入库！拣货发货后物品处于"在途"状态，
            //    待科室签收（receiveRequisition）时才真正增加二级库库存

            // 3. 写入 TRANSFER consumption_record 审计追踪
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
        reqMapper.lockById(id);
        PurchaseRequisition req = reqMapper.selectById(id);
        if (req == null) throw new BizException(40400, "申领单不存在");
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
                for (ConsumptionRecord tr : transferRecords) {
                    requirePositive(tr.getQuantity(), "发货流水数量");
                    // 每笔 transfer record 可能有不同的目标仓库，逐笔解析
                    Long trTargetWarehouseId = tr.getWarehouseId() != null
                            ? tr.getWarehouseId() : defaultTargetWarehouseId;
                    // 从源仓库查找对应的批次（即使库存已清零，批次记录仍存在）
                    ReagentBatch sourceBatch = inventoryService.findBatch(tr.getReagentId(), tr.getBatchNo(), tr.getFromWarehouseId());
                    if (sourceBatch == null) {
                        throw new BizException(40001, "签收入库源批次不存在，请核对发货流水");
                    }
                    Long targetBatchId = upsertTargetBatch(sourceBatch, trTargetWarehouseId, tr.getQuantity());
                    consumptionService.confirmTransferReceipt(tr.getId(), targetBatchId);
                    log.info("签收入库: batchNo={}, qty={}, targetWarehouseId={}, targetBatchId={}",
                            tr.getBatchNo(), tr.getQuantity(), trTargetWarehouseId, targetBatchId);

                    // 唯一码全流程流转：签收时转移对应数量的唯一码到目标批次
                    if (targetBatchId != null && tr.getQuantity() != null
                            && tr.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                        int codeCount = tr.getQuantity().intValue();
                        uniqueCodeService.transferUniqueCodes(
                                sourceBatch.getId(), targetBatchId, codeCount);
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

    /**
     * 扣减源仓库库存（乐观锁重试，最多 3 次）
     */
    private ReagentBatch deductSourceBatchStock(Long batchId, Long reagentId, BigDecimal qty) {
        requirePositive(qty, "拣货数量");
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            ReagentBatch sourceBatch = inventoryService.getBatch(batchId);
            if (sourceBatch == null) throw new BizException(40400, "批次不存在");
            if (!Objects.equals(reagentId, sourceBatch.getReagentId()))
                throw new BizException(40001, "源批次试剂与申领明细不一致");
            if (sourceBatch.getUdiStatus() != UdiStatusEnum.IN_STOCK
                    || sourceBatch.getExpiryDate() == null || sourceBatch.getExpiryDate().isBefore(LocalDate.now()))
                throw new BizException(40001, "源批次不可用或已过期");
            if (sourceBatch.getStock().compareTo(qty) < 0) throw new BizException(40001, "库存不足");

            sourceBatch.setStock(sourceBatch.getStock().subtract(qty));
            if (sourceBatch.getStock().compareTo(BigDecimal.ZERO) <= 0) {
                sourceBatch.setUdiStatus(UdiStatusEnum.TRANSFERRED);
            }

            boolean updated = inventoryService.updateBatch(sourceBatch);
            if (updated) return sourceBatch;
            if (i == maxRetries - 1) throw new BizException(50000, "拣货扣减失败：超过最大重试次数");
            log.warn("拣货扣减乐观锁冲突，重试 ({}/{})", i + 1, maxRetries);
        }
        throw new BizException(50000, "拣货扣减失败：超过最大重试次数");
    }

    /**
     * 目标仓库累加/新建批次（乐观锁重试累加分支，最多 3 次）
     */
    /**
     * 签收入库：将源批次库存拷贝/合并到目标仓库，返回目标批次 ID（供唯一码转移）
     */
    private Long upsertTargetBatch(ReagentBatch sourceBatch, Long targetWarehouseId, BigDecimal qty) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            ReagentBatch targetBatch = inventoryService.findBatch(sourceBatch.getReagentId(), sourceBatch.getBatchNo(), targetWarehouseId);

            if (targetBatch != null) {
                targetBatch.setStock(targetBatch.getStock().add(qty));
                boolean updated = inventoryService.updateBatch(targetBatch);
                if (updated) return targetBatch.getId();
                if (i == maxRetries - 1) throw new BizException(50000, "拣货入库失败：超过最大重试次数");
                log.warn("拣货入库乐观锁冲突，重试 ({}/{})", i + 1, maxRetries);
            } else {
                ReagentBatch newBatch = new ReagentBatch();
                newBatch.setId(IdGenerator.nextId());
                newBatch.setBatchNo(sourceBatch.getBatchNo());
                newBatch.setReagentId(sourceBatch.getReagentId());
                newBatch.setMaterialType(sourceBatch.getMaterialType());
                newBatch.setUdiDi(sourceBatch.getUdiDi());
                newBatch.setUdiPi(sourceBatch.getUdiPi());
                newBatch.setManufactureDate(sourceBatch.getManufactureDate());
                newBatch.setExpiryDate(sourceBatch.getExpiryDate());
                newBatch.setStock(qty);
                newBatch.setUnit(sourceBatch.getUnit());
                newBatch.setUnitPrice(sourceBatch.getUnitPrice());
                newBatch.setAvgCost(sourceBatch.getAvgCost());
                newBatch.setUdiStatus(UdiStatusEnum.IN_STOCK);
                newBatch.setWarehouseId(targetWarehouseId);
                newBatch.setLocationCode(sourceBatch.getLocationCode());
                newBatch.setCreatedAt(LocalDateTime.now());
                newBatch.setUpdatedAt(LocalDateTime.now());
                inventoryService.createBatch(newBatch);
                return newBatch.getId();
            }
        }
        throw new BizException(50000, "拣货入库失败：超过最大重试次数");
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
        record.setOperatorId(SecurityUtils.getCurrentUserId().intValue());
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
        qw.orderByDesc(ShortageOrder::getCreatedAt);
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
            vo.setFromDeptId(so.getFromDeptId() != null ? so.getFromDeptId().intValue() : null);
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
                int totalShortage = items.stream().mapToInt(i -> i.getQtyShortage() != null ? i.getQtyShortage().intValue() : 0).sum();
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
        vo.setFromDeptId(so.getFromDeptId() != null ? so.getFromDeptId().intValue() : null);
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
            int totalShortage = items.stream().mapToInt(i -> i.getQtyShortage() != null ? i.getQtyShortage().intValue() : 0).sum();
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
        Long targetWarehouseId = req.getToWarehouseId() != null ? req.getToWarehouseId() : 1L;

        List<PurchaseRequisitionItem> shortItems = new ArrayList<>();
        for (PurchaseRequisitionItem item : reqItems) {
            // BUGFIX 1: 从 reagent_batch 查询真实库存，而非使用申领创建时初始化的 ZERO
            BigDecimal stock = BigDecimal.ZERO;
            if (item.getReagentId() != null) {
                List<ReagentBatch> batches = inventoryService.findInStockBatches(
                        item.getReagentId().longValue(), targetWarehouseId);
                if (batches != null && !batches.isEmpty()) {
                    stock = batches.stream()
                        .map(b -> b.getStock() != null ? b.getStock() : BigDecimal.ZERO)
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
        qw.orderByDesc(PurchaseOrder::getCreatedAt);
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
        String no = "PO-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
                String.format("%04d", System.currentTimeMillis() % 10000);
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNo(no);
        po.setSupplierId(supplierId);
        po.setSourceType("SHORTAGE");
        po.setSettlementMode(settlementMode != null ? settlementMode : "调拨结算");
        po.setSourceId(shortageId);
        po.setStatus(PurchaseOrderStatusEnum.DRAFT);
        po.setOrderDate(LocalDate.now());
        if (expectedDeliveryDate != null && !expectedDeliveryDate.isEmpty()) {
            po.setExpectedDeliveryDate(LocalDate.parse(expectedDeliveryDate));
        }
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());
        orderMapper.insert(po);

        // BUGFIX 2: 从缺货明细复制到采购订单明细
        List<ShortageOrderItem> shItems = shortageItemMapper.selectList(
                new LambdaQueryWrapper<ShortageOrderItem>()
                        .eq(ShortageOrderItem::getShortageOrderId, shortageId));
        if (shItems != null && !shItems.isEmpty()) {
            int lineNo = 1;
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (ShortageOrderItem si : shItems) {
                PurchaseOrderItem poi = new PurchaseOrderItem();
                poi.setPoId(po.getId());
                poi.setLineNo(lineNo++);
                poi.setReagentId(si.getReagentId());
                poi.setSupplierId(supplierId);
                poi.setMaterialType("REAGENT");
                poi.setQtyOrdered(si.getQtyShortage() != null ? si.getQtyShortage() : BigDecimal.ONE);
                BigDecimal unitPrice = si.getEstUnitPrice() != null ? si.getEstUnitPrice() : BigDecimal.ZERO;
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
        ShortageOrder so = shortageMapper.selectById(shortageId);
        if (so != null) {
            so.setPoId(po.getId());
            so.setStatus(ShortageStatusEnum.PURCHASING);
            shortageMapper.updateById(so);
        }
        log.info("采购订单创建: poNo={}, shortageId={}", no, shortageId);
        return po;
    }

    @Override
    public List<PurchaseOrder> createManualOrder(CreateManualOrderRequest request) {
        List<CreateManualOrderRequest.ManualOrderItem> allItems = request.getItems();
        if (allItems == null || allItems.isEmpty()) {
            throw new BizException(40000, "采购明细不能为空");
        }

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

        // 1. 从试剂目录的 distributor 字段解析缺失的 supplierId
        //    ReagentCatalog.distributor 存的是配送商名称，需要反查 Supplier 表得到 ID
        Map<String, Integer> supplierNameToId = new HashMap<>();
        List<Supplier> allSuppliers = supplierService.findAll();
        for (Supplier s : allSuppliers) {
            if (s.getName() != null) supplierNameToId.put(s.getName().trim(), s.getId().intValue());
            if (s.getShortName() != null) supplierNameToId.put(s.getShortName().trim(), s.getId().intValue());
        }

        for (CreateManualOrderRequest.ManualOrderItem item : allItems) {
            if (item.getSupplierId() == null && item.getReagentId() != null) {
                ReagentCatalog cat = allCatalogMap.get(item.getReagentId());
                if (cat != null && cat.getDistributor() != null && !cat.getDistributor().isBlank()) {
                    String dist = cat.getDistributor().trim();
                    // 1) 精确匹配
                    Integer resolvedId = supplierNameToId.get(dist);
                    // 2) 模糊匹配：供应商名包含配送商名 或 配送商名包含供应商名
                    if (resolvedId == null) {
                        for (Map.Entry<String, Integer> entry : supplierNameToId.entrySet()) {
                            if (entry.getKey().contains(dist) || dist.contains(entry.getKey())) {
                                resolvedId = entry.getValue();
                                break;
                            }
                        }
                    }
                    if (resolvedId != null) {
                        item.setSupplierId(resolvedId);
                        log.info("试剂 {} 配送商\"{}\" → 供应商ID={}", item.getReagentId(), dist, resolvedId);
                    } else {
                        log.warn("试剂 {} 配送商\"{}\" 未匹配到任何供应商，supplierId 保持为 null", item.getReagentId(), dist);
                    }
                }
            }
        }

        // 2. 按供应商分组（null 归入 key=0，统一成一个"未指定供应商"的 PO）
        Map<Integer, List<CreateManualOrderRequest.ManualOrderItem>> grouped = new LinkedHashMap<>();
        for (CreateManualOrderRequest.ManualOrderItem item : allItems) {
            Integer supplierKey = item.getSupplierId() != null ? item.getSupplierId() : 0;
            grouped.computeIfAbsent(supplierKey, k -> new ArrayList<>()).add(item);
        }

        List<PurchaseOrder> results = new ArrayList<>();
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int baseSeq = (int) (System.currentTimeMillis() % 10000);

        for (Map.Entry<Integer, List<CreateManualOrderRequest.ManualOrderItem>> entry : grouped.entrySet()) {
            Integer supplierId = entry.getKey() == 0 ? null : entry.getKey();
            List<CreateManualOrderRequest.ManualOrderItem> groupItems = entry.getValue();

            // 1. 先插入 PO 头（获取雪花 ID）
            int seq = (baseSeq + results.size()) % 10000;
            String no = "PO-" + datePart + "-" + String.format("%04d", seq);
            PurchaseOrder po = new PurchaseOrder();
            po.setPoNo(no);
            po.setSourceType("MANUAL");
            po.setSupplierId(supplierId);
            po.setSettlementMode(request.getSettlementMode() != null ? request.getSettlementMode() : "调拨结算");
            po.setStatus(PurchaseOrderStatusEnum.DRAFT);
            po.setOrderDate(LocalDate.now());
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
                poi.setMaterialType("REAGENT");
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
        // BUGFIX 10: 先取出订单明细并校验归属
        PurchaseOrderItem item = orderItemMapper.selectById(request.getItemId());
        if (item == null) throw new BizException(40400, "采购订单明细不存在");
        ReagentCatalog receivedCatalog=catalogService.findById(item.getReagentId());
        if(receivedCatalog==null) throw new BizException(40400,"试剂目录不存在");
        if (request.getOrderId() != null && !request.getOrderId().equals(item.getPoId())) {
            throw new BizException(40000, "订单明细不属于该采购订单");
        }

        BigDecimal qty = request.getQtyReceived() != null ? request.getQtyReceived() : BigDecimal.ONE;

        // BUGFIX 8+9: 乐观锁更新采购明细已收数量（独立重试循环，不掺杂批次操作）
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            PurchaseOrderItem freshItem = orderItemMapper.selectById(request.getItemId());
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
            // BUGFIX 7: 按 warehouseId + batchNo 限定范围
            Long warehouseId = request.getWarehouseId() != null ? request.getWarehouseId() : 1L;
            Long savedBatchId = null;
            int batchRetries = 3;
            for (int attempt = 0; attempt < batchRetries; attempt++) {
                ReagentBatch existing = inventoryService.findBatch(item.getReagentId(), request.getBatchNo(), warehouseId);
                if (existing != null) {
                    // 追加库存
                    existing.setStock(existing.getStock().add(qty));
                    existing.setUdiStatus(UdiStatusEnum.IN_STOCK);
                    boolean batchUpdated = inventoryService.updateBatch(existing);
                    if (batchUpdated) {
                        savedBatchId = existing.getId();
                        log.info("批次库存追加: batchNo={}, addQty={}, newStock={}", request.getBatchNo(), qty, existing.getStock());
                        break;
                    }
                    if (attempt == batchRetries - 1) {
                        throw new BizException(50000, "验收批次更新失败：超过最大重试次数");
                    }
                    log.warn("验收批次更新乐观锁冲突，重试 ({}/{})", attempt + 1, batchRetries);
                } else {
                    // 新批次
                    ReagentBatch batch = new ReagentBatch();
                    batch.setId(IdGenerator.nextId());
                    batch.setBatchNo(request.getBatchNo());
                    batch.setReagentId(item.getReagentId().longValue());
                    batch.setMaterialType(receivedCatalog.getMaterialType());
                    batch.setUnit(receivedCatalog.getUnit());
                    batch.setUdiDi(request.getUdiDi());
                    batch.setManufactureDate(request.getProductionDate());
                    batch.setExpiryDate(request.getExpiryDate());
                    batch.setStock(qty);
                    batch.setUnitPrice(item.getUnitPrice());
                    batch.setUdiStatus(UdiStatusEnum.IN_STOCK);
                    batch.setWarehouseId(warehouseId);
                    batch.setCreatedAt(LocalDateTime.now());
                    batch.setUpdatedAt(LocalDateTime.now());
                    inventoryService.createBatch(batch);
                    savedBatchId = batch.getId();
                    log.info("新批次入库: batchNo={}, reagentId={}, qty={}", request.getBatchNo(), item.getReagentId(), qty);
                    break;
                }
            }

            // BUGFIX 6: 写入 INBOUND consumption_record 审计追踪
            ConsumptionRecord cr = new ConsumptionRecord();
            cr.setId(IdGenerator.nextId());
            cr.setRecordType(ConsumptionRecordTypeEnum.INBOUND.getCode());
            cr.setBatchNo(request.getBatchNo());
            cr.setBatchId(savedBatchId);
            cr.setPurchaseOrderItemId(item.getId());
            cr.setUnit(receivedCatalog.getUnit());
            cr.setReagentId(item.getReagentId().longValue());
            cr.setMaterialType("REAGENT");
            cr.setQuantity(qty);
            cr.setUnitPrice(item.getUnitPrice());
            cr.setAmount(item.getUnitPrice() != null ? item.getUnitPrice().multiply(qty) : null);
            cr.setWarehouseId(warehouseId);
            cr.setOperatorId(SecurityUtils.getCurrentUserId().intValue());
            cr.setOccurredAt(LocalDateTime.now());
            cr.setCreatedAt(LocalDateTime.now());
            consumptionService.appendRecord(cr);

            // 按入库数量生成唯一码（仅当试剂目录开启唯一码管控时）
            ReagentCatalog catalog = catalogService.findById(item.getReagentId().longValue());
            if (catalog != null && catalog.getEnableUniqueCode() != null && catalog.getEnableUniqueCode() == 1) {
                int codeCount = qty.intValue();
                uniqueCodeService.generateAndSave(codeCount, savedBatchId,
                        item.getReagentId().longValue(), request.getBatchNo(), request.getUdiDi());
            }
        }

        // 更新采购订单状态
        updateOrderStatus(request.getOrderId());

        log.info("验收收货完成: orderId={}, itemId={}, qty={}, batchNo={}",
                request.getOrderId(), request.getItemId(), qty, request.getBatchNo());
    }

    /** 根据所有明细的收货情况更新订单状态 */
    private void updateOrderStatus(Long orderId) {
        PurchaseOrder po = orderMapper.selectById(orderId);
        if (po == null) return;

        List<PurchaseOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getPoId, orderId));

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
        }
        Integer supplierId = request.getSupplierId() != null ? request.getSupplierId() : 1;
        Long warehouseId = request.getWarehouseId() != null ? request.getWarehouseId() : 1L;

        // 1. 自动创建 MANUAL 采购订单
        PurchaseOrder po = new PurchaseOrder();
        po.setId(IdGenerator.nextId());
        po.setSupplierId(supplierId);
        po.setSourceType("MANUAL");
        po.setAcceptanceMethod("MANUAL");
        po.setSettlementMode("调拨结算");
        po.setStatus(PurchaseOrderStatusEnum.PARTIAL_RECEIVED);
        po.setOrderDate(LocalDate.now());
        po.setExpectedDeliveryDate(LocalDate.now());
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());

        String datePart = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seq = String.format("%04d", (System.currentTimeMillis() % 10000));
        po.setPoNo("PO-" + datePart + "-" + seq);
        orderMapper.insert(po);

        // 2. 创建订单明细 + 直接入库
        int successCount = 0;
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
                unitPrice = catalog != null ? catalog.getPricePerUnit() : BigDecimal.ZERO;
            }

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
            poi.setQtyReceived(qty);  // 直接全额收货
            poi.setCreatedAt(LocalDateTime.now());
            orderItemMapper.insert(poi);

            totalAmount = totalAmount.add(unitPrice.multiply(qty));
            totalReceivedQty = totalReceivedQty.add(qty);

            // 2.2 直接创建/追加批次库存
            if (item.getBatchNo() != null && !item.getBatchNo().isBlank()) {
                Long savedBatchId;
                // BUGFIX: 按 warehouseId + batchNo 限定范围（同 Bug 7）
                ReagentBatch existing = inventoryService.findBatch(item.getReagentId(), item.getBatchNo(), warehouseId);
                if (existing != null) {
                    existing.setStock(existing.getStock().add(qty));
                    existing.setUdiStatus(UdiStatusEnum.IN_STOCK);
                    inventoryService.updateBatch(existing);
                    savedBatchId = existing.getId();
                } else {
                    ReagentBatch batch = new ReagentBatch();
                    batch.setId(IdGenerator.nextId());
                    batch.setBatchNo(item.getBatchNo());
                    batch.setReagentId(item.getReagentId().longValue());
                    batch.setMaterialType(MaterialTypeEnum.REAGENT);
                    batch.setManufactureDate(item.getProductionDate());
                    batch.setExpiryDate(item.getExpiryDate());
                    batch.setStock(qty);
                    batch.setUnitPrice(unitPrice);
                    batch.setUdiDi(item.getUdiDi());
                    batch.setUdiStatus(UdiStatusEnum.IN_STOCK);
                    batch.setWarehouseId(warehouseId);
                    batch.setCreatedAt(LocalDateTime.now());
                    batch.setUpdatedAt(LocalDateTime.now());
                    inventoryService.createBatch(batch);
                    savedBatchId = batch.getId();
                }

                // BUGFIX: 写入 INBOUND consumption_record 审计追踪（同 Bug 6）
                ConsumptionRecord cr = new ConsumptionRecord();
                cr.setId(IdGenerator.nextId());
                cr.setRecordType(ConsumptionRecordTypeEnum.INBOUND.getCode());
                cr.setBatchNo(item.getBatchNo());
                cr.setReagentId(item.getReagentId().longValue());
                cr.setMaterialType("REAGENT");
                cr.setQuantity(qty);
                cr.setUnitPrice(unitPrice);
                cr.setAmount(unitPrice.multiply(qty));
                cr.setWarehouseId(warehouseId);
                cr.setOperatorId(SecurityUtils.getCurrentUserId().intValue());
                cr.setOccurredAt(LocalDateTime.now());
                cr.setCreatedAt(LocalDateTime.now());
                consumptionService.appendRecord(cr);

                // 按入库数量生成唯一码（仅当试剂目录开启唯一码管控时）
                ReagentCatalog catForCode = catalogService.findById(item.getReagentId().longValue());
                if (catForCode != null && catForCode.getEnableUniqueCode() != null && catForCode.getEnableUniqueCode() == 1) {
                    int codeCount = qty.intValue();
                    uniqueCodeService.generateAndSave(codeCount, savedBatchId,
                            item.getReagentId().longValue(), item.getBatchNo(), item.getUdiDi());
                }
            }
            successCount++;
        }

        // 3. 更新 PO 总金额、已收货数量和状态
        po.setTotalAmount(totalAmount);
        po.setReceivedQuantity(totalReceivedQty);
        boolean allReceived = successCount == request.getItems().size();
        po.setStatus(allReceived ? PurchaseOrderStatusEnum.COMPLETED : PurchaseOrderStatusEnum.PARTIAL_RECEIVED);
        orderMapper.updateById(po);

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
    public List<Map<String, Object>> getSmartReplenishSuggestions(int days) {
        // 1. 当前库存：按试剂汇总 reagent_batch.stock
        Map<Long, BigDecimal> stockMap = new HashMap<>();
        List<ReagentBatch> batches = inventoryService.listAllBatches();
        for (ReagentBatch b : batches) {
            if (b.getReagentId() != null && b.getStock() != null) {
                stockMap.merge(b.getReagentId(), b.getStock(), BigDecimal::add);
            }
        }

        // 2. 历史消耗：最近 N 天 + 全量
        Map<Long, BigDecimal> consumptionMap = new HashMap<>();
        Map<Long, BigDecimal> recentMap = new HashMap<>();
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<ConsumptionRecord> records = consumptionService.findConsumptionRecords();
        for (ConsumptionRecord r : records) {
            if (r.getReagentId() != null && r.getQuantity() != null) {
                consumptionMap.merge(r.getReagentId(), r.getQuantity(), BigDecimal::add);
                if (r.getOccurredAt() != null && !r.getOccurredAt().isBefore(since)) {
                    recentMap.merge(r.getReagentId(), r.getQuantity(), BigDecimal::add);
                }
            }
        }

        // 3. 先删除旧的 PENDING 建议（每次重新计算）
        suggestionMapper.delete(new LambdaQueryWrapper<SmartReplenishSuggestion>()
                .eq(SmartReplenishSuggestion::getStatus, "PENDING"));

        // 4. 计算补货建议 → 持久化到 smart_replenish_suggestion 表
        //    算法：目标库存 = max(历史总消耗, 最低库存阈值10)，建议补货量 = 目标 - 当前库存
        String batchNo = "SR-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
                String.format("%04d", System.currentTimeMillis() % 10000);
        List<SmartReplenishSuggestion> entities = new ArrayList<>();

        for (Long reagentId : consumptionMap.keySet()) {
            BigDecimal currentStock = stockMap.getOrDefault(reagentId, BigDecimal.ZERO);
            BigDecimal totalConsumed = consumptionMap.get(reagentId);
            BigDecimal recentConsumed = recentMap.getOrDefault(reagentId, BigDecimal.ZERO);

            BigDecimal minPar = BigDecimal.TEN;
            BigDecimal targetStock = totalConsumed.compareTo(minPar) > 0 ? totalConsumed : minPar;
            BigDecimal suggestedQty = targetStock.subtract(currentStock);
            if (suggestedQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal dailyRate = recentConsumed.compareTo(BigDecimal.ZERO) > 0
                    ? recentConsumed.divide(BigDecimal.valueOf(days), 2, java.math.RoundingMode.HALF_UP)
                    : totalConsumed.divide(BigDecimal.valueOf(Math.max(days, 1)), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal safetyStock = dailyRate.multiply(BigDecimal.TEN);

            ReagentCatalog cat = catalogService.findById(reagentId);
            if (cat == null) continue;

            SmartReplenishSuggestion s = new SmartReplenishSuggestion();
            s.setReagentId(reagentId);
            s.setReagentName(cat.getGenericName());
            s.setSpecification(cat.getSpecification() != null ? cat.getSpecification() : "");
            s.setManufacturer(cat.getManufacturer() != null ? cat.getManufacturer() : "");
            s.setCurrentStock(currentStock);
            s.setDailyConsumption(dailyRate);
            s.setSafetyStock(safetyStock.setScale(0, java.math.RoundingMode.UP));
            s.setSuggestedQty(suggestedQty.setScale(0, java.math.RoundingMode.UP));
            s.setUnit(cat.getUnit() != null ? cat.getUnit() : "");
            s.setSupplier(cat.getDistributor() != null ? cat.getDistributor()
                    : cat.getManufacturer() != null ? cat.getManufacturer() : "—");
            s.setAnalysisDays(days);
            s.setBatchNo(batchNo);
            s.setStatus("PENDING");
            s.setCreatedAt(LocalDateTime.now());
            s.setUpdatedAt(LocalDateTime.now());
            entities.add(s);
        }

        if (!entities.isEmpty()) {
            suggestionMapper.insert(entities);
        }

        // 5. 读回持久化后的记录（带 ID），转为 Map 供前端展示
        List<SmartReplenishSuggestion> persisted = suggestionMapper.selectList(
                new LambdaQueryWrapper<SmartReplenishSuggestion>().eq(SmartReplenishSuggestion::getBatchNo, batchNo));
        persisted.sort((a, b) -> b.getSuggestedQty().compareTo(a.getSuggestedQty()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (SmartReplenishSuggestion s : persisted) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId().toString());
            item.put("reagentId", s.getReagentId());
            item.put("reagentName", s.getReagentName());
            item.put("specification", s.getSpecification());
            item.put("manufacturer", s.getManufacturer());
            item.put("currentStock", s.getCurrentStock());
            item.put("safetyStock", s.getSafetyStock());
            item.put("dailyConsumption", s.getDailyConsumption());
            item.put("suggestedQty", s.getSuggestedQty());
            item.put("unit", s.getUnit());
            item.put("supplier", s.getSupplier());
            result.add(item);
        }

        log.info("智能补货计算完成: days={}, 分析试剂={}, 建议补货={}, batchNo={}", days, consumptionMap.size(), result.size(), batchNo);
        return result;
    }

    @Override
    public List<PurchaseOrder> createOrdersFromReplenish(List<Map<String, Object>> items) {
        List<PurchaseOrder> orders = new ArrayList<>();
        Set<Long> suggestionIds = new HashSet<>();

        for (Map<String, Object> item : items) {
            // 读取前端提交的 ID + 可能被用户修改过的数量
            Long suggestionId = null;
            Object idObj = item.get("id");
            if (idObj != null) {
                suggestionId = Long.valueOf(idObj.toString());
                suggestionIds.add(suggestionId);
            }

            Long reagentId = null;
            Object rid = item.get("reagentId");
            if (rid != null) reagentId = Long.valueOf(rid.toString());

            // 数量优先使用前端传入（支持修改），否则从建议表读取
            BigDecimal orderQty = null;
            Object qty = item.get("suggestedQty");
            if (qty != null) orderQty = new BigDecimal(qty.toString());

            if (reagentId == null || orderQty == null) {
                log.warn("智能补货生成PO跳过: 缺少 reagentId 或 suggestedQty, item={}", item);
                continue;
            }

            String no = "PO-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
                    String.format("%04d", System.currentTimeMillis() % 10000);
            PurchaseOrder po = new PurchaseOrder();
            po.setPoNo(no);
            po.setSourceType("SMART_REPLENISH");
            po.setSettlementMode("调拨结算");
            po.setStatus(PurchaseOrderStatusEnum.DRAFT);
            po.setOrderDate(LocalDate.now());

            // 供应商：优先前端传入，否则默认取最小可用供应商
            Object sid = item.get("supplierId");
            if (sid != null) {
                po.setSupplierId(Integer.valueOf(sid.toString()));
            } else {
                po.setSupplierId(4); // 默认供应商
            }

            po.setCreatedAt(LocalDateTime.now());
            po.setUpdatedAt(LocalDateTime.now());

            orderMapper.insert(po);

            // 写入订单明细
            PurchaseOrderItem poi = new PurchaseOrderItem();
            poi.setPoId(po.getId());
            poi.setLineNo(1);
            poi.setReagentId(reagentId);
            poi.setSupplierId(po.getSupplierId());
            poi.setMaterialType("REAGENT");
            poi.setQtyOrdered(orderQty);

            ReagentCatalog cat = catalogService.findById(reagentId);
            BigDecimal unitPrice = (cat != null && cat.getPricePerUnit() != null)
                    ? cat.getPricePerUnit() : BigDecimal.ZERO;
            poi.setUnitPrice(unitPrice);
            poi.setAmount(unitPrice.multiply(orderQty));
            poi.setQtyReceived(BigDecimal.ZERO);
            poi.setVersion(0);
            poi.setCreatedAt(LocalDateTime.now());
            orderItemMapper.insert(poi);

            // 回写 PO 总金额（与 createManualOrder / createOrderFromShortage 对齐）
            po.setTotalAmount(poi.getAmount());
            po.setReceivedQuantity(BigDecimal.ZERO);
            orderMapper.updateById(po);

            orders.add(po);
        }

        // 更新建议表状态为 GENERATED
        if (!suggestionIds.isEmpty()) {
            SmartReplenishSuggestion update = new SmartReplenishSuggestion();
            update.setStatus("GENERATED");
            update.setUpdatedAt(LocalDateTime.now());
            suggestionMapper.update(update, new LambdaQueryWrapper<SmartReplenishSuggestion>()
                    .in(SmartReplenishSuggestion::getId, suggestionIds));
        }

        log.info("智能补货生成采购单: count={}, suggestionIds={}", orders.size(), suggestionIds);
        return orders;
    }

    // ── 私有辅助方法 ──

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
