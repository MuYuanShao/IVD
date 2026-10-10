package com.zlth.ivd.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zlth.ivd.common.enums.UdiStatusEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.inventory.entity.ReagentBatch;
import com.zlth.ivd.inventory.mapper.ReagentBatchMapper;
import com.zlth.ivd.udi.service.UdiBatchStateStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UdiBatchStateStoreImpl implements UdiBatchStateStore {

    private final ReagentBatchMapper batchMapper;

    @Override
    public Long findUniqueBatchId(String batchNo) {
        List<ReagentBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<ReagentBatch>()
                .eq(ReagentBatch::getBatchNo, batchNo)
                .last("LIMIT 2"));
        if (batches.isEmpty()) throw new BizException(40400, "批次不存在: " + batchNo);
        if (batches.size() > 1) throw new BizException(40900, "批号不唯一，请使用批次ID执行状态转移: " + batchNo);
        return batches.get(0).getId();
    }

    @Override
    public boolean updateStatus(Long batchId, UdiStatusEnum expectedStatus, UdiStatusEnum targetStatus) {
        return batchMapper.update(null, new LambdaUpdateWrapper<ReagentBatch>()
                .eq(ReagentBatch::getId, batchId)
                .eq(ReagentBatch::getUdiStatus, expectedStatus)
                .set(ReagentBatch::getUdiStatus, targetStatus)) == 1;
    }
}
