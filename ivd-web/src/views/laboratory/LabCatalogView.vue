<template>
  <div class="page-container lab-catalog">
    <el-alert
      v-if="!enabled"
      title="检验业务尚未启用"
      description="需要先完成机构隔离迁移与权限验证。此页面不使用演示数据，也不会向未启用的后端提交资料。"
      type="info"
      :closable="false"
      show-icon
    />
    <template v-else>
      <div class="page-title">
        <h2>{{ isItems ? '检验项目' : '参考区间与测量范围' }}</h2>
        <el-button v-if="canWrite" type="primary" @click="openEditor()">{{
          isItems ? '新增项目' : '新增区间'
        }}</el-button>
      </div>
      <el-alert
        title="参考区间与测量范围独立维护；年龄按天、上下限与有效期均为闭区间；缺失或冲突需人工处理，不自动换算单位或生成诊断。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-card shadow="never" class="lab-table-card">
        <div class="lab-toolbar">
          <el-input
            v-if="isItems"
            v-model="keyword"
            clearable
            placeholder="搜索编码 / 项目名称"
            aria-label="搜索检验项目"
            @keyup.enter="search"
            @clear="search"
          />
          <el-select
            v-else
            v-model="kind"
            placeholder="所有类型"
            aria-label="区间类型"
            @change="search"
            ><el-option label="所有类型" value="" /><el-option
              label="正常参考区间"
              value="REFERENCE" /><el-option
              label="设备测量范围"
              value="MEASUREMENT"
          /></el-select>
          <el-button :loading="loading" @click="search">查询</el-button>
        </div>
        <el-alert v-if="error" :title="error" type="error" :closable="false" />
        <el-table
          v-loading="loading"
          :data="rows"
          row-key="id"
          :empty-text="error ? '读取失败，请重试' : '暂无数据'"
        >
          <template v-if="isItems">
            <el-table-column prop="code" label="项目编码" min-width="110" />
            <el-table-column
              prop="name"
              label="项目名称"
              min-width="160"
              show-overflow-tooltip
            />
            <el-table-column
              prop="methodology"
              label="方法学"
              min-width="110"
            />
            <el-table-column label="结果类型" width="100"
              ><template #default="{ row }">{{
                row.resultType === 'NUMERIC' ? '定量' : '定性'
              }}</template></el-table-column
            >
            <el-table-column prop="unit" label="单位" min-width="90" />
          </template>
          <template v-else>
            <el-table-column
              label="检验项目"
              min-width="160"
              show-overflow-tooltip
              ><template #default="{ row }"
                ><div>{{ row.itemName }}</div>
                <small class="lab-hint">{{ row.itemCode }}</small></template
              ></el-table-column
            >
            <el-table-column label="区间类型" min-width="140"
              ><template #default="{ row }">{{
                row.kind === 'REFERENCE' ? '正常参考区间' : '设备测量范围'
              }}</template></el-table-column
            >
            <el-table-column label="设备 ID" min-width="100"
              ><template #default="{ row }">{{
                row.deviceId || '不限设备'
              }}</template></el-table-column
            >
            <el-table-column
              prop="methodology"
              label="方法学"
              min-width="100"
            />
            <el-table-column label="适用性别" width="100"
              ><template #default="{ row }">{{
                sexLabels[row.sex]
              }}</template></el-table-column
            >
            <el-table-column label="年龄（天）" min-width="130"
              ><template #default="{ row }"
                >{{ row.minAgeDays }} ～ {{ row.maxAgeDays }}</template
              ></el-table-column
            >
            <el-table-column label="参考值 / 范围" min-width="170"
              ><template #default="{ row }">{{
                row.resultType === 'NUMERIC'
                  ? `${row.lowerBound} ～ ${row.upperBound} ${row.unit}`
                  : row.normalText
              }}</template></el-table-column
            >
            <el-table-column label="有效期" min-width="210"
              ><template #default="{ row }"
                >{{ row.validFrom }} ～ {{ row.validTo }}</template
              ></el-table-column
            >
          </template>
          <el-table-column label="状态" width="80" fixed="right"
            ><template #default="{ row }"
              ><el-tag :type="row.active ? 'success' : 'info'">{{
                row.active ? '启用' : '停用'
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column v-if="canWrite" label="操作" width="80" fixed="right"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="openEditor(row)"
                >编辑</el-button
              ></template
            ></el-table-column
          >
        </el-table>
        <el-pagination
          v-model:current-page="page"
          :page-size="20"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </el-card>
      <el-dialog
        v-model="editorOpen"
        :title="`${editingId ? '编辑' : '新增'}${isItems ? '检验项目' : '区间'}`"
        width="min(680px, 94vw)"
        :close-on-click-modal="!saving"
        :close-on-press-escape="!saving"
        :show-close="!saving"
        class="scm-theme"
      >
        <el-form
          v-if="isItems"
          ref="itemFormRef"
          :model="itemForm"
          label-width="100px"
        >
          <el-form-item label="项目编码" prop="code" :rules="required"
            ><el-input v-model="itemForm.code" maxlength="64"
          /></el-form-item>
          <el-form-item label="项目名称" prop="name" :rules="required"
            ><el-input v-model="itemForm.name" maxlength="200"
          /></el-form-item>
          <el-form-item label="方法学" prop="methodology" :rules="required"
            ><el-input v-model="itemForm.methodology" maxlength="100"
          /></el-form-item>
          <el-form-item label="结果类型"
            ><el-radio-group v-model="itemForm.resultType"
              ><el-radio-button value="NUMERIC">定量</el-radio-button
              ><el-radio-button value="TEXT"
                >定性</el-radio-button
              ></el-radio-group
            ></el-form-item
          >
          <el-form-item
            label="单位"
            prop="unit"
            :rules="itemForm.resultType === 'NUMERIC' ? required : []"
            ><el-input
              v-model="itemForm.unit"
              maxlength="32"
              placeholder="定量项目必填；不自动换算"
          /></el-form-item>
          <el-form-item label="启用"
            ><el-switch v-model="itemForm.active"
          /></el-form-item>
          <p class="lab-hint">
            已关联区间的项目不可直接变更方法学、单位或结果类型。
          </p>
        </el-form>
        <el-form
          v-else
          ref="intervalFormRef"
          :model="intervalForm"
          label-width="105px"
        >
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
              ><el-radio-button value="REFERENCE">正常参考区间</el-radio-button
              ><el-radio-button value="MEASUREMENT"
                >测量范围</el-radio-button
              ></el-radio-group
            ></el-form-item
          >
          <el-form-item
            label="设备"
            prop="deviceId"
            :rules="intervalForm.kind === 'MEASUREMENT' ? required : []"
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
                v-for="(label, value) in sexLabels"
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
          <el-form-item v-if="selectedResultType === 'NUMERIC'" label="定量范围"
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
          </p>
        </el-form>
        <template #footer
          ><el-button :disabled="saving" @click="editorOpen = false"
            >取消</el-button
          ><el-button type="primary" :loading="saving" @click="save"
            >保存</el-button
          ></template
        >
      </el-dialog>
    </template>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  getLabItems,
  createLabItem,
  updateLabItem,
  getLabIntervals,
  createLabInterval,
  updateLabInterval,
  getLabDeviceOptions,
} from '@/api/laboratory'
const route = useRoute(),
  auth = useAuthStore()
const enabled = computed(
  () =>
    auth.user?.features?.labCatalog === true &&
    ['SYSTEM_ADMIN', 'LAB_DIRECTOR', 'LAB_DOCTOR', 'LAB_TECH'].includes(
      auth.role,
    ),
)
const canWrite = computed(() =>
  ['SYSTEM_ADMIN', 'LAB_DIRECTOR'].includes(auth.role),
)
const isItems = computed(() => route.meta.labMode === 'items')
const rows = ref([]),
  total = ref(0),
  page = ref(1),
  keyword = ref(''),
  kind = ref(''),
  loading = ref(false),
  error = ref('')
const editorOpen = ref(false),
  editingId = ref(null),
  saving = ref(false),
  itemFormRef = ref(),
  intervalFormRef = ref()
const itemOptions = ref([]),
  deviceOptions = ref([]),
  optionsLoading = ref(false)
const sexLabels = { ANY: '不限', MALE: '男', FEMALE: '女' }
const required = [
  { required: true, message: '请填写必填项', trigger: 'change' },
]
const blankItem = () => ({
  code: '',
  name: '',
  methodology: '',
  resultType: 'NUMERIC',
  unit: '',
  active: true,
  version: null,
})
const blankInterval = () => ({
  itemId: null,
  deviceId: null,
  kind: 'REFERENCE',
  sex: 'ANY',
  minAgeDays: 0,
  maxAgeDays: 73000,
  lowerBound: '',
  upperBound: '',
  normalText: '',
  dates: [],
  active: true,
  version: null,
})
const itemForm = reactive(blankItem()),
  intervalForm = reactive(blankInterval())
const selectedItem = ref(null)
const selectedResultType = computed(
  () => selectedItem.value?.resultType || 'NUMERIC',
)
const selectedUnit = computed(() => selectedItem.value?.unit || '')
let controller,
  loadVersion = 0,
  optionsVersion = 0,
  devicesVersion = 0
async function load() {
  controller?.abort()
  const version = ++loadVersion
  if (!enabled.value) {
    rows.value = []
    total.value = 0
    return
  }
  controller = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const data = isItems.value
      ? await getLabItems(
          { page: page.value, pageSize: 20, keyword: keyword.value },
          controller.signal,
        )
      : await getLabIntervals(
          { page: page.value, pageSize: 20, kind: kind.value || undefined },
          controller.signal,
        )
    if (version === loadVersion) {
      rows.value = data.records
      total.value = data.total
    }
  } catch (e) {
    if (version === loadVersion && e.code !== 'ERR_CANCELED') {
      rows.value = []
      total.value = 0
      error.value = e.response?.data?.message || e.message || '数据读取失败'
    }
  } finally {
    if (version === loadVersion) loading.value = false
  }
}
function search() {
  page.value = 1
  load()
}
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
async function openEditor(row) {
  editingId.value = row?.id || null
  if (isItems.value) Object.assign(itemForm, blankItem(), row || {})
  else {
    Object.assign(intervalForm, blankInterval(), row || {}, {
      dates: row ? [row.validFrom, row.validTo] : [],
    })
    selectedItem.value = row
      ? { id: row.itemId, resultType: row.resultType, unit: row.unit }
      : null
    await Promise.all([findItems(), findDevices()])
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
  }
  editorOpen.value = true
}
async function save() {
  if (saving.value) return
  const valid = await (
    isItems.value ? itemFormRef.value : intervalFormRef.value
  )
    ?.validate()
    .catch(() => false)
  if (!valid || saving.value) return
  let payload
  if (isItems.value)
    payload = {
      code: itemForm.code,
      name: itemForm.name,
      methodology: itemForm.methodology,
      resultType: itemForm.resultType,
      unit: itemForm.unit,
      active: itemForm.active,
      version: itemForm.version,
    }
  else {
    if (intervalForm.dates?.length !== 2) {
      ElMessage.error('请选择有效期')
      return
    }
    payload = {
      itemId: intervalForm.itemId,
      deviceId: intervalForm.deviceId || null,
      kind: intervalForm.kind,
      sex: intervalForm.sex,
      minAgeDays: intervalForm.minAgeDays,
      maxAgeDays: intervalForm.maxAgeDays,
      lowerBound:
        selectedResultType.value === 'NUMERIC' ? intervalForm.lowerBound : null,
      upperBound:
        selectedResultType.value === 'NUMERIC' ? intervalForm.upperBound : null,
      normalText:
        selectedResultType.value === 'TEXT' ? intervalForm.normalText : null,
      validFrom: intervalForm.dates[0],
      validTo: intervalForm.dates[1],
      active: intervalForm.active,
      version: intervalForm.version,
    }
  }
  saving.value = true
  try {
    if (isItems.value)
      await (editingId.value
        ? updateLabItem(editingId.value, payload)
        : createLabItem(payload))
    else
      await (editingId.value
        ? updateLabInterval(editingId.value, payload)
        : createLabInterval(payload))
    ElMessage.success('保存成功')
    editorOpen.value = false
    await load()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '保存失败'
  } finally {
    saving.value = false
  }
}
watch(
  [isItems, enabled],
  () => {
    editorOpen.value = false
    page.value = 1
    error.value = ''
    load()
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  controller?.abort()
  ++loadVersion
  ++optionsVersion
  ++devicesVersion
})
</script>
<style scoped>
.lab-table-card {
  margin-top: 16px;
}
.lab-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.lab-toolbar .el-input,
.lab-toolbar .el-select {
  max-width: 320px;
}
.el-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.el-form .el-select {
  width: 100%;
}
.lab-pair {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.lab-pair .el-input,
.lab-pair .el-input-number {
  min-width: 0;
  flex: 1;
}
.lab-hint {
  font-size: 12px;
  color: var(--scm-muted);
  line-height: 1.6;
}
@media (max-width: 600px) {
  .lab-pair {
    flex-wrap: wrap;
  }
}
</style>
