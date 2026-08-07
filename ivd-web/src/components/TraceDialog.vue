<!--
  TraceDialog.vue — UDI 追溯弹窗（共用）
  mode="trace"        → Central/Dept/Group 仓库，输入 UDI-DI/批号查询单条追溯
  mode="reagent-udi"  → 中心库专用，展示试剂全部 UDI-DI / UDI-PI / 唯一码
-->
<template>
  <el-dialog :model-value="modelValue" :title="mode === 'reagentUdi' ? 'UDI 数据总览' : 'UDI 追溯'" width="900px" top="5vh" @update:model-value="$emit('update:modelValue', $event)">
    <!-- trace 模式：搜索框 -->
    <template v-if="mode !== 'reagentUdi'">
      <el-input v-model="search" placeholder="输入 UDI-DI 或批号" :prefix-icon="Search" style="width:300px;margin-bottom:16px" @keyup.enter="doSearch" />
    </template>

    <!-- trace 结果 -->
    <template v-if="mode !== 'reagentUdi' && result">
      <el-descriptions :column="3" border size="small" style="margin-bottom:20px">
        <el-descriptions-item label="批号">{{ result.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="UDI-DI">{{ result.udiDi }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag size="small">{{ result.statusLabel }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="库存">{{ result.stock }}</el-descriptions-item>
        <el-descriptions-item label="效期至">{{ result.expiryDate }}</el-descriptions-item>
        <el-descriptions-item label="仓库ID">{{ result.warehouseId }}</el-descriptions-item>
      </el-descriptions>
      <el-steps :active="result.timeline ? result.timeline.filter(t => t.status === 'completed').length : 0" finish-status="success" align-center>
        <el-step v-for="(t, i) in (result.timeline || [])" :key="i" :title="t.node" :status="t.status === 'completed' ? 'success' : i === 0 ? 'finish' : 'wait'" />
      </el-steps>
    </template>

    <!-- reagent-udi 模式：试剂信息 + 全部批次 UDI 数据 -->
    <template v-if="mode === 'reagentUdi'">
      <div v-loading="udiLoading">
        <!-- 试剂信息 -->
        <template v-if="udiData">
          <el-descriptions :column="4" border size="small" style="margin-bottom:20px">
            <el-descriptions-item label="试剂名称">{{ udiData.reagentName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="生产厂家">{{ udiData.manufacturer || '—' }}</el-descriptions-item>
            <el-descriptions-item label="规格">{{ udiData.spec || '—' }}</el-descriptions-item>
            <el-descriptions-item label="注册证号">{{ udiData.registrationCertNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="HIS 编码">{{ udiData.hisCode || '—' }}</el-descriptions-item>
            <el-descriptions-item label="医保编码">{{ udiData.insuranceCode || '—' }}</el-descriptions-item>
            <el-descriptions-item label="NMPA 注册证">{{ udiData.nmpaCert || '—' }}</el-descriptions-item>
            <el-descriptions-item label="批次数">{{ udiData.batchCount || 0 }}</el-descriptions-item>
          </el-descriptions>

          <!-- 批次 UDI 列表 -->
          <el-table :data="udiData.batches || []" stripe size="small" max-height="400">
            <el-table-column prop="batchNo" label="批号" width="160" />
            <el-table-column prop="udiDi" label="UDI-DI" min-width="180" show-overflow-tooltip />
            <el-table-column prop="udiPi" label="UDI-PI" min-width="180" show-overflow-tooltip />
            <el-table-column prop="stock" label="库存" width="70" align="center" />
            <el-table-column prop="unit" label="单位" width="60" align="center" />
            <el-table-column prop="expiryDate" label="效期至" width="110" align="center" />
            <el-table-column prop="manufactureDate" label="生产日期" width="110" align="center" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'IN_STOCK' ? 'success' : 'warning'" size="small">
                  {{ row.statusLabel || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="locationCode" label="库位" width="80" align="center" />
          </el-table>
          <div style="margin-top:8px;font-size:12px;color:var(--text-tertiary)">
            共 {{ udiData.batchCount || 0 }} 个批次
          </div>

          <!-- 唯一码列表 -->
          <el-divider />
          <!-- 库存概览（始终显示） -->
          <div style="display:flex;align-items:center;gap:16px;margin-bottom:12px;flex-wrap:wrap">
            <span style="font-weight:600">🔢 唯一码</span>
            <span style="font-size:13px">
              中心库库存：<b>{{ udiData.totalStock ?? '—' }}</b>
            </span>
            <span v-if="(udiData.inTransitStock || 0) > 0" style="font-size:13px;color:var(--el-color-info)">
              在途：<b>{{ udiData.inTransitStock }}</b>
            </span>
          </div>
          <!-- 启用唯一码管控：展示统计数据 + 唯一码列表 -->
          <template v-if="udiData.enableUniqueCode">
            <div v-if="(udiData.inStockCodeCount || 0) > 0 || (udiData.consumedCodeCount || 0) > 0 || (udiData.transferredCodeCount || 0) > 0" style="display:flex;align-items:center;gap:16px;margin-bottom:12px;flex-wrap:wrap">
              <span style="font-size:13px;color:var(--el-color-success)">
                在库：<b>{{ udiData.inStockCodeCount ?? 0 }}</b> 个
              </span>
              <template v-if="(udiData.consumedCodeCount || 0) > 0 || (udiData.transferredCodeCount || 0) > 0">
                <span v-if="(udiData.consumedCodeCount || 0) > 0" style="font-size:13px;color:var(--el-color-warning)">
                  已消耗：<b>{{ udiData.consumedCodeCount }}</b> 个
                </span>
                <span v-if="(udiData.transferredCodeCount || 0) > 0" style="font-size:13px;color:var(--el-color-info)">
                  已调拨：<b>{{ udiData.transferredCodeCount }}</b> 个
                </span>
              </template>
              <span v-if="(udiData.consumedCodeCount || 0) > 0 || (udiData.transferredCodeCount || 0) > 0" style="font-size:12px;color:var(--text-tertiary)">
                共 {{ udiData.uniqueCodeCount || 0 }} 个
              </span>
              <span v-if="(udiData.consumedCodeCount || 0) > 0 || (udiData.transferredCodeCount || 0) > 0" style="font-size:12px;color:var(--text-tertiary)">
                （已消耗/已调拨唯一码不在下方列表展示）
              </span>
            </div>
            <el-table v-if="udiData.uniqueCodes && udiData.uniqueCodes.length > 0" :data="udiData.uniqueCodes" stripe size="small" max-height="300">
              <el-table-column prop="uniqueCode" label="唯一码" width="180" />
              <el-table-column prop="batchNo" label="批号" width="160" />
              <el-table-column prop="udiDi" label="UDI-DI" min-width="180" show-overflow-tooltip />
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'IN_STOCK' ? 'success' : 'warning'" size="small">
                    {{ row.status === 'IN_STOCK' ? '在库' : row.status }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂未生成唯一码（验收入库后自动生成）" :image-size="60" />
          </template>
          <!-- 未启用唯一码管控：提示说明 -->
          <el-alert v-else type="info" :closable="false" show-icon>
            <template #title>
              该试剂<strong>未启用唯一码管控</strong>，批次库存量基于入库累加计算，与唯一码无关。
              如需使用唯一码追溯，请在「试剂目录 → 编辑」中开启「唯一码管控」开关。
            </template>
          </el-alert>
        </template>
        <el-empty v-else-if="!udiLoading" description="暂无 UDI 数据" />
      </div>
    </template>

    <!-- trace 模式无结果 -->
    <template v-if="mode !== 'reagentUdi' && !result && !search">
      <el-empty description="输入 UDI-DI 或批号查询" />
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { useInventoryStore } from '@/stores/inventory'
import { getReagentUdiData } from '@/api/inventory'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  initialSearch: { type: String, default: '' },
  mode: { type: String, default: 'trace' },        // 'trace' | 'reagentUdi'
  reagentId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue'])

const store = useInventoryStore()
const search = ref('')
const result = ref(null)

// ── reagent-udi 模式状态 ──
const udiData = ref(null)
const udiLoading = ref(false)

watch(() => props.modelValue, async (v) => {
  if (v) {
    if (props.mode === 'reagentUdi' && props.reagentId) {
      // reagent-udi 模式：直接加载试剂 UDI 数据
      await loadReagentUdi()
    } else {
      // trace 模式：初始化搜索
      search.value = props.initialSearch || ''
      result.value = null
      if (search.value.trim()) doSearch()
    }
  }
})

async function doSearch() {
  if (!search.value.trim()) return
  try {
    await store.doTrace(search.value)
    result.value = store.traceResult
  } catch { result.value = null }
}

async function loadReagentUdi() {
  udiLoading.value = true
  udiData.value = null
  try {
    udiData.value = await getReagentUdiData(props.reagentId)
  } catch {
    udiData.value = null
  } finally {
    udiLoading.value = false
  }
}
</script>
