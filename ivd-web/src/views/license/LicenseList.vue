<!--
  LicenseList.vue — M03 证照管理
  对照效果图 page-license (line 1072-1201)
  功能：证照 CRUD + 效期预警 + 附件管理 + MinIO 上传
-->
<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <el-statistic title="证照总数" :value="licenseStore.stats.total">
            <template #prefix><el-icon :size="24" color="#059669"><Stamp /></el-icon></template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card stat-card--success">
          <el-statistic title="有效期内" :value="licenseStore.stats.active">
            <template #suffix><span class="status-dot" style="background:#10b981"></span></template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card stat-card--warning">
          <el-statistic title="即将到期" :value="licenseStore.stats.expiring">
            <template #suffix><span class="status-pending-dot"></span></template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card stat-card--danger">
          <el-statistic title="已过期/冻结" :value="licenseStore.stats.expiredFrozen">
            <template #suffix><span class="status-dot" style="background:#ef4444"></span></template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card stat-card--info">
          <el-statistic title="合作供应商" :value="licenseStore.stats.supplierCount || 0" suffix="家">
            <template #prefix><el-icon :size="24" color="#3b82f6"><OfficeBuilding /></el-icon></template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>

    <!-- 工具栏 -->
    <el-card class="toolbar-card">
      <el-row :gutter="16" align="middle">
        <el-col :span="6">
          <el-input v-model="searchText" placeholder="搜索 供应商 / 证号" clearable :prefix-icon="Search" />
        </el-col>
        <el-col :span="3">
          <el-select v-model="filterType" placeholder="证照类型" clearable>
            <el-option label="产品注册证" value="PRODUCT_REG" />
            <el-option label="生产许可证" value="PRODUCTION" />
            <el-option label="经营许可证" value="OPERATION" />
            <el-option label="GMP 证书" value="GMP" />
            <el-option label="GSP 证书" value="GSP" />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-select v-model="filterStatus" placeholder="证照状态" clearable>
            <el-option label="有效" value="ACTIVE" />
            <el-option label="即将到期" value="EXPIRING" />
            <el-option label="已过期" value="EXPIRED" />
            <el-option label="已冻结" value="FROZEN" />
          </el-select>
        </el-col>
        <el-col :span="12" style="text-align:right">
          <el-button type="primary" :icon="Upload" @click="showUploadDialog = true">上传证照</el-button>
          <el-button :icon="Upload" @click="handleImportClick">批量导入</el-button>
          <el-dropdown @command="handleExport" style="margin-left:0">
            <el-button :icon="Download">
              导出报表 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="xlsx">Excel (.xlsx)</el-dropdown-item>
                <el-dropdown-item command="csv">CSV (.csv)</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <input
            ref="importFileRef"
            type="file"
            accept=".xlsx,.xls,.csv"
            style="display:none"
            @change="handleImportFile"
          />
        </el-col>
      </el-row>
    </el-card>

    <!-- ECharts 图表区 -->
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">证照效期分布</span></template>
          <div ref="pieChartRef" style="height:260px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">未来 3 个月到期时间轴</span></template>
          <div ref="barChartRef" style="height:260px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 规则联动警告条 -->
    <el-alert
      v-if="licenseStore.stats.expiring > 0"
      title="规则 LIC-02 触发：存在即将到期的证照，可能影响关联试剂采购。请尽快续期。"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom:16px"
    />

    <!-- 证照清单表格 -->
    <el-card shadow="never">
      <el-table :data="licenseStore.list" stripe v-loading="licenseStore.loading" style="width:100%">
        <el-table-column label="供应商" min-width="150">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <el-icon :size="20" color="#059669"><OfficeBuilding /></el-icon>
              <span>{{ row.supplierName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="certNo" label="证号" width="180" show-overflow-tooltip />
        <el-table-column label="证照类型" width="130">
          <template #default="{ row }">
            <el-tag size="small">
              <el-icon style="margin-right:3px;vertical-align:middle"><component :is="licenseTypeIcon(row.licenseType)" /></el-icon>
              {{ row.licenseTypeLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="relatedReagents" label="关联试剂" min-width="120" show-overflow-tooltip />
        <el-table-column prop="issueDate" label="发证日期" width="110" />
        <el-table-column prop="expireDate" label="有效期至" width="110">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.remainingDays <= 90 }">{{ row.expireDate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="剩余天数" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.remainingDays <= 30 ? 'danger' : row.remainingDays <= 90 ? 'warning' : 'success'"
              size="small" effect="dark"
            >{{ row.remainingDays }} 天</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="附件" width="70" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.attachmentCount > 0" color="#3b82f6"><Paperclip /></el-icon>
            <span v-else style="color:#cbd5e1">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="View" @click="handleViewDetail(row.id)">详情</el-button>
            <el-button link type="primary" size="small" :icon="Edit" @click="handleEdit(row.id)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page" v-model:page-size="pageSize"
        :total="licenseStore.total" :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top:16px; justify-content:flex-end" @change="fetchData"
      />
    </el-card>

    <!-- 证照明细弹窗 (960px) -->
    <el-dialog v-model="showDetailDialog" title="证照明细" width="960px" top="5vh" @closed="detailData = null">
      <template v-if="detailData">
        <el-descriptions title="基本信息" :column="3" border size="small" style="margin-bottom:20px">
          <el-descriptions-item label="供应商">{{ detailData.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="证照类型">{{ detailData.licenseTypeLabel }}</el-descriptions-item>
          <el-descriptions-item label="证号">{{ detailData.certNo }}</el-descriptions-item>
          <el-descriptions-item label="发证机关">{{ detailData.issuingAuthority || '—' }}</el-descriptions-item>
          <el-descriptions-item label="发证日期">{{ detailData.issueDate }}</el-descriptions-item>
          <el-descriptions-item label="有效期至">
            <el-tag :type="detailData.remainingDays <= 90 ? 'warning' : 'success'" size="small">
              {{ detailData.expireDate }} ({{ detailData.remainingDays }}天)
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">关联试剂</el-divider>
        <el-table :data="detailData.relatedReagents || []" size="small" style="margin-bottom:20px" empty-text="暂未关联试剂">
          <el-table-column prop="hisCode" label="院内编码" width="120" />
          <el-table-column prop="genericName" label="试剂名称" min-width="140" />
          <el-table-column prop="manufacturer" label="厂家" width="120" />
          <el-table-column label="默认供应商" width="100">
            <template #default="{ row }"><el-tag v-if="row.isDefault" type="success" size="small">默认</el-tag></template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left">附件档案</el-divider>
        <div v-if="detailData.attachments && detailData.attachments.length > 0" class="attachment-list">
          <div v-for="(att, i) in detailData.attachments" :key="i" class="attachment-item">
            <el-icon :size="24" :color="att.type === 'pdf' ? '#ef4444' : '#3b82f6'">
              <Document v-if="att.type === 'pdf'" /><PictureFilled v-else />
            </el-icon>
            <div class="attachment-info">
              <span class="attachment-name">{{ att.name }}</span>
              <span class="attachment-meta">{{ formatFileSize(att.size) }} · {{ att.uploadedAt }}</span>
            </div>
            <el-button link type="primary" size="small" @click="previewAttachment(att)">预览</el-button>
          </div>
        </div>
        <el-empty v-else description="暂无附件" :image-size="40" />

        <el-divider content-position="left">变更记录</el-divider>
        <el-timeline v-if="detailData.changeLogs && detailData.changeLogs.length > 0">
          <el-timeline-item
            v-for="log in detailData.changeLogs" :key="log.id"
            :timestamp="log.createdAt" placement="top"
            :color="log.changeType === 'CREATE' ? '#10b981' : '#3b82f6'"
          >
            <p>{{ log.changeTypeLabel }} — {{ log.operatorName }}</p>
            <p v-if="log.note" style="color:#94a3b8;font-size:12px">{{ log.note }}</p>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无变更记录" :image-size="40" />
      </template>
      <el-skeleton v-else :rows="8" animated />
    </el-dialog>

    <!-- 上传/编辑证照弹窗 -->
    <el-dialog v-model="showUploadDialog" :title="editingLicenseId ? '编辑证照' : '上传证照'" width="560px" @closed="resetUploadForm">
      <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="100px">
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="uploadForm.supplierId" placeholder="请选择供应商" style="width:100%">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="证照类型" prop="licenseType">
          <el-select v-model="uploadForm.licenseType" placeholder="请选择证照类型" style="width:100%">
            <el-option label="产品注册证" value="PRODUCT_REG" />
            <el-option label="生产许可证" value="PRODUCTION" />
            <el-option label="经营许可证" value="OPERATION" />
            <el-option label="GMP 证书" value="GMP" />
            <el-option label="GSP 证书" value="GSP" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="证号" prop="certNo">
          <el-input v-model="uploadForm.certNo" placeholder="请输入证号" />
        </el-form-item>
        <el-form-item label="发证机关">
          <el-input v-model="uploadForm.issuingAuthority" placeholder="请输入发证机关" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="发证日期" prop="issueDate">
              <el-date-picker v-model="uploadForm.issueDate" type="date" placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期至" prop="expireDate">
              <el-date-picker v-model="uploadForm.expireDate" type="date" placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="证照附件">
          <el-upload ref="uploadFilesRef" drag :auto-upload="false" :limit="3" accept=".pdf,.jpg,.jpeg,.png">
            <el-icon :size="40" color="#059669"><UploadFilled /></el-icon>
            <div style="font-size:13px;color:#475569;margin-top:8px">拖拽文件到此处，或点击上传</div>
            <template #tip><div style="font-size:12px;color:#94a3b8;margin-top:4px">支持 PDF / JPG / PNG，单文件不超过 20MB</div></template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取 消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">确认上传</el-button>
      </template>
    </el-dialog>

    <!-- 附件预览弹窗 -->
    <el-dialog v-model="showPreviewDialog" title="附件预览" width="800px" top="5vh" @closed="revokePreviewUrl()">
      <div v-if="previewItem" class="preview-container">
        <div v-if="previewLoading" style="text-align:center;padding:60px">
          <el-icon :size="48" class="is-loading"><Loading /></el-icon>
          <p style="margin-top:12px;color:#94a3b8">加载中...</p>
        </div>
        <template v-else-if="previewItem.type !== 'pdf'">
          <img v-if="previewUrl" :src="previewUrl" alt="预览" style="max-width:100%" />
          <el-empty v-else description="加载失败，请重试" :image-size="80" />
        </template>
        <div v-else style="text-align:center;padding:40px">
          <el-icon :size="64" color="#ef4444"><Document /></el-icon>
          <p style="color:#475569;margin-top:12px">{{ previewItem.name }}</p>
          <el-button type="primary" style="margin-top:16px" @click="downloadAttachment">下载查看</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Search, Upload, Download, View, Edit, Stamp, OfficeBuilding, Document, Medal, Checked,
  Paperclip, Document as DocIcon, PictureFilled, UploadFilled, InfoFilled, ArrowDown, Loading
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { useLicenseStore } from '@/stores/license'
import { getSuppliers } from '@/api/license'
import request from '@/utils/request'

const licenseStore = useLicenseStore()

const searchText = ref('')
const filterType = ref('')
const filterStatus = ref('')
const page = ref(1)
const pageSize = ref(10)

// 图表
const pieChartRef = ref(null)
const barChartRef = ref(null)
let pieChart = null
let barChart = null

// 明细弹窗
const showDetailDialog = ref(false)
const detailData = ref(null)

// 上传弹窗
const showUploadDialog = ref(false)
const uploading = ref(false)
const uploadFormRef = ref(null)
const uploadFilesRef = ref(null)
const editingLicenseId = ref(null)
const suppliers = ref([])

async function loadSuppliers() {
  try {
    const res = await getSuppliers()
    suppliers.value = res || []
  } catch {
    // keep empty array on failure
  }
}

const uploadForm = reactive({
  supplierId: null, licenseType: '', certNo: '', issuingAuthority: '', issueDate: null, expireDate: null
})
const uploadRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  licenseType: [{ required: true, message: '请选择证照类型', trigger: 'change' }],
  certNo: [{ required: true, message: '请输入证号', trigger: 'blur' }],
  issueDate: [{ required: true, message: '请选择发证日期', trigger: 'change' }],
  expireDate: [{ required: true, message: '请选择有效期', trigger: 'change' }]
}

// 预览弹窗
const showPreviewDialog = ref(false)
const previewLoading = ref(false)
const previewItem = ref(null)
const previewUrl = ref('')

let searchTimer = null
watch(searchText, () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => { page.value = 1; fetchData() }, 300) })
watch([filterType, filterStatus], () => { page.value = 1; fetchData() })

onMounted(() => {
  fetchData(); licenseStore.fetchStats(); licenseStore.fetchExpiryAlerts(); loadSuppliers()
  nextTick(() => initCharts())
})
onBeforeUnmount(() => { pieChart?.dispose(); barChart?.dispose() })

async function fetchData() {
  const params = { page: page.value, pageSize: pageSize.value }
  if (filterType.value) params.licenseType = filterType.value
  if (filterStatus.value) params.status = filterStatus.value
  await licenseStore.fetchList(params)
  nextTick(() => renderCharts())
}

function initCharts() {
  if (pieChartRef.value) pieChart = echarts.init(pieChartRef.value)
  if (barChartRef.value) barChart = echarts.init(barChartRef.value)
  renderCharts()
}

function renderCharts() {
  if (!pieChart) return
  const stats = licenseStore.stats
  pieChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['50%', '75%'], center: ['50%', '45%'],
      label: { show: false },
      data: [
        { value: stats.active || 0, name: '有效期内', itemStyle: { color: '#10b981' } },
        { value: stats.expiring || 0, name: '即将到期', itemStyle: { color: '#f59e0b' } },
        { value: stats.expiredFrozen || 0, name: '已过期/冻结', itemStyle: { color: '#ef4444' } }
      ]
    }]
  })
  barChart?.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['本月', '下月', '第三月'] },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', barWidth: 32, itemStyle: { color: '#f59e0b', borderRadius: [4, 4, 0, 0] }, data: [3, 5, 4] }]
  })
}

