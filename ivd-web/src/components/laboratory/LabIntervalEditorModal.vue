<template>
  <el-dialog
    v-model="editorOpen"
    :title="`${editingId ? '编辑' : '新增'}区间`"
    width="min(680px, 94vw)"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    class="scm-theme"
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form ref="intervalFormRef" :model="intervalForm" label-width="105px">
      <el-form-item label="检验项目" prop="itemId" :rules="required"
        ><el-select
          v-model="intervalForm.itemId"
          :disabled="!!editingId"
          filterable
          remote
          :remote-method="findItems"
          :loading="optionsLoading"
          placeholder="输入编码或名称搜索（最多50项）"
          @change="selectItem"
          ><el-option
            v-for="item in itemOptions"
            :key="item.id"
            :value="item.id"
            :label="`${item.code} · ${item.name}`" /></el-select
      ></el-form-item>
      <el-form-item label="区间类型"
        ><el-radio-group v-model="intervalForm.kind"
          ><el-radio-button
            v-for="option in intervalKindOptions"
            :key="option.value"
            :value="option.value"
            >{{ option.label }}</el-radio-button
          ></el-radio-group
        ></el-form-item
      >
      <el-form-item
        label="设备"
        prop="deviceId"
        :rules="intervalForm.kind === IntervalKind.MEASUREMENT ? required : []"
        ><el-select
          v-model="intervalForm.deviceId"
          clearable
          filterable
          remote
          :remote-method="findDevices"
          placeholder="不限设备；测量范围必须选择设备"
          ><el-option
            v-for="device in deviceOptions"
            :key="device.id"
            :value="device.id"
            :label="`${device.code} · ${device.name}`" /></el-select
      ></el-form-item>
      <el-form-item label="适用性别"
        ><el-select v-model="intervalForm.sex"
          ><el-option
            v-for="(label, value) in referenceSexLabels"
            :key="value"
            :label="label"
            :value="value" /></el-select
      ></el-form-item>
      <el-form-item label="年龄（天）"
        ><div class="lab-pair">
          <el-input-number
            v-model="intervalForm.minAgeDays"
            :min="0"
            :max="73000"
            :precision="0"
          /><span>至</span
          ><el-input-number
            v-model="intervalForm.maxAgeDays"
            :min="0"
            :max="73000"
            :precision="0"
          /></div
      ></el-form-item>
      <el-form-item
        v-if="selectedResultType === ResultType.NUMERIC"
        label="定量范围"
        ><div class="lab-pair">
          <el-input
            v-model="intervalForm.lowerBound"
            placeholder="下限（支持6位小数）"
          /><span>至</span
          ><el-input
            v-model="intervalForm.upperBound"
            placeholder="上限"
          /><span>{{ selectedUnit }}</span>
        </div></el-form-item
      >
      <el-form-item
        v-else
        label="定性参考值"
        prop="normalText"
        :rules="required"
        ><el-input
          v-model="intervalForm.normalText"
          maxlength="200"
          placeholder="如：阴性（按原值比对）"
      /></el-form-item>
      <el-form-item label="有效期" prop="dates" :rules="required"
        ><el-date-picker
          v-model="intervalForm.dates"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="生效日期"
          end-placeholder="结束日期"
      /></el-form-item>
      <el-form-item label="启用"
        ><el-switch v-model="intervalForm.active"
      /></el-form-item>
      <p class="lab-hint">
        通用区间与设备专用区间不能重叠。数值仅使用项目原单位。
      </p> </el-form
    ><template #footer
      ><el-button :disabled="saving" @click="editorOpen = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save"
        >保存</el-button
      ></template
    ></el-dialog
  >
</template>
<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getLabItems,
  getLabDeviceOptions,
  createLabInterval,
  updateLabInterval,
} from '@/api/laboratory'
import {
  ResultType,
  IntervalKind,
  ReferenceSex,
  intervalKindOptions,
  referenceSexLabels,
} from '@/utils/laboratoryOptions.mjs'
const emit = defineEmits(['saved'])
const editorOpen = ref(false),
  editingId = ref(null),
  saving = ref(false),
  error = ref(''),
  intervalFormRef = ref()
const itemOptions = ref([]),
  deviceOptions = ref([]),
  optionsLoading = ref(false),
  selectedItem = ref(null)
