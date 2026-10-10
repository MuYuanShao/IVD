<!--
  ChangeLogView.vue — 信息变更 功能菜单
  统一展示证照管理 + 试剂目录 两模块变更记录
-->
<template>
  <div class="page-container">
    <!-- 过滤器 -->
    <el-card class="toolbar-card">
      <el-row :gutter="16" align="middle">
        <el-col :span="3">
          <el-select v-model="filterModule" placeholder="变更模块" style="width:100%" @change="onModuleChange">
            <el-option label="全部模块" value="" />
            <el-option label="证照管理" value="LICENSE" />
            <el-option label="试剂目录" value="CATALOG" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width:100%"
          />
        </el-col>
        <el-col :span="3">
          <el-select v-model="filterChangeType" placeholder="变更类型" clearable style="width:100%">
            <el-option label="全部" value="" />
            <template v-if="filterModule !== 'CATALOG'">
              <el-option label="新增" value="CREATE" />
              <el-option label="变更" value="UPDATE" />
              <el-option label="续期" value="RENEW" />
              <el-option label="冻结" value="FREEZE" />
              <el-option label="解冻" value="UNFREEZE" />
              <el-option label="删除附件" value="DELETE_ATTACHMENT" />
              <el-option label="变更附件" value="REPLACE_ATTACHMENT" />
            </template>
            <template v-if="filterModule !== 'LICENSE'">
              <el-option label="新增试剂" value="CREATE" />
              <el-option label="编辑试剂" value="UPDATE" />
              <el-option label="删除试剂" value="DELETE" />
              <el-option label="停用试剂" value="DISABLE" />
            </template>
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-input v-model="filterKeyword" :placeholder="keywordPlaceholder" clearable :prefix-icon="Search" />
        </el-col>
        <el-col :span="10" style="text-align:right">
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 数据表格 -->
    <el-card>
      <el-table
        v-loading="licenseStore.changeLogLoading"
        :data="licenseStore.changeLogs"
        stripe
        size="small"
        style="width:100%"
      >
        <el-table-column label="模块" width="90">
          <template #default="{ row }">
            <el-tag :type="row.module === 'LICENSE' ? 'info' : 'success'" size="small" effect="plain">
              {{ row.moduleLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="变更时间" width="170" sortable />

        <!-- 证照专用列 -->
        <el-table-column v-if="showLicenseCols" prop="supplierName" label="供应商" width="140" />
        <el-table-column v-if="showLicenseCols" prop="certNo" label="证号" width="180" />
        <el-table-column v-if="showLicenseCols" prop="licenseTypeLabel" label="证照类型" width="100" />

        <!-- 试剂专用列 -->
        <el-table-column v-if="showCatalogCols" prop="reagentName" label="试剂名称" min-width="140" />
        <el-table-column v-if="showCatalogCols" prop="hisCode" label="院内编码" width="120" />

        <el-table-column label="变更类型" width="110">
          <template #default="{ row }">
            <el-tag :type="changeTypeTag(row.changeType, row.module)" size="small">
              {{ row.changeTypeLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="100" />

        <!-- 试剂审批状态 -->
        <el-table-column v-if="showCatalogCols" label="审批状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.reviewStatus" :type="reviewStatusTag(row.reviewStatus)" size="small">
              {{ row.reviewStatusLabel }}
            </el-tag>
            <span v-else style="color:#c0c4cc">—</span>
          </template>
        </el-table-column>

        <el-table-column label="备注信息" min-width="180">
          <template #default="{ row }">
            <span v-if="row.note || row.fieldChanges" style="color:#64748b;font-size:12px">
              {{ formatNote(row) }}
            </span>
            <span v-else style="color:#c0c4cc">—</span>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top:16px;text-align:center">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="licenseStore.changeLogTotal"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleQuery"
          @current-change="handleQuery"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Search, RefreshRight } from '@element-plus/icons-vue'
import { useLicenseStore } from '@/stores/license'

const licenseStore = useLicenseStore()

const filterModule = ref('')
const dateRange = ref(null)
const filterChangeType = ref('')
const filterKeyword = ref('')
const page = ref(1)
const pageSize = ref(20)

const keywordPlaceholder = computed(() => {
  if (filterModule.value === 'LICENSE') return '搜索 供应商 / 证号'
  if (filterModule.value === 'CATALOG') return '搜索 试剂名称 / 院内编码'
  return '搜索 供应商/证号/试剂/编码'
})

const showLicenseCols = computed(() => filterModule.value !== 'CATALOG')
const showCatalogCols = computed(() => filterModule.value !== 'LICENSE')

onMounted(() => { handleQuery() })

function onModuleChange() {
  filterChangeType.value = ''
  page.value = 1
  handleQuery()
}

function handleQuery() {
  const params = { page: page.value, pageSize: pageSize.value }
  if (filterModule.value) params.module = filterModule.value
  if (filterChangeType.value) params.changeType = filterChangeType.value
  if (filterKeyword.value) params.keyword = filterKeyword.value
  if (dateRange.value && dateRange.value.length === 2) {
    params.startDate = dateRange.value[0]
    params.endDate = dateRange.value[1]
  }
  licenseStore.fetchChangeLogs(params)
}

function handleReset() {
  filterModule.value = ''
  dateRange.value = null
  filterChangeType.value = ''
  filterKeyword.value = ''
  page.value = 1
  handleQuery()
}

function changeTypeTag(type, module) {
  if (module === 'CATALOG') {
    switch (type) {
      case 'CREATE': return 'success'
      case 'UPDATE': return 'info'
      case 'DELETE': return 'danger'
      case 'DISABLE': return 'info'
      default: return 'info'
    }
  }
  switch (type) {
    case 'CREATE': return 'success'
    case 'RENEW': return 'warning'
    case 'UPDATE': return 'info'
    case 'FREEZE': return 'info'
    case 'UNFREEZE': return 'success'
    case 'DELETE_ATTACHMENT': return 'danger'
    case 'REPLACE_ATTACHMENT': return 'warning'
    default: return 'info'
  }
}

function reviewStatusTag(status) {
  switch (status) {
    case 'APPROVED': return 'success'
    case 'PENDING': return 'warning'
    case 'REJECTED': return 'danger'
    default: return 'info'
  }
}

function formatNote(row) {
  // 试剂目录：优先展示 field_changes 摘要
  if (row.module === 'CATALOG' && row.fieldChanges) {
    try {
      const obj = JSON.parse(row.fieldChanges)
      const keys = Object.keys(obj)
      if (keys.length > 0) return `变更字段: ${keys.join('、')}`
    } catch { /* fall through */ }
    if (row.fieldChanges.length > 60) return row.fieldChanges.substring(0, 60) + '…'
    return row.fieldChanges
  }
  // 试剂审批意见
  if (row.reviewComment) return row.reviewComment
  // 证照 note
  if (!row.note) return ''
  try {
    const obj = JSON.parse(row.note)
    if (obj.fileName) return `删除附件: ${obj.fileName}`
    if (obj.oldFileName && obj.newFileName) return `替换: ${obj.oldFileName} → ${obj.newFileName}`
    return row.note
  } catch {
    return row.note
  }
}
</script>
