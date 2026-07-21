<template>
  <el-dialog
    v-model="visible"
    title="编辑物料"
    width="720px"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="130px"
      label-position="right"
      v-loading="loadingDetail"
    >
      <el-divider content-position="left">基础信息</el-divider>

      <el-form-item label="院内编码">
        <el-input :model-value="form.hisCode" disabled />
      </el-form-item>

      <el-form-item label="物料名称" prop="genericName">
        <el-input v-model="form.genericName" placeholder="请输入物料名称" maxlength="200" />
      </el-form-item>

      <el-form-item label="物料类型" prop="materialType">
        <el-select v-model="form.materialType" style="width: 100%">
          <el-option
            v-for="mt in materialTypeOptions"
            :key="mt.value"
            :label="mt.label"
            :value="mt.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="生产厂家">
        <el-select
          v-model="form.manufacturer"
          filterable
          remote
          reserve-keyword
          :remote-method="loadManufacturerOptions"
          :loading="manufacturerLoading"
          allow-create
          clearable
          placeholder="请选择或输入生产厂家"
          style="width: 100%"
        >
          <el-option
            v-for="item in manufacturerOptions"
            :key="item.id"
            :label="item.name"
            :value="item.name"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="配送商">
        <el-select
          v-model="form.distributor"
          filterable
          remote
          reserve-keyword
          :remote-method="loadDistributorOptions"
          :loading="distributorLoading"
          allow-create
          clearable
          placeholder="请选择或输入配送商"
          style="width: 100%"
        >
          <el-option
            v-for="item in distributorOptions"
            :key="item.id"
            :label="item.name"
            :value="item.name"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="规格">
        <el-input v-model="form.specification" placeholder="例：R1:60mL×1 R2:20mL×1" maxlength="100" />
      </el-form-item>

      <el-form-item label="单位">
        <el-select v-model="form.unit" placeholder="请选择单位" style="width: 100%">
          <el-option label="盒" value="盒" />
          <el-option label="瓶" value="瓶" />
          <el-option label="箱" value="箱" />
          <el-option label="包" value="包" />
          <el-option label="人份" value="人份" />
        </el-select>
      </el-form-item>

      <el-form-item label="储存条件">
        <el-input v-model="form.storageCondition" placeholder="例：2-8°C 冷藏" maxlength="100" />
      </el-form-item>

      <el-form-item label="适用组别">
        <el-select v-model="form.testGroups" multiple placeholder="请选择适用组别" style="width: 100%">
          <el-option label="生化" value="生化" />
          <el-option label="免疫" value="免疫" />
          <el-option label="临检" value="临检" />
          <el-option label="微生物" value="微生物" />
          <el-option label="分子" value="分子" />
        </el-select>
      </el-form-item>

      <el-divider content-position="left">五码信息</el-divider>

      <el-form-item v-if="showUdiFields" label="UDI-DI">
        <el-input v-model="form.udiDi" placeholder="14位数字" maxlength="128" />
      </el-form-item>

      <el-form-item v-if="showUdiFields" label="GTIN">
        <el-input v-model="form.gtin" placeholder="14位GTIN码" maxlength="14" />
      </el-form-item>

      <el-form-item v-if="showUdiFields" label="医保编码">
        <el-input v-model="form.insuranceCode" placeholder="27位数字" maxlength="27" />
      </el-form-item>

      <el-form-item v-if="showUdiFields" label="注册证号">
        <el-input v-model="form.registrationCertNo" placeholder="例：国械注准20253123456" maxlength="128" />
      </el-form-item>

      <el-form-item label="检测方法学">
        <el-input v-model="form.detectionMethod" placeholder="例：化学发光法" maxlength="100" />
      </el-form-item>

      <el-divider content-position="left">库存与规则</el-divider>

      <el-form-item v-if="showTestsPerKit" label="标称测试数">
        <el-input-number v-model="form.testsPerKit" :min="1" :max="999999" style="width: 100%" />
      </el-form-item>

      <el-form-item label="含税单价(元)">
        <el-input-number v-model="form.pricePerUnit" :min="0" :precision="2" :max="99999999" style="width: 100%" />
      </el-form-item>

      <el-form-item label="开瓶稳定天数">
        <el-input-number v-model="form.stabilityDaysAfterOpen" :min="0" :max="3650" style="width: 100%" />
      </el-form-item>

      <el-form-item label="采购提前期(天)">
        <el-input-number v-model="form.leadTimeDays" :min="1" :max="365" style="width: 100%" />
      </el-form-item>

      <el-form-item label="设备关联策略">
        <el-select v-model="form.deviceBindMode" style="width: 100%">
          <el-option label="强制关联" value="FORCE" />
          <el-option label="柔性关联" value="FLEXIBLE" />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        保 存
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useCatalogStore } from '@/stores/catalog'
import { getCatalogDetail } from '@/api/catalog'
import { materialTypeLabel } from '@/utils/format'
import { getManufacturerOptions } from '@/api/manufacturer'
import { getDistributorOptions } from '@/api/distributor'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  reagentId: { type: Number, default: null }
})

