<template>
  <el-dialog
    v-model="visible"
    title="新增物料"
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
    >
      <el-divider content-position="left">基础信息</el-divider>

      <el-form-item label="院内编码" prop="hisCode">
        <el-input v-model="form.hisCode" placeholder="例：IVD-BIO-001" maxlength="50" />
      </el-form-item>

      <el-form-item label="物料名称" prop="genericName">
        <el-input v-model="form.genericName" placeholder="请输入物料名称" maxlength="200" />
      </el-form-item>

      <el-form-item label="物料类型" prop="materialType">
        <el-select v-model="form.materialType" placeholder="请选择物料类型" style="width: 100%">
          <el-option label="试剂" value="REAGENT" />
          <el-option label="校准品" value="CALIBRATOR" />
          <el-option label="质控品" value="QC_MATERIAL" />
          <el-option label="耗材" value="CONSUMABLE" />
          <el-option label="其他" value="OTHER" />
        </el-select>
      </el-form-item>

      <el-form-item label="生产厂家" prop="manufacturer">
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

      <el-form-item label="配送商" prop="distributor">
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
        <el-select
          v-model="form.testGroups"
          multiple
          placeholder="请选择适用组别（可多选）"
          style="width: 100%"
        >
          <el-option v-for="item in optionGroups.TEST_GROUP" :key="item.id" :label="item.optionLabel" :value="item.optionValue" />
        </el-select>
      </el-form-item>

      <el-divider content-position="left">五码信息</el-divider>

      <el-form-item
        v-if="showUdiFields"
        label="UDI-DI"
      >
        <el-input v-model="form.udiDi" placeholder="14位数字" maxlength="128" />
      </el-form-item>

      <el-form-item v-if="showUdiFields" label="GTIN">
        <el-input v-model="form.gtin" placeholder="14位GTIN码" maxlength="14" />
      </el-form-item>

      <el-form-item v-if="showUdiFields" label="医保编码">
        <el-input v-model="form.insuranceCode" placeholder="27位数字" maxlength="27" />
      </el-form-item>

      <el-form-item
        v-if="showUdiFields"
        label="注册证号"
      >
        <el-input v-model="form.registrationCertNo" placeholder="例：国械注准20253123456" maxlength="128" />
      </el-form-item>

      <el-form-item label="检测方法学">
        <el-select v-model="form.detectionMethod" filterable clearable placeholder="请选择检测方法学" style="width:100%">
          <el-option v-for="item in optionGroups.DETECTION_METHOD" :key="item.id" :label="item.optionLabel" :value="item.optionValue" />
        </el-select>
      </el-form-item>

      <el-divider content-position="left">库存与规则</el-divider>

      <el-form-item
        v-if="showTestsPerKit"
        label="标称测试数"
      >
        <el-input-number v-model="form.testsPerKit" :min="1" :max="999999" placeholder="仅试剂必填" style="width: 100%" />
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

      <el-divider content-position="left">采购与医保</el-divider>

      <el-form-item label="是否带量">
        <el-switch v-model="form.isVolumeBased" :active-value="1" :inactive-value="0" active-text="是" inactive-text="否" />
      </el-form-item>

      <el-form-item label="带量类型">
        <el-select v-model="form.volumeType" placeholder="请选择带量类型" style="width:100%" :disabled="!form.isVolumeBased">
          <el-option v-for="item in optionGroups.VOLUME_TYPE" :key="item.id" :label="item.optionLabel" :value="item.optionValue" />
        </el-select>
      </el-form-item>

      <el-form-item label="是否医保支付">
        <el-switch v-model="form.isInsurancePayment" :active-value="1" :inactive-value="0" active-text="是" inactive-text="否" />
      </el-form-item>

      <el-form-item label="医保支付类型">
        <el-select v-model="form.insurancePaymentType" placeholder="请选择医保支付类型" style="width:100%">
          <el-option v-for="item in optionGroups.INSURANCE_PAYMENT_TYPE" :key="item.id" :label="item.optionLabel" :value="item.optionValue" />
        </el-select>
      </el-form-item>

      <el-form-item label="是否收费">
        <el-switch v-model="form.isChargeable" :active-value="1" :inactive-value="0" active-text="是" inactive-text="否" />
      </el-form-item>

      <el-form-item label="收费编码">
        <el-input v-model="form.chargeCode" placeholder="请输入收费编码" maxlength="50" />
      </el-form-item>

      <el-form-item label="设备关联策略">
        <el-select v-model="form.deviceBindMode" style="width: 100%">
          <el-option label="强制关联" value="FORCE" />
          <el-option label="柔性关联" value="FLEXIBLE" />
        </el-select>
      </el-form-item>

      <el-form-item label="唯一码管控">
        <el-switch v-model="form.enableUniqueCode" :active-value="1" :inactive-value="0" active-text="是" inactive-text="否" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        确 定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useCatalogStore } from '@/stores/catalog'
import { getManufacturerOptions } from '@/api/manufacturer'
import { getDistributorOptions } from '@/api/distributor'
import { useCatalogFieldOptions } from '@/composables/useCatalogFieldOptions'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'created'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const catalogStore = useCatalogStore()
const formRef = ref(null)
const submitting = ref(false)
const { optionGroups, loadCatalogFieldOptions } = useCatalogFieldOptions()

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

const defaultForm = () => ({
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
  isVolumeBased: 0,
  volumeType: '',
  isInsurancePayment: 0,
  insurancePaymentType: '',
  isChargeable: 0,
  chargeCode: '',
  deviceBindMode: 'FORCE',
  enableUniqueCode: 0
})

const form = reactive(defaultForm())

const rules = {
  hisCode: [{ required: true, message: '请输入院内编码', trigger: 'blur' }],
  genericName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  materialType: [{ required: true, message: '请选择物料类型', trigger: 'change' }],
  manufacturer: [{ required: true, message: '请输入生产厂家', trigger: 'blur' }],
  distributor: [{ required: true, message: '请输入配送商', trigger: 'blur' }]
}

// 物料类型条件字段显隐
const showUdiFields = computed(() =>
  ['REAGENT', 'CALIBRATOR', 'QC_MATERIAL'].includes(form.materialType)
)
const showTestsPerKit = computed(() => form.materialType === 'REAGENT')

// 切换物料类型时清除条件字段
watch(() => form.materialType, () => {
  if (!showUdiFields.value) {
    form.udiDi = ''
    form.registrationCertNo = ''
    form.gtin = ''
    form.insuranceCode = ''
  }
  if (!showTestsPerKit.value) {
    form.testsPerKit = null
  }
})

// 对话框打开时预加载厂家/配送商选项
watch(visible, (val) => {
  if (val) {
    loadManufacturerOptions('')
    loadDistributorOptions('')
    loadCatalogFieldOptions(true)
  }
})

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      ...form,
      testGroups: form.testGroups.length > 0 ? form.testGroups.join(',') : undefined,
      enableUniqueCode: form.enableUniqueCode ? 1 : 0,
      isVolumeBased: form.isVolumeBased ? 1 : 0,
      isInsurancePayment: form.isInsurancePayment ? 1 : 0,
      isChargeable: form.isChargeable ? 1 : 0
    }
    await catalogStore.create(payload)
    ElMessage.success('物料新增成功')
    emit('created')
    visible.value = false
  } catch {
    // 错误由 request 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

function handleClosed() {
  formRef.value?.resetFields()
  Object.assign(form, defaultForm())
}
</script>