async function handleViewDetail(id) {
  showDetailDialog.value = true; detailData.value = null
  try { detailData.value = await licenseStore.fetchDetail(id) } catch { ElMessage.error('加载证照明细失败') }
}

function handleEdit(id) {
  editingLicenseId.value = id
  licenseStore.fetchDetail(id).then(data => {
    Object.assign(uploadForm, {
      supplierId: data.supplierId, licenseType: data.licenseType,
      certNo: data.certNo, issuingAuthority: data.issuingAuthority || '',
      issueDate: data.issueDate, expireDate: data.expireDate
    })
    showUploadDialog.value = true
  }).catch(() => ElMessage.error('加载证照明细失败'))
}

async function handleUpload() {
  const valid = await uploadFormRef.value?.validate().catch(() => false)
  if (!valid) return
  uploading.value = true
  try {
    // 1. 创建/更新证照元数据
    let licenseId = editingLicenseId.value
    if (licenseId) {
      await licenseStore.update(licenseId, { ...uploadForm, operatorId: 1, operatorName: '管理员' })
    } else {
      const created = await licenseStore.create({ ...uploadForm, operatorId: 1, operatorName: '管理员' })
      licenseId = created?.id
    }

    // 2. 上传选中的附件
    const files = uploadFilesRef.value?.uploadFiles || []
    for (const f of files) {
      if (f.raw) {
        const formData = new FormData()
        formData.append('file', f.raw)
        try {
          await request.post(`/license/${licenseId}/upload`, formData)
        } catch { /* 单个文件上传失败不中断流程 */ }
      }
    }

    ElMessage.success(editingLicenseId.value ? '证照更新成功' : '证照上传成功')
    showUploadDialog.value = false
    fetchData(); licenseStore.fetchStats()
  } catch { /* handled */ } finally { uploading.value = false }
}

