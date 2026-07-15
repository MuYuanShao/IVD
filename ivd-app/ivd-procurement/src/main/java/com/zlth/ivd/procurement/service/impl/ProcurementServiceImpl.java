package com.zlth.ivd.procurement.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.enums.MaterialTypeEnum;
import com.zlth.ivd.common.enums.PurchaseOrderStatusEnum;
import com.zlth.ivd.common.enums.RequisitionStatusEnum;
import com.zlth.ivd.common.enums.ShortageStatusEnum;
import com.zlth.ivd.common.enums.UdiStatusEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.catalog.mapper.ReagentCatalogMapper;
import com.zlth.ivd.inventory.entity.ReagentBatch;
import com.zlth.ivd.inventory.mapper.ReagentBatchMapper;
import com.zlth.ivd.procurement.dto.ReceiveItemRequest;
import com.zlth.ivd.procurement.entity.*;
import com.zlth.ivd.procurement.vo.PurchaseOrderVO;
import com.zlth.ivd.procurement.vo.PurchaseRequisitionVO;
import com.zlth.ivd.procurement.vo.ShortageOrderVO;
import com.zlth.ivd.procurement.mapper.*;
import com.zlth.ivd.procurement.service.ProcurementService;
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

    public ProcurementServiceImpl(PurchaseRequisitionMapper reqMapper,
                                  PurchaseRequisitionItemMapper reqItemMapper,
                                  ShortageOrderMapper shortageMapper,
                                  ShortageOrderItemMapper shortageItemMapper,
                                  PurchaseOrderMapper orderMapper,
                                  PurchaseOrderItemMapper orderItemMapper,
                                  ReagentBatchMapper batchMapper,
                                  ReagentCatalogMapper catalogMapper) {
        this.reqMapper = reqMapper;
        this.reqItemMapper = reqItemMapper;
        this.shortageMapper = shortageMapper;
        this.shortageItemMapper = shortageItemMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.batchMapper = batchMapper;
        this.catalogMapper = catalogMapper;
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

        List<PurchaseRequisitionVO> records = new ArrayList<>();
        for (PurchaseRequisition req : p.getRecords()) {
            PurchaseRequisitionVO vo = new PurchaseRequisitionVO();
            vo.setId(req.getId()); vo.setReqNo(req.getReqNo());
            vo.setFromDeptId(req.getFromDeptId());
            vo.setDeptName("科室#" + req.getFromDeptId());
            vo.setToWarehouseId(req.getToWarehouseId());
            vo.setApplicantId(req.getApplicantId());
            vo.setApplicantName("用户#" + (req.getApplicantId() != null ? req.getApplicantId() : "-"));
            vo.setUrgency(req.getUrgency()); vo.setStatus(req.getStatus());
            vo.setReviewerId(req.getReviewerId()); vo.setReviewTime(req.getReviewTime());
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
        req.setId(IdUtil.getSnowflakeNextId());
        req.setReqNo(no);
        req.setStatus(RequisitionStatusEnum.DRAFT);
        reqMapper.insert(req);
        log.info("申领单创建: reqNo={}", no);
        return req;
    }

    @Override
    public PurchaseRequisitionVO getRequisitionDetail(Long id) {
        PurchaseRequisition req = reqMapper.selectById(id);
        if (req == null) throw new BizException(40400, "申领单不存在");

        List<PurchaseRequisitionItem> items = reqItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseRequisitionItem>().eq(PurchaseRequisitionItem::getReqId, id));
        if (items == null) items = Collections.emptyList();

        PurchaseRequisitionVO vo = new PurchaseRequisitionVO();
        vo.setId(req.getId()); vo.setReqNo(req.getReqNo());
        vo.setFromDeptId(req.getFromDeptId());
        vo.setDeptName("科室#" + req.getFromDeptId());
        vo.setToWarehouseId(req.getToWarehouseId());
        vo.setApplicantId(req.getApplicantId());
        vo.setApplicantName("用户#" + (req.getApplicantId() != null ? req.getApplicantId() : "-"));
        vo.setUrgency(req.getUrgency()); vo.setStatus(req.getStatus());
        vo.setReviewerId(req.getReviewerId()); vo.setReviewTime(req.getReviewTime());
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

        List<ShortageOrderVO> records = new ArrayList<>();
        for (ShortageOrder so : p.getRecords()) {
            ShortageOrderVO vo = new ShortageOrderVO();
            vo.setId(so.getId()); vo.setShortageNo(so.getShortageNo());
            vo.setReqId(so.getReqId());
            vo.setSourceReqNo(reqNoMap.getOrDefault(so.getReqId(), "-"));
            vo.setFromDeptId(so.getFromDeptId());
            vo.setDeptName("科室#" + so.getFromDeptId());
            vo.setStatus(so.getStatus()); vo.setUrgency(so.getUrgency());
            vo.setSuggestedSupplierId(so.getSuggestedSupplierId());
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
    public ShortageOrderVO getShortageDetail(Long id) {
        ShortageOrder so = shortageMapper.selectById(id);
        if (so == null) throw new BizException(40400, "缺货单不存在");

        List<ShortageOrderItem> items = shortageItemMapper.selectList(
                new LambdaQueryWrapper<ShortageOrderItem>().eq(ShortageOrderItem::getShortageOrderId, id));
        if (items == null) items = Collections.emptyList();

        ShortageOrderVO vo = new ShortageOrderVO();
        vo.setId(so.getId()); vo.setShortageNo(so.getShortageNo());
        vo.setReqId(so.getReqId());
        vo.setSourceReqNo(so.getReqId() != null ? "REQ#" + so.getReqId() : "-");
        vo.setFromDeptId(so.getFromDeptId());
        vo.setDeptName("科室#" + so.getFromDeptId());
        vo.setStatus(so.getStatus()); vo.setUrgency(so.getUrgency());
        vo.setSuggestedSupplierId(so.getSuggestedSupplierId());
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
        String no = "SH-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
                String.format("%03d", System.currentTimeMillis() % 1000);
        ShortageOrder so = new ShortageOrder();
        so.setId(IdUtil.getSnowflakeNextId());
        so.setShortageNo(no);
        so.setReqId(reqId);
        so.setStatus(ShortageStatusEnum.PENDING_PROCUREMENT);
        shortageMapper.insert(so);
        log.info("缺货单创建: shortageNo={}, reqId={}", no, reqId);
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
    public PurchaseOrder createOrderFromShortage(Long shortageId, Integer supplierId, String expectedDeliveryDate, String paymentTerms, String remark) {
        String no = "PO-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
                String.format("%04d", System.currentTimeMillis() % 10000);
        PurchaseOrder po = new PurchaseOrder();
        po.setId(IdUtil.getSnowflakeNextId());
        po.setPoNo(no);
        po.setSupplierId(supplierId);
        po.setSourceType("SHORTAGE");
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
    public PurchaseOrder createManualOrder(PurchaseOrder order) {
        String no = "PO-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
                String.format("%04d", System.currentTimeMillis() % 10000);
        order.setId(IdUtil.getSnowflakeNextId());
        order.setPoNo(no);
        order.setSourceType("MANUAL");
        order.setStatus(PurchaseOrderStatusEnum.DRAFT);
        order.setOrderDate(LocalDate.now());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.insert(order);
        log.info("手动采购订单创建: poNo={}", no);
        return order;
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
                batch.setId(IdUtil.getSnowflakeNextId());
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
        if (deptId != null) qw.eq(PurchaseRequisition::getFromDeptId, deptId.intValue());
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
                row.put("applicant", "用户#" + (req.getApplicantId() != null ? req.getApplicantId() : "-"));
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
                row.put("applicant", "用户#" + (req.getApplicantId() != null ? req.getApplicantId() : "-"));
                row.put("status", req.getStatus() != null ? req.getStatus().getLabel() : "-");
                row.put("createdAt", req.getCreatedAt() != null ? req.getCreatedAt().toString().replace("T", " ") : null);
                records.add(row);
            }
        }

        return new PageVO<>(p.getTotal(), page, pageSize, records);
    }

    @Override
    public List<Map<String, Object>> getSmartReplenishSuggestions(int days) {
        // TODO: 基于历史消耗预测的智能补货算法
        return Collections.emptyList();
    }
}
