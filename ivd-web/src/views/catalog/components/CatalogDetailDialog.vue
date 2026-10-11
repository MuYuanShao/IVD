<template>
  <el-dialog v-model="visible" width="min(1120px, 94vw)" top="3vh" append-to-body destroy-on-close class="catalog-detail-dialog" @closed="resetState">
    <template #header>
      <div class="dialog-heading">
        <div class="title-mark"><el-icon><Document /></el-icon></div>
        <div>
          <div class="eyebrow">REAGENT MASTER DATA</div>
          <h2>{{ detail?.genericName || '试剂目录详情' }}</h2>
          <div class="heading-meta">
            <span>{{ detail?.hisCode || '—' }}</span>
            <el-tag v-if="detail" :type="approvalTagType(detail.approvalStatus)" size="small">{{ detail.approvalStatusLabel || detail.approvalStatus }}</el-tag>
          </div>
        </div>
      </div>
    </template>

    <div v-loading="loading" class="detail-body">
      <template v-if="detail">
        <el-alert v-if="detail.editingDraft" title="当前展示待审批草稿，正式业务仍使用审批前的字段值。" type="warning" :closable="false" show-icon />
        <section class="detail-section">
          <div class="section-title"><span>01</span><div><strong>基础信息</strong><small>目录身份、产品属性与使用配置</small></div></div>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="目录ID">{{ display(detail.id) }}</el-descriptions-item>
            <el-descriptions-item label="院内编码">{{ display(detail.hisCode) }}</el-descriptions-item>
            <el-descriptions-item label="通用名称">{{ display(detail.genericName) }}</el-descriptions-item>
            <el-descriptions-item label="物料类型">{{ display(detail.materialTypeLabel || detail.materialType) }}</el-descriptions-item>
            <el-descriptions-item label="生产厂家">{{ display(detail.manufacturer) }}</el-descriptions-item>
            <el-descriptions-item label="配送商">{{ display(detail.distributor) }}</el-descriptions-item>
            <el-descriptions-item label="规格">{{ display(detail.specification) }}</el-descriptions-item>
            <el-descriptions-item label="单位">{{ display(detail.unit) }}</el-descriptions-item>
            <el-descriptions-item label="储存条件">{{ display(detail.storageCondition) }}</el-descriptions-item>
            <el-descriptions-item label="适用组别">{{ display(detail.testGroups) }}</el-descriptions-item>
            <el-descriptions-item label="检测方法学">{{ display(detail.detectionMethod) }}</el-descriptions-item>
            <el-descriptions-item label="标称测试数">{{ display(detail.testsPerKit) }}</el-descriptions-item>
            <el-descriptions-item label="开瓶稳定天数">{{ withUnit(detail.stabilityDaysAfterOpen, '天') }}</el-descriptions-item>
            <el-descriptions-item label="采购提前期">{{ withUnit(detail.leadTimeDays, '天') }}</el-descriptions-item>
            <el-descriptions-item label="设备关联策略">{{ display(detail.deviceBindModeLabel || detail.deviceBindMode) }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDateTime(detail.updatedAt) }}</el-descriptions-item>
            <el-descriptions-item label="审批状态">{{ display(detail.approvalStatusLabel || detail.approvalStatus) }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-section">
          <div class="section-title"><span>02</span><div><strong>编码与合规</strong><small>UDI、NMPA、医保与收费映射</small></div></div>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="UDI-DI">{{ display(detail.udiDi) }}</el-descriptions-item>
            <el-descriptions-item label="GTIN">{{ display(detail.gtin) }}</el-descriptions-item>
            <el-descriptions-item label="注册证号">{{ display(detail.registrationCertNo) }}</el-descriptions-item>
            <el-descriptions-item label="医保编码">{{ display(detail.insuranceCode) }}</el-descriptions-item>
            <el-descriptions-item label="唯一盒码管控">{{ yesNo(detail.enableUniqueCode) }}</el-descriptions-item>
            <el-descriptions-item label="医保支付">{{ yesNo(detail.isInsurancePayment) }}</el-descriptions-item>
            <el-descriptions-item label="医保支付类型">{{ display(detail.insurancePaymentType) }}</el-descriptions-item>
            <el-descriptions-item label="是否收费">{{ yesNo(detail.isChargeable) }}</el-descriptions-item>
            <el-descriptions-item label="收费编码">{{ display(detail.chargeCode) }}</el-descriptions-item>
            <el-descriptions-item label="是否带量采购">{{ yesNo(detail.isVolumeBased) }}</el-descriptions-item>
            <el-descriptions-item label="带量类型">{{ display(detail.volumeType) }}</el-descriptions-item>
            <el-descriptions-item label="含税单价">{{ money(detail.pricePerUnit) }}</el-descriptions-item>
          </el-descriptions>
          <div class="mapping-strip" v-if="detail.fiveCodeMapping">
            <div v-for="item in fiveCodeItems" :key="item.label" class="mapping-item">
              <small>{{ item.label }}</small><strong>{{ display(item.value) }}</strong>
            </div>
            <el-tag :type="detail.fiveCodeMapping.mappingStatus === 'COMPLETE' ? 'success' : 'warning'" size="small">
              {{ detail.fiveCodeMapping.mappingStatusLabel || detail.fiveCodeMapping.mappingStatus || '映射状态未知' }}
            </el-tag>
          </div>
          <el-empty v-else description="暂无五码映射" :image-size="46" />
        </section>

        <section class="detail-section license-section">
          <div class="section-title section-title--split">
            <div class="section-title-main"><span>03</span><div><strong>相关证照</strong><small>按试剂关联供应商汇总，附件支持在线阅览</small></div></div>
            <el-tag type="info" effect="plain">{{ licenses.length }} 张证照</el-tag>
          </div>
          <div v-if="licenses.length" class="license-grid">
            <article v-for="license in licenses" :key="license.id" class="license-card">
              <div class="license-card__header">
                <div class="license-icon"><el-icon><Stamp /></el-icon></div>
                <div class="license-identity">
                  <strong>{{ license.licenseTypeLabel || license.licenseType }}</strong>
                  <span>{{ license.certNo }}</span>
                </div>
                <el-tag :type="licenseStatusType(license.status)" size="small">{{ license.statusLabel || license.status }}</el-tag>
              </div>
              <el-descriptions :column="2" size="small" class="license-meta">
                <el-descriptions-item label="供应商">{{ display(license.supplierName) }}</el-descriptions-item>
                <el-descriptions-item label="发证机关">{{ display(license.issuingAuthority) }}</el-descriptions-item>
                <el-descriptions-item label="发证日期">{{ display(license.issueDate) }}</el-descriptions-item>
                <el-descriptions-item label="有效期至">{{ display(license.expireDate) }}</el-descriptions-item>
                <el-descriptions-item label="剩余天数">{{ withUnit(license.remainingDays, '天') }}</el-descriptions-item>
                <el-descriptions-item label="更新时间">{{ formatDateTime(license.updatedAt) }}</el-descriptions-item>
              </el-descriptions>
              <div class="attachment-block">
                <div class="attachment-title"><span>附件档案</span><small>{{ license.attachments?.length || 0 }} 个文件</small></div>
                <button v-for="att in (license.attachments || [])" :key="att.objectKey" type="button" class="attachment-row" @click="previewAttachment(license, att)">
                  <el-icon :class="fileIconClass(att)"><Document v-if="fileExtension(att) === 'pdf'" /><PictureFilled v-else /></el-icon>
                  <span class="attachment-name">{{ att.name }}</span>
                  <span class="attachment-size">{{ fileSize(att.size) }}</span>
                  <span class="preview-action"><el-icon><View /></el-icon>阅览</span>
                </button>
                <el-empty v-if="!license.attachments?.length" description="该证照暂无附件" :image-size="38" />
              </div>
            </article>
          </div>
          <el-empty v-else description="未找到与该试剂供应商关联的证照" :image-size="70" />
        </section>
      </template>
      <el-empty v-else-if="!loading" description="未获取到试剂详情" />
    </div>

    <el-dialog v-model="previewVisible" append-to-body width="min(1040px, 94vw)" top="4vh" class="attachment-preview-dialog" @closed="clearPreview">
      <template #header>
        <div class="preview-heading"><div><strong>{{ previewName }}</strong><small>{{ previewMime || '附件预览' }}</small></div><el-button type="primary" plain @click="downloadPreview"><el-icon><Download /></el-icon>下载</el-button></div>
      </template>
      <div v-loading="previewLoading" class="preview-canvas">
        <iframe v-if="previewUrl && previewMime === 'application/pdf'" :src="previewUrl" title="证照附件 PDF 预览" />
        <el-image v-else-if="previewUrl && previewMime.startsWith('image/')" :src="previewUrl" fit="contain" :preview-src-list="[previewUrl]" />
        <div v-else-if="previewUrl" class="unsupported-preview"><el-icon><Document /></el-icon><p>该格式暂不支持内嵌预览，请下载查看。</p></div>
        <el-empty v-else-if="!previewLoading" description="附件加载失败" />
      </div>
    </el-dialog>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Download, PictureFilled, Stamp, View } from '@element-plus/icons-vue'