const emit = defineEmits(['update:modelValue', 'updated'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const catalogStore = useCatalogStore()
const formRef = ref(null)
const submitting = ref(false)
const loadingDetail = ref(false)

// ── 生产厂家 / 配送商远程搜索 ──
const manufacturerOptions = ref([])
const distributorOptions = ref([])
const manufacturerLoading = ref(false)
const distributorLoading = ref(false)

async function loadManufacturerOptions(keyword) {
  manufacturerLoading.value = true
  try {
    const list = await getManufacturerOptions(keyword || undefined)
    manufacturerOptions.value = list || []
  } catch { manufacturerOptions.value = [] }
  finally { manufacturerLoading.value = false }
}

async function loadDistributorOptions(keyword) {
  distributorLoading.value = true
  try {
    const list = await getDistributorOptions(keyword || undefined)
    distributorOptions.value = list || []
  } catch { distributorOptions.value = [] }
  finally { distributorLoading.value = false }
}

const form = reactive({
  hisCode: '',
  genericName: '',
  materialType: 'REAGENT',
  manufacturer: '',
  distributor: '',
  specification: '',
  unit: '',
  storageCondition: '',
  testGroups: [],
  udiDi: '',
  gtin: '',
  insuranceCode: '',
  registrationCertNo: '',
  detectionMethod: '',
  testsPerKit: null,
  pricePerUnit: null,
  stabilityDaysAfterOpen: null,
  leadTimeDays: 7,
  deviceBindMode: 'FORCE'
})

const rules = {
  genericName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }]
}

const materialTypeOptions = [
  { label: '试剂', value: 'REAGENT' },
  { label: '校准品', value: 'CALIBRATOR' },
  { label: '质控品', value: 'QC_MATERIAL' },
  { label: '耗材', value: 'CONSUMABLE' },
  { label: '其他', value: 'OTHER' }
]

const showUdiFields = computed(() =>
  ['REAGENT', 'CALIBRATOR', 'QC_MATERIAL'].includes(form.materialType)
)
const showTestsPerKit = computed(() => form.materialType === 'REAGENT')

watch(() => props.reagentId, async (id) => {
  if (id && visible.value) {
    await loadDetail(id)
  }
})

watch(visible, async (val) => {
  if (val) {
    loadManufacturerOptions('')
    loadDistributorOptions('')
    if (props.reagentId) {
      await loadDetail(props.reagentId)
    }
  }
})

async function loadDetail(id) {
  loadingDetail.value = true
  try {
    const data = await getCatalogDetail(id)
    form.hisCode = data.hisCode || ''
    form.genericName = data.genericName || ''
    form.materialType = data.materialType || 'REAGENT'
    form.manufacturer = data.manufacturer || ''
    form.distributor = data.distributor || ''
    form.specification = data.specification || ''
    form.unit = data.unit || ''
    form.storageCondition = data.storageCondition || ''
    form.testGroups = data.testGroups ? data.testGroups.split(',').filter(Boolean) : []
    form.udiDi = data.udiDi || ''
    form.gtin = data.gtin || ''
    form.insuranceCode = data.insuranceCode || ''
    form.registrationCertNo = data.registrationCertNo || ''
    form.detectionMethod = data.detectionMethod || ''
    form.testsPerKit = data.testsPerKit
    form.pricePerUnit = data.pricePerUnit
    form.stabilityDaysAfterOpen = data.stabilityDaysAfterOpen
    form.leadTimeDays = data.leadTimeDays ?? 7
    form.deviceBindMode = data.deviceBindMode || 'FORCE'
  } finally {
    loadingDetail.value = false
  }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      ...form,
      testGroups: Array.isArray(form.testGroups) ? form.testGroups.join(',') : form.testGroups
    }
    await catalogStore.update(props.reagentId, payload)
    ElMessage.success('物料编辑成功')
    emit('updated')
    visible.value = false
  } catch {
    // 错误由 request 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

function handleClosed() {
  formRef.value?.resetFields()
}
</script>