function resetUploadForm() {
  editingLicenseId.value = null
  uploadFormRef.value?.resetFields()
  uploadFilesRef.value?.clearFiles()
  Object.assign(uploadForm, { supplierId: null, licenseType: '', certNo: '', issuingAuthority: '', issueDate: null, expireDate: null })
}

// ─── 导入/导出 ───

const importFileRef = ref(null)

function handleImportClick() {
  importFileRef.value?.click()
}

async function handleImportFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const formData = new FormData()
  formData.append('file', file)
  formData.append('operatorId', '1')
  formData.append('operatorName', '管理员')
  try {
    const res = await request({
      url: '/license/import', method: 'post', data: formData
    })
    ElMessage.success(`证照导入完成：成功 ${res.success} 条，跳过 ${res.skipped} 条`)
    fetchData(); licenseStore.fetchStats()
  } catch {
    // handled by interceptor
  } finally {
    importFileRef.value.value = ''
  }
}

async function handleExport(format) {
  try {
    const res = await request({
      url: '/license/export',
      method: 'get',
      params: { format },
      responseType: 'blob'
    })
    const blob = res.data || res
    const ext = format === 'csv' ? 'csv' : 'xlsx'
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `证照列表.${ext}`
    a.click()
    URL.revokeObjectURL(a.href)
  } catch {
    ElMessage.error('导出失败')
  }
}

