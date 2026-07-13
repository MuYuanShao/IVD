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
        <el-input v-model="form.manufacturer" placeholder="请输入生产厂家" maxlength="200" />
      </el-form-item>

      <el-form-item label="配送商" prop="distributor">
        <el-input v-model="form.distributor" placeholder="请输入配送商" maxlength="200" />
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
          <el-option label="生化" value="生化" />
          <el-option label="免疫" value="免疫" />
          <el-option label="临检" value="临检" />
          <el-option label="微生物" value="微生物" />
          <el-option label="分子" value="分子" />
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
        <el-input v-model="form.detectionMethod" placeholder="例：化学发光法" maxlength="100" />
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

      <el-form-item label="最低库存警戒">
        <el-input-number v-model="form.minStockLevel" :min="0" :precision="2" style="width: 100%" />
      </el-form-item>

      <el-form-item label="最高库存">
        <el-input-number v-model="form.maxStockLevel" :min="0" :precision="2" style="width: 100%" />
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
        确 定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useCatalogStore } from '@/stores/catalog'

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
  minStockLevel: 0,
  maxStockLevel: 99999,
  leadTimeDays: 7,
  deviceBindMode: 'FORCE'
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

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      ...form,
      testGroups: form.testGroups.length > 0 ? form.testGroups.join(',') : undefined
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
