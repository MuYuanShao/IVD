package com.zlth.ivd.procurement.service.impl;

import com.zlth.ivd.common.utils.IdGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.enums.MaterialTypeEnum;
import com.zlth.ivd.common.enums.PurchaseOrderStatusEnum;
import com.zlth.ivd.common.enums.RequisitionStatusEnum;
import com.zlth.ivd.common.enums.ShortageStatusEnum;
import com.zlth.ivd.common.enums.UdiStatusEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.catalog.mapper.ReagentCatalogMapper;
import com.zlth.ivd.consumption.entity.ConsumptionRecord;
import com.zlth.ivd.consumption.mapper.ConsumptionRecordMapper;
import com.zlth.ivd.inventory.entity.ReagentBatch;
import com.zlth.ivd.inventory.mapper.ReagentBatchMapper;
import com.zlth.ivd.procurement.dto.CreateManualOrderRequest;
import com.zlth.ivd.procurement.dto.DirectReceiveRequest;
import com.zlth.ivd.procurement.dto.ReceiveItemRequest;
import com.zlth.ivd.procurement.entity.*;
import com.zlth.ivd.procurement.vo.PurchaseOrderVO;
import com.zlth.ivd.procurement.vo.PurchaseRequisitionVO;
import com.zlth.ivd.procurement.vo.ShortageOrderVO;
import com.zlth.ivd.procurement.mapper.*;
import com.zlth.ivd.procurement.service.ProcurementService;
import com.zlth.ivd.system.entity.SysDept;
import com.zlth.ivd.system.entity.SysUser;
import com.zlth.ivd.system.mapper.SysDeptMapper;
import com.zlth.ivd.system.mapper.SysUserMapper;
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

    private static final Logger log = LoggerFactory.getLogger(ProcurementServiceImpl.class);

    private final PurchaseRequisitionMapper reqMapper;
    private final PurchaseRequisitionItemMapper reqItemMapper;
    private final ShortageOrderMapper shortageMapper;
    private final ShortageOrderItemMapper shortageItemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final ReagentBatchMapper batchMapper;
    private final ReagentCatalogMapper catalogMapper;
    private final ConsumptionRecordMapper consumptionMapper;
    private final SmartReplenishSuggestionMapper suggestionMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysUserMapper sysUserMapper;

    public ProcurementServiceImpl(PurchaseRequisitionMapper reqMapper,
                                  PurchaseRequisitionItemMapper reqItemMapper,
                                  ShortageOrderMapper shortageMapper,
                                  ShortageOrderItemMapper shortageItemMapper,
                                  PurchaseOrderMapper orderMapper,
                                  PurchaseOrderItemMapper orderItemMapper,
                                  ReagentBatchMapper batchMapper,
                                  ReagentCatalogMapper catalogMapper,
                                  ConsumptionRecordMapper consumptionMapper,
                                  SmartReplenishSuggestionMapper suggestionMapper,
                                  SysDeptMapper sysDeptMapper,
                                  SysUserMapper sysUserMapper) {
        this.reqMapper = reqMapper;
        this.reqItemMapper = reqItemMapper;
        this.shortageMapper = shortageMapper;
        this.shortageItemMapper = shortageItemMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.batchMapper = batchMapper;
        this.catalogMapper = catalogMapper;
        this.consumptionMapper = consumptionMapper;
        this.suggestionMapper = suggestionMapper;
        this.sysDeptMapper = sysDeptMapper;
        this.sysUserMapper = sysUserMapper;
    }

    // ── 申领单 ──
    @Override
    public PageVO<PurchaseRequisitionVO> listRequisitions(int page, int pageSize, String status, String keyword) {
        LambdaQueryWrapper<PurchaseRequisition> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) qw.eq(PurchaseRequisition::getStatus, RequisitionStatusEnum.valueOf(status));
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
        String no = "REQ-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
                String.format("%03d", System.currentTimeMillis() % 1000);
        req.setReqNo(no);
        req.setStatus(RequisitionStatusEnum.DRAFT);
        // 强制使用 JWT 当前用户作为申请人，防止前端伪造 applicantId
        req.setApplicantId(SecurityUtils.getCurrentUserId());
        reqMapper.insert(req);
        log.info("申领单创建: reqNo={}, applicantId={}", no, req.getApplicantId());

        // 持久化申领明细
        List<PurchaseRequisitionItem> items = req.getItems();
        if (items != null && !items.isEmpty()) {
            // 批量查询试剂目录，填充规格/单价/厂家等信息
            Set<Integer> reagentIds = items.stream()
                    .map(PurchaseRequisitionItem::getReagentId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
            if (!reagentIds.isEmpty()) {
                List<ReagentCatalog> catalogs = catalogMapper.selectBatchIds(
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
            Set<Integer> reagentIds = items.stream()
                    .map(PurchaseRequisitionItem::getReagentId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!reagentIds.isEmpty()) {
                Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
                List<ReagentCatalog> catalogs = catalogMapper.selectBatchIds(
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
            Set<Integer> reagentIds = items.stream().map(PurchaseRequisitionItem::getReagentId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!reagentIds.isEmpty()) {
                Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
                List<ReagentCatalog> catalogs = catalogMapper.selectBatchIds(
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
        String deptName = req.getFromDeptId() != null
                ? sysDeptMapper.selectById(req.getFromDeptId()) != null
                    ? sysDeptMapper.selectById(req.getFromDeptId()).getDeptName()
                    : "科室#" + req.getFromDeptId()
                : "-";
        String applicantName = req.getApplicantId() != null
                ? sysUserMapper.selectById(req.getApplicantId()) != null
                    ? sysUserMapper.selectById(req.getApplicantId()).getRealName()
                    : "用户#" + req.getApplicantId()
                : "-";

        PurchaseRequisitionVO vo = new PurchaseRequisitionVO();
        vo.setId(req.getId()); vo.setReqNo(req.getReqNo());
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

        Set<Integer> reagentIds = allItems.stream().map(ShortageOrderItem::getReagentId).collect(Collectors.toSet());
        Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
        if (!reagentIds.isEmpty()) {
            List<ReagentCatalog> catalogs = catalogMapper.selectBatchIds(
                    reagentIds.stream().map(Long::valueOf).collect(Collectors.toList()));
            for (ReagentCatalog cat : catalogs) catalogMap.put(cat.getId(), cat);
        }

        // 批量查询科室名称
        Set<Long> deptIds = p.getRecords().stream().map(ShortageOrder::getFromDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNameMap = batchGetDeptNames(deptIds);

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
            vo.setSuggestedSupplier("供应商#" + (so.getSuggestedSupplierId() != null ? so.getSuggestedSupplierId() : "-"));
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
            Set<Integer> reagentIds = items.stream()
                    .map(ShortageOrderItem::getReagentId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!reagentIds.isEmpty()) {
                Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
                List<ReagentCatalog> catalogs = catalogMapper.selectBatchIds(
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
        String deptName = so.getFromDeptId() != null
                ? sysDeptMapper.selectById(so.getFromDeptId()) != null
                    ? sysDeptMapper.selectById(so.getFromDeptId()).getDeptName()
                    : "科室#" + so.getFromDeptId()
                : "-";

        ShortageOrderVO vo = new ShortageOrderVO();
        vo.setId(so.getId()); vo.setShortageNo(so.getShortageNo());
        vo.setReqId(so.getReqId());
        vo.setSourceReqNo(so.getReqId() != null ? "REQ#" + so.getReqId() : "-");
        vo.setFromDeptId(so.getFromDeptId() != null ? so.getFromDeptId().intValue() : null);
        vo.setDeptName(deptName);
        vo.setStatus(so.getStatus()); vo.setUrgency(so.getUrgency());
        vo.setSuggestedSupplierId(so.getSuggestedSupplierId() != null ? so.getSuggestedSupplierId().intValue() : null);
        vo.setSuggestedSupplier("供应商#" + (so.getSuggestedSupplierId() != null ? so.getSuggestedSupplierId() : "-"));
        vo.setPoId(so.getPoId());
        vo.setCreatedAt(so.getCreatedAt()); vo.setUpdatedAt(so.getUpdatedAt());

        if (!items.isEmpty()) {
            ShortageOrderItem first = items.get(0);
            // Try to get catalog info
            Set<Integer> reagentIds = items.stream().map(ShortageOrderItem::getReagentId).collect(Collectors.toSet());
            Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
            if (!reagentIds.isEmpty()) {
                List<ReagentCatalog> catalogs = catalogMapper.selectBatchIds(
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

        List<PurchaseRequisitionItem> shortItems = new ArrayList<>();
        for (PurchaseRequisitionItem item : reqItems) {
            BigDecimal stock = item.getStockOnHand() != null ? item.getStockOnHand() : BigDecimal.ZERO;
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

        Set<Integer> reagentIds = allItems.stream().map(PurchaseOrderItem::getReagentId).collect(Collectors.toSet());
        Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
        if (!reagentIds.isEmpty()) {
            List<ReagentCatalog> catalogs = catalogMapper.selectBatchIds(
                    reagentIds.stream().map(Long::valueOf).collect(Collectors.toList()));
            for (ReagentCatalog cat : catalogs) catalogMap.put(cat.getId(), cat);
        }

        List<PurchaseOrderVO> records = new ArrayList<>();
        for (PurchaseOrder po : p.getRecords()) {
            PurchaseOrderVO vo = new PurchaseOrderVO();
            vo.setId(po.getId()); vo.setPoNo(po.getPoNo());
            vo.setSupplierId(po.getSupplierId());
            vo.setSupplierName("供应商#" + (po.getSupplierId() != null ? po.getSupplierId() : "-"));
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
                vo.setOrderQuantity(first.getQtyOrdered());
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
        vo.setSupplierName("供应商#" + (po.getSupplierId() != null ? po.getSupplierId() : "-"));
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
            Set<Integer> reagentIds = items.stream().map(PurchaseOrderItem::getReagentId).collect(Collectors.toSet());
            Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
            if (!reagentIds.isEmpty()) {
                List<ReagentCatalog> catalogs = catalogMapper.selectBatchIds(
                        reagentIds.stream().map(Long::valueOf).collect(Collectors.toList()));
                for (ReagentCatalog cat : catalogs) catalogMap.put(cat.getId(), cat);
            }
            ReagentCatalog cat = catalogMap.get(Long.valueOf(first.getReagentId()));
            vo.setReagentName(cat != null ? cat.getGenericName() : "试剂#" + first.getReagentId());
            vo.setSpecification(cat != null ? cat.getSpecification() : "-");
            vo.setOrderQuantity(first.getQtyOrdered());
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
    public PurchaseOrder createManualOrder(CreateManualOrderRequest request) {
        // 1. 创建 PO 头
        String no = "PO-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
                String.format("%04d", System.currentTimeMillis() % 10000);
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNo(no);
        po.setSourceType("MANUAL");
        po.setSupplierId(request.getSupplierId());
        po.setSettlementMode(request.getSettlementMode() != null ? request.getSettlementMode() : "调拨结算");
        po.setStatus(PurchaseOrderStatusEnum.DRAFT);
        po.setOrderDate(LocalDate.now());
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());
        orderMapper.insert(po);

        // 2. 创建采购明细
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            int line = 1;
            for (CreateManualOrderRequest.ManualOrderItem item : request.getItems()) {
                PurchaseOrderItem poi = new PurchaseOrderItem();
                poi.setPoId(po.getId());
                poi.setLineNo(line++);
                poi.setReagentId(item.getReagentId());
                poi.setMaterialType("REAGENT");
                poi.setQtyOrdered(item.getQuantity());

                // 从目录获取单价
                ReagentCatalog cat = catalogMapper.selectById((long) item.getReagentId());
                if (cat != null && cat.getPricePerUnit() != null) {
                    poi.setUnitPrice(cat.getPricePerUnit());
                } else {
                    poi.setUnitPrice(BigDecimal.ZERO);
                }
                orderItemMapper.insert(poi);
            }
        }

        log.info("手动采购订单创建: poNo={}, items={}", no, request.getItems() != null ? request.getItems().size() : 0);
        return po;
    }

    // ── 验收 ──
    @Override
    public void receiveOrder(ReceiveItemRequest request) {
        PurchaseOrderItem item = orderItemMapper.selectById(request.getItemId());
        if (item == null) throw new BizException(40400, "采购订单明细不存在");

        // 1. 更新采购订单明细的已收数量
        BigDecimal qty = request.getQtyReceived() != null ? request.getQtyReceived() : BigDecimal.ONE;
        BigDecimal currentReceived = item.getQtyReceived() != null ? item.getQtyReceived() : BigDecimal.ZERO;
        item.setQtyReceived(currentReceived.add(qty));
        orderItemMapper.updateById(item);

        // 2. 创建/更新试剂批次记录（入库）
        if (request.getBatchNo() != null && !request.getBatchNo().isBlank()) {
            // 查找是否已有同批号记录
            ReagentBatch existing = batchMapper.selectOne(
                    new LambdaQueryWrapper<ReagentBatch>().eq(ReagentBatch::getBatchNo, request.getBatchNo()));
            if (existing != null) {
                // 追加库存
                existing.setStock(existing.getStock().add(qty));
                existing.setUdiStatus(UdiStatusEnum.IN_STOCK);
                batchMapper.updateById(existing);
                log.info("批次库存追加: batchNo={}, addQty={}, newStock={}", request.getBatchNo(), qty, existing.getStock());
            } else {
                // 新批次
                ReagentBatch batch = new ReagentBatch();
                batch.setId(IdGenerator.nextId());
                batch.setBatchNo(request.getBatchNo());
                batch.setReagentId(item.getReagentId().longValue());
                batch.setMaterialType(MaterialTypeEnum.REAGENT);
                batch.setUdiDi(request.getUdiDi());
                batch.setManufactureDate(request.getProductionDate());
                batch.setExpiryDate(request.getExpiryDate());
                batch.setStock(qty);
                batch.setUnitPrice(item.getUnitPrice());
                batch.setUdiStatus(UdiStatusEnum.IN_STOCK);
                batch.setWarehouseId(request.getWarehouseId() != null ? request.getWarehouseId() : 1L);
                batch.setCreatedAt(LocalDateTime.now());
                batch.setUpdatedAt(LocalDateTime.now());
                batchMapper.insert(batch);
                log.info("新批次入库: batchNo={}, reagentId={}, qty={}", request.getBatchNo(), item.getReagentId(), qty);
            }
        }

        // 3. 更新采购订单状态
        updateOrderStatus(request.getOrderId());

        log.info("验收收货完成: orderId={}, itemId={}, qty={}, batchNo={}", request.getOrderId(), request.getItemId(), qty, request.getBatchNo());
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
        orderMapper.updateById(po);
    }

    // ── 人工验收直收（无采购订单直接验收入库）──
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> receiveDirect(DirectReceiveRequest request) {
        Integer supplierId = request.getSupplierId() != null ? request.getSupplierId() : 1;
        Long warehouseId = request.getWarehouseId() != null ? request.getWarehouseId() : 1L;

        // 1. 自动创建 MANUAL 采购订单
        PurchaseOrder po = new PurchaseOrder();
        po.setId(IdGenerator.nextId());
        po.setSupplierId(supplierId);
        po.setSourceType("MANUAL");
        po.setAcceptanceMethod("人工验收");
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

        for (DirectReceiveRequest.DirectReceiveItem item : request.getItems()) {
            if (item.getReagentId() == null) continue;
            BigDecimal qty = item.getQtyReceived() != null ? item.getQtyReceived() : BigDecimal.ONE;

            // 查试剂目录获取单价
            BigDecimal unitPrice = item.getUnitPrice();
            if (unitPrice == null) {
                ReagentCatalog catalog = catalogMapper.selectById(item.getReagentId().longValue());
                unitPrice = catalog != null ? catalog.getPricePerUnit() : BigDecimal.ZERO;
            }

            // 2.1 创建订单明细
            PurchaseOrderItem poi = new PurchaseOrderItem();
            poi.setId(IdGenerator.nextId());
            poi.setPoId(po.getId());
            poi.setLineNo(successCount + 1);
            poi.setReagentId(item.getReagentId());
            poi.setMaterialType("REAGENT");
            poi.setQtyOrdered(qty);
            poi.setUnitPrice(unitPrice);
            poi.setAmount(unitPrice.multiply(qty));
            poi.setQtyReceived(qty);  // 直接全额收货
            poi.setCreatedAt(LocalDateTime.now());
            orderItemMapper.insert(poi);

            totalAmount = totalAmount.add(unitPrice.multiply(qty));

            // 2.2 直接创建/追加批次库存
            if (item.getBatchNo() != null && !item.getBatchNo().isBlank()) {
                ReagentBatch existing = batchMapper.selectOne(
                        new LambdaQueryWrapper<ReagentBatch>().eq(ReagentBatch::getBatchNo, item.getBatchNo()));
                if (existing != null) {
                    existing.setStock(existing.getStock().add(qty));
                    existing.setUdiStatus(UdiStatusEnum.IN_STOCK);
                    batchMapper.updateById(existing);
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
                    batch.setUdiStatus(UdiStatusEnum.IN_STOCK);
                    batch.setWarehouseId(warehouseId);
                    batch.setCreatedAt(LocalDateTime.now());
                    batch.setUpdatedAt(LocalDateTime.now());
                    batchMapper.insert(batch);
                }
            }
            successCount++;
        }

        // 3. 更新 PO 总金额和状态
        po.setTotalAmount(totalAmount);
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
        Set<Integer> reagentIds = allItems.stream().map(PurchaseRequisitionItem::getReagentId).collect(Collectors.toSet());
        Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
        if (!reagentIds.isEmpty()) {
            List<ReagentCatalog> catalogs = catalogMapper.selectBatchIds(
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
        List<ReagentBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<>());
        for (ReagentBatch b : batches) {
            if (b.getReagentId() != null && b.getStock() != null) {
                stockMap.merge(b.getReagentId(), b.getStock(), BigDecimal::add);
            }
        }

        // 2. 历史消耗：最近 N 天 + 全量
        Map<Long, BigDecimal> consumptionMap = new HashMap<>();
        Map<Long, BigDecimal> recentMap = new HashMap<>();
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<ConsumptionRecord> records = consumptionMapper.selectList(
                new LambdaQueryWrapper<ConsumptionRecord>()
                        .eq(ConsumptionRecord::getRecordType, "CONSUME"));
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

            ReagentCatalog cat = catalogMapper.selectById(reagentId);
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
            poi.setReagentId(reagentId.intValue());
            poi.setMaterialType("REAGENT");
            poi.setQtyOrdered(orderQty);

            ReagentCatalog cat = catalogMapper.selectById(reagentId);
            if (cat != null && cat.getPricePerUnit() != null) {
                poi.setUnitPrice(cat.getPricePerUnit());
            } else {
                poi.setUnitPrice(BigDecimal.ZERO);
            }
            orderItemMapper.insert(poi);

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
        List<SysDept> depts = sysDeptMapper.selectBatchIds(deptIds);
        return depts.stream().collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));
    }

    /** 批量查询用户真实姓名 */
    private Map<Long, String> batchGetUserNames(Set<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        return users.stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
    }
}
