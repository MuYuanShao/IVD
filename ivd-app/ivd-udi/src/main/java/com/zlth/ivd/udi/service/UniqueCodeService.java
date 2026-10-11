package com.zlth.ivd.udi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.common.utils.IdGenerator;
import com.zlth.ivd.udi.entity.UdiUniqueCode;
import com.zlth.ivd.udi.entity.UdiUniqueCodeSeq;
import com.zlth.ivd.udi.mapper.UdiUniqueCodeMapper;
import com.zlth.ivd.udi.mapper.UdiUniqueCodeSeqMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 唯一码生成服务
 * <p>
 * 验收入库时按入库数量逐盒/逐单位生成唯一码，格式: yyyyMMdd + 6位序号。
 * 使用数据库日序列表保证并发安全与日级重置。
 *
 * @author IVD Team
 * @since 2026-07-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UniqueCodeService {

    private final UdiUniqueCodeMapper uniqueCodeMapper;
    private final UdiUniqueCodeSeqMapper seqMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 批量生成唯一码并持久化
     *
     * @param count     生成数量
     * @param batchId   关联批次ID
     * @param reagentId 试剂ID
     * @param batchNo   批号
     * @param udiDi     UDI-DI
     * @return 生成的唯一码列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> generateAndSave(int count, Long batchId, Long reagentId, String batchNo, String udiDi) {
        if (count <= 0) return List.of();

        String dateKey = LocalDate.now().format(DATE_FMT);

        // 1. 原子自增获取起始序号
        seqMapper.incrementSeq(dateKey, count);

        // 2. 读取当前最大序号
        UdiUniqueCodeSeq seq = seqMapper.selectById(dateKey);
        long endSeq = seq != null ? seq.getCurrentSeq() : count;
        long startSeq = endSeq - count + 1;

        // 3. 生成唯一码并批量插入
        List<String> codes = new ArrayList<>(count);
        List<UdiUniqueCode> entities = new ArrayList<>(count);
        LocalDateTime now = LocalDateTime.now();

        for (long i = startSeq; i <= endSeq; i++) {
            String code = dateKey + String.format("%06d", i);
            codes.add(code);

            UdiUniqueCode entity = new UdiUniqueCode();
            entity.setId(IdGenerator.nextId());
            entity.setUniqueCode(code);
            entity.setBatchId(batchId);
            entity.setReagentId(reagentId);
            entity.setBatchNo(batchNo);
            entity.setUdiDi(udiDi);
            entity.setStatus("IN_STOCK");
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entities.add(entity);
        }

        // 批量插入
        for (UdiUniqueCode entity : entities) {
            uniqueCodeMapper.insert(entity);
        }

        log.info("唯一码生成完成: count={}, range={}~{}, batchNo={}", count, codes.get(0), codes.get(codes.size() - 1), batchNo);
        return codes;
    }

    /**
     * 按批次查询所有唯一码
     */
    public List<UdiUniqueCode> listByBatchId(Long batchId) {
        return uniqueCodeMapper.selectList(
                new LambdaQueryWrapper<UdiUniqueCode>()
                        .eq(UdiUniqueCode::getBatchId, batchId)
                        .orderByAsc(UdiUniqueCode::getUniqueCode));
    }

    /**
     * 按试剂查询所有唯一码
     */
    public List<UdiUniqueCode> listByReagentId(Long reagentId) {
        return uniqueCodeMapper.selectList(
                new LambdaQueryWrapper<UdiUniqueCode>()
                        .eq(UdiUniqueCode::getReagentId, reagentId)
                        .orderByAsc(UdiUniqueCode::getUniqueCode));
    }

    /**
     * 消耗唯一码：将批次下指定数量的在库唯一码标记为已消耗
     *
     * @param batchId 批次ID
     * @param count   消耗数量
     * @return 实际更新的数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int consumeUniqueCodes(Long batchId, int count) {
        if (count <= 0) return 0;

        List<UdiUniqueCode> codes = uniqueCodeMapper.selectList(
                new LambdaQueryWrapper<UdiUniqueCode>()
                        .eq(UdiUniqueCode::getBatchId, batchId)
                        .eq(UdiUniqueCode::getStatus, "IN_STOCK")
                        .orderByAsc(UdiUniqueCode::getUniqueCode)
                        .last("LIMIT " + count));

        if (codes.isEmpty()) return 0;

        LocalDateTime now = LocalDateTime.now();
        int updated = 0;
        for (UdiUniqueCode code : codes) {
            code.setStatus("CONSUMED");
            code.setUpdatedAt(now);
            uniqueCodeMapper.updateById(code);
            updated++;
        }

        log.info("唯一码消耗完成: batchId={}, count={}, consumed={}", batchId, count, updated);
        return updated;
    }

    /**
     * 调拨唯一码：将批次下指定数量的在库唯一码关联到目标批次
     *
     * @param sourceBatchId 源批次ID
     * @param targetBatchId 目标批次ID
     * @param count         调拨数量
     * @return 实际更新的数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int transferUniqueCodes(Long sourceBatchId, Long targetBatchId, int count) {
        if (count <= 0) return 0;

        List<UdiUniqueCode> codes = uniqueCodeMapper.selectList(
                new LambdaQueryWrapper<UdiUniqueCode>()
                        .eq(UdiUniqueCode::getBatchId, sourceBatchId)
                        .eq(UdiUniqueCode::getStatus, "IN_STOCK")
                        .orderByAsc(UdiUniqueCode::getUniqueCode)
                        .last("LIMIT " + count));

        if (codes.isEmpty()) return 0;

        LocalDateTime now = LocalDateTime.now();
        int updated = 0;
        for (UdiUniqueCode code : codes) {
            code.setBatchId(targetBatchId);
            // Called after target stock has actually been received, not while shipment is in transit.
            code.setStatus("IN_STOCK");
            code.setUpdatedAt(now);
            uniqueCodeMapper.updateById(code);
            updated++;
        }

        log.info("唯一码调拨完成: sourceBatch={}, targetBatch={}, count={}, transferred={}",
                sourceBatchId, targetBatchId, count, updated);
        return updated;
    }

    /**
     * 按唯一码查询单条记录
     */
    public UdiUniqueCode getByCode(String uniqueCode) {
        return uniqueCodeMapper.selectOne(
                new LambdaQueryWrapper<UdiUniqueCode>()
                        .eq(UdiUniqueCode::getUniqueCode, uniqueCode));
    }
}