const required = [
  { required: true, message: '请填写必填项', trigger: 'change' },
]
const blank = () => ({
  itemId: null,
  deviceId: null,
  kind: IntervalKind.REFERENCE,
  sex: ReferenceSex.ANY,
  minAgeDays: 0,
  maxAgeDays: 73000,
  lowerBound: '',
  upperBound: '',
  normalText: '',
  dates: [],
  active: true,
  version: null,
})
const intervalForm = reactive(blank())
const selectedResultType = computed(
  () => selectedItem.value?.resultType || ResultType.NUMERIC,
)
const selectedUnit = computed(() => selectedItem.value?.unit || '')
let optionsVersion = 0,
  devicesVersion = 0,
  openVersion = 0
async function findItems(keyword = '') {
  const version = ++optionsVersion
  optionsLoading.value = true
  try {
    const data = await getLabItems({ page: 1, pageSize: 50, keyword })
    if (version === optionsVersion)
      itemOptions.value = data.records.filter((item) => item.active)
  } catch (e) {
    if (version === optionsVersion)
      ElMessage.error(e.message || '项目选项读取失败')
  } finally {
    if (version === optionsVersion) optionsLoading.value = false
  }
}
async function findDevices(keyword = '') {
  const version = ++devicesVersion
  try {
    const data = await getLabDeviceOptions(keyword)
    if (version === devicesVersion) deviceOptions.value = data
  } catch (e) {
    if (version === devicesVersion)
      ElMessage.error(e.message || '设备选项读取失败')
  }
}
function selectItem(id) {
  selectedItem.value = itemOptions.value.find((item) => item.id === id)
  intervalForm.lowerBound = ''
  intervalForm.upperBound = ''
  intervalForm.normalText = ''
}

async function open(row) {
  if (saving.value) return
  const current = ++openVersion
  editingId.value = row?.id || null
  error.value = ''
  Object.assign(intervalForm, blank(), row || {}, {
    dates: row ? [row.validFrom, row.validTo] : [],
  })
  selectedItem.value = row
    ? { id: row.itemId, resultType: row.resultType, unit: row.unit }
    : null
  await Promise.all([findItems(), findDevices()])
  if (current !== openVersion) return
  if (row && !itemOptions.value.some((item) => item.id === row.itemId))
    itemOptions.value.push({
      id: row.itemId,
      code: row.itemCode,
      name: row.itemName,
      active: false,
    })
  if (
    row?.deviceId &&
    !deviceOptions.value.some((device) => device.id === row.deviceId)
  )
    deviceOptions.value.push({
      id: row.deviceId,
      code: `ID ${row.deviceId}`,
      name: '原关联设备（可能已停用）',
    })
  editorOpen.value = true
  await nextTick()
  intervalFormRef.value?.clearValidate()
}
async function save() {
  if (saving.value) return
  const valid = await intervalFormRef.value?.validate().catch(() => false)
  if (!valid || saving.value) return
  if (intervalForm.dates?.length !== 2) {
    ElMessage.error('请选择有效期')
    return
  }
  const numeric = selectedResultType.value === ResultType.NUMERIC
  const payload = {
    itemId: intervalForm.itemId,
    deviceId: intervalForm.deviceId || null,
    kind: intervalForm.kind,
    sex: intervalForm.sex,
    minAgeDays: intervalForm.minAgeDays,
    maxAgeDays: intervalForm.maxAgeDays,
    lowerBound: numeric ? intervalForm.lowerBound : null,
    upperBound: numeric ? intervalForm.upperBound : null,
    normalText: numeric ? null : intervalForm.normalText,
    validFrom: intervalForm.dates[0],
    validTo: intervalForm.dates[1],
    active: intervalForm.active,
    version: intervalForm.version,
  }
  saving.value = true
  error.value = ''
  try {
    await (editingId.value
      ? updateLabInterval(editingId.value, payload)
      : createLabInterval(payload))
    ElMessage.success('保存成功')
    editorOpen.value = false
    emit('saved')
  } catch (failure) {
    error.value =
      failure.response?.data?.message || failure.message || '保存失败'
  } finally {
    saving.value = false
  }
}
onBeforeUnmount(() => {
  ++openVersion
  ++optionsVersion
  ++devicesVersion
})
defineExpose({ open })
</script>
<style scoped lang="scss" src="./lab-editor.scss" />