async function previewAttachment(att) {
  // 释放旧 blob URL，防止内存泄漏
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
  previewItem.value = att
  previewLoading.value = true
  showPreviewDialog.value = true

  if (!detailData.value?.id || !att.objectKey) {
    previewLoading.value = false
    return
  }

  try {
    const blob = await request.get(
      `/license/${detailData.value.id}/file/${encodeURIComponent(att.objectKey)}`,
      { responseType: 'blob' }
    )
    // 检测错误响应：后端异常会返回 200 OK JSON 而非实际图片
    if (blob.type === 'application/json' || blob.type === '') {
      const text = await blob.text()
      try {
        const err = JSON.parse(text)
        throw new Error(err.message || '服务器返回错误')
      } catch (parseErr) {
        if (parseErr.message !== '服务器返回错误') {
          throw new Error('服务器返回了非预期的响应格式')
        }
        throw parseErr
      }
    }
    previewUrl.value = URL.createObjectURL(blob)
  } catch (e) {
    console.error('附件预览失败:', e)
    ElMessage.error('附件预览失败: ' + (e.message || '未知错误'))
  } finally {
    previewLoading.value = false
  }
}

async function downloadAttachment() {
  if (!detailData.value?.id || !previewItem.value?.objectKey) return
  try {
    const blob = await request.get(
      `/license/${detailData.value.id}/file/${encodeURIComponent(previewItem.value.objectKey)}`,
      { responseType: 'blob' }
    )
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = previewItem.value.name || 'attachment'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (e) {
    console.error('下载附件失败:', e)
  }
}

function revokePreviewUrl() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
  previewLoading.value = false
}

function formatFileSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']; let i = 0; let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(1) + ' ' + units[i]
}

function statusTagType(s) {
  return { ACTIVE: 'success', EXPIRING: 'warning', EXPIRED: 'danger', FROZEN: 'info' }[s] || ''
}

function licenseTypeIcon(type) {
  const icons = { PRODUCT_REG: Stamp, PRODUCTION: Medal, OPERATION: Stamp, GMP: Checked, GSP: Checked, OTHER: InfoFilled }
  return icons[type] || Document
}</script>

<style scoped>
.stat-card { border-radius: var(--radius-md); transition: transform 0.2s, box-shadow 0.2s; }
.stat-card:hover { transform: translateY(-2px); }
.stat-card--success { border-top: 3px solid var(--ivd-success); }
.stat-card--warning { border-top: 3px solid var(--ivd-warning); }
.stat-card--info { border-top: 3px solid var(--ivd-info); }
.text-danger { color: var(--ivd-danger); font-weight: 500; }

.attachment-list { display: flex; flex-direction: column; gap: 8px; }
.attachment-item {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 12px; border: 1px solid var(--border-color);
  border-radius: var(--radius-sm); background: var(--bg-page);
}
.attachment-info { flex: 1; display: flex; flex-direction: column; }
.attachment-name { font-size: 13px; font-weight: 500; color: var(--text-primary); }
.attachment-meta { font-size: 11px; color: var(--text-tertiary); }
.preview-container { min-height: 300px; display: flex; align-items: center; justify-content: center; }
:deep(.el-table th) { background: var(--bg-page); color: var(--text-secondary); font-weight: 600; }
</style>