import { getCatalogDetail } from '@/api/catalog'
import { getLicenseList, getLicenseDetail } from '@/api/license'
import request from '@/utils/request'

const props = defineProps({ modelValue: Boolean, reagentId: { type: [String, Number], default: null } })
const emit = defineEmits(['update:modelValue'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const detail = ref(null)
const licenses = ref([])
const loading = ref(false)
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewUrl = ref('')
const previewBlob = ref(null)
const previewName = ref('')
const previewMime = ref('')

const fiveCodeItems = computed(() => {
  const m = detail.value?.fiveCodeMapping || {}
  return [
    { label: 'HIS 编码', value: m.hisCode }, { label: 'UDI-DI', value: m.udiDi },
    { label: 'GTIN', value: m.gtin }, { label: '医保编码', value: m.insuranceCode },
    { label: 'NMPA 注册证号', value: m.nmpaRegNo }
  ]
})

watch(() => [props.modelValue, props.reagentId], ([open, id]) => { if (open && id) loadDetail(id) }, { immediate: true })

async function loadDetail(id) {
  loading.value = true
  detail.value = null
  licenses.value = []
  try {
    const [catalog, licensePage] = await Promise.all([
      getCatalogDetail(id),
      getLicenseList({ reagentId: id, page: 1, pageSize: 100 })
    ])
    detail.value = catalog
    const rows = licensePage?.records || []
    licenses.value = await Promise.all(rows.map(async row => {
      try { return await getLicenseDetail(row.id) } catch { return { ...row, attachments: [] } }
    }))
  } catch (error) {
    console.error('加载试剂目录详情失败:', error)
    ElMessage.error('加载试剂目录详情失败')
  } finally {
    loading.value = false
  }
}

async function previewAttachment(license, attachment) {
  clearPreview()
  previewVisible.value = true
  previewLoading.value = true
  previewName.value = attachment.name || '证照附件'
  try {
    const blob = await request({ url: `/license/${license.id}/file/${encodeURIComponent(attachment.objectKey)}`, method: 'get', responseType: 'blob' })
    previewBlob.value = blob
    previewMime.value = resolveMime(blob.type, attachment)
    previewUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    console.error('证照附件预览失败:', error)
    ElMessage.error('证照附件预览失败')
  } finally {
    previewLoading.value = false
  }
}

function downloadPreview() {
  if (!previewBlob.value) return
  const url = URL.createObjectURL(previewBlob.value)
  const link = document.createElement('a')
  link.href = url
  link.download = previewName.value || 'attachment'
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
function resolveMime(mime, attachment) {
  if (mime && mime !== 'application/octet-stream') return mime
  const ext = fileExtension(attachment)
  if (ext === 'pdf') return 'application/pdf'
  if (['jpg', 'jpeg'].includes(ext)) return 'image/jpeg'
  if (ext === 'png') return 'image/png'
  return mime || 'application/octet-stream'
}
function clearPreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  previewBlob.value = null
  previewName.value = ''
  previewMime.value = ''
  previewLoading.value = false
}
function resetState() { detail.value = null; licenses.value = []; clearPreview() }
onBeforeUnmount(clearPreview)

function display(value) { return value === null || value === undefined || value === '' ? '—' : value }
function withUnit(value, unit) { return value === null || value === undefined || value === '' ? '—' : `${value} ${unit}` }
function yesNo(value) { return Number(value) === 1 || value === true ? '是' : '否' }
function money(value) { return value === null || value === undefined || value === '' ? '—' : `¥ ${Number(value).toFixed(2)}` }
function formatDateTime(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '—' }
function fileExtension(att) { return (att?.type || att?.name?.split('.').pop() || '').toLowerCase() }
function fileIconClass(att) { return fileExtension(att) === 'pdf' ? 'file-icon file-icon--pdf' : 'file-icon file-icon--image' }
function fileSize(bytes) { const n = Number(bytes); if (!n) return '0 B'; return n < 1024 * 1024 ? `${(n / 1024).toFixed(1)} KB` : `${(n / 1024 / 1024).toFixed(1)} MB` }
function approvalTagType(status) { return { APPROVED: 'success', PENDING: 'warning', REJECTED: 'danger', DRAFT: 'info' }[status] || 'info' }
function licenseStatusType(status) { return { ACTIVE: 'success', EXPIRING: 'warning', EXPIRED: 'danger', FROZEN: 'info' }[status] || 'info' }
</script>

<style scoped>
.detail-body { min-height: 260px; max-height: 78vh; overflow: auto; padding: 0 4px 18px; }
.dialog-heading { display: flex; align-items: center; gap: 14px; }
.title-mark { width: 46px; height: 46px; display: grid; place-items: center; border-radius: 12px; color: #047857; background: linear-gradient(145deg, #ecfdf5, #d1fae5); font-size: 22px; }
.eyebrow { color: #059669; font-size: 10px; letter-spacing: .16em; font-weight: 700; }
.dialog-heading h2 { margin: 2px 0; font-size: 20px; color: var(--text-primary); }
.heading-meta { display: flex; gap: 10px; align-items: center; color: var(--text-tertiary); font-size: 12px; }
.detail-section { margin-top: 22px; }
.detail-section:first-child { margin-top: 4px; }
.section-title, .section-title-main { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.section-title > span, .section-title-main > span { font: 700 20px/1 Georgia, serif; color: #a7f3d0; }
.section-title strong { display: block; color: var(--text-primary); font-size: 14px; }
.section-title small { display: block; color: var(--text-tertiary); font-size: 11px; margin-top: 2px; }
.section-title--split { justify-content: space-between; }
.mapping-strip { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)) auto; gap: 10px; align-items: center; padding: 14px; margin-top: 12px; border: 1px solid #d1fae5; border-radius: 10px; background: #f7fffb; }
.mapping-item { min-width: 0; }
.mapping-item small { display: block; color: var(--text-tertiary); font-size: 10px; margin-bottom: 4px; }
.mapping-item strong { display: block; color: #334155; font-size: 12px; overflow-wrap: anywhere; }
.license-section { padding-top: 4px; }
.license-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.license-card { border: 1px solid #e2e8f0; border-radius: 12px; padding: 16px; background: #fff; box-shadow: 0 6px 20px rgba(15, 23, 42, .04); }
.license-card__header { display: flex; align-items: center; gap: 10px; padding-bottom: 12px; border-bottom: 1px solid #eef2f7; }
.license-icon { width: 36px; height: 36px; display: grid; place-items: center; border-radius: 9px; background: #eff6ff; color: #2563eb; font-size: 18px; }
.license-identity { min-width: 0; flex: 1; }
.license-identity strong, .license-identity span { display: block; }
.license-identity strong { font-size: 13px; color: var(--text-primary); }
.license-identity span { margin-top: 3px; color: var(--text-tertiary); font-size: 11px; overflow-wrap: anywhere; }
.license-meta { margin-top: 12px; }
.attachment-block { margin-top: 10px; }
.attachment-title { display: flex; justify-content: space-between; color: var(--text-secondary); font-size: 12px; font-weight: 600; margin-bottom: 7px; }
.attachment-title small { color: var(--text-tertiary); font-weight: 400; }
.attachment-row { width: 100%; border: 1px solid #e2e8f0; border-radius: 8px; padding: 9px 10px; background: #f8fafc; display: flex; align-items: center; gap: 8px; color: inherit; cursor: pointer; text-align: left; transition: border-color .15s, background .15s; }
.attachment-row + .attachment-row { margin-top: 6px; }
.attachment-row:hover { border-color: #60a5fa; background: #eff6ff; }
.file-icon--pdf { color: #ef4444; }.file-icon--image { color: #2563eb; }
.attachment-name { min-width: 0; flex: 1; font-size: 12px; color: #334155; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.attachment-size { color: #94a3b8; font-size: 10px; }
.preview-action { color: #2563eb; font-size: 11px; display: inline-flex; gap: 3px; align-items: center; }
.preview-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.preview-heading strong, .preview-heading small { display: block; }
.preview-heading strong { max-width: 760px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.preview-heading small { margin-top: 3px; color: var(--text-tertiary); font-size: 11px; }
.preview-canvas { min-height: 520px; height: 72vh; display: grid; place-items: center; border-radius: 10px; overflow: hidden; background: #eef2f7; }
.preview-canvas iframe { width: 100%; height: 100%; border: 0; background: #fff; }
.preview-canvas :deep(.el-image) { width: 100%; height: 100%; }
.unsupported-preview { text-align: center; color: #64748b; }
.unsupported-preview .el-icon { font-size: 64px; color: #94a3b8; }
:deep(.el-descriptions__label) { width: 116px; color: #64748b !important; font-weight: 500 !important; }
:deep(.el-descriptions__content) { color: #1e293b !important; overflow-wrap: anywhere; }
@media (max-width: 900px) { .license-grid { grid-template-columns: 1fr; }.mapping-strip { grid-template-columns: repeat(2, 1fr); }.mapping-strip .el-tag { justify-self: start; } }
</style>
