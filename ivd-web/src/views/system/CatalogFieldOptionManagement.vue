<template>
  <div class="field-config-page">
    <header class="page-hero">
      <div class="hero-icon"><el-icon><SetUp /></el-icon></div>
      <div class="hero-copy">
        <span class="eyebrow">CATALOG FIELD DICTIONARY</span>
        <h2>试剂目录字段配置</h2>
        <p>集中维护目录表单的业务下拉项。停用后不影响历史数据，仅从新增与编辑选项中隐藏。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增选项</el-button>
    </header>

    <section class="category-grid">
      <button
        v-for="category in categories"
        :key="category.code"
        type="button"
        :class="['category-card', { active: activeCode === category.code }]"
        @click="activeCode = category.code"
      >
        <span class="category-index">{{ category.index }}</span>
        <span class="category-info">
          <strong>{{ category.label }}</strong>
          <small>{{ category.description }}</small>
        </span>
        <span class="category-count">{{ categoryCount(category.code) }}</span>
      </button>
    </section>

    <el-card shadow="never" class="config-panel">
      <template #header>
        <div class="panel-header">
          <div>
            <strong>{{ activeCategory.label }}</strong>
            <span>共 {{ filteredItems.length }} 项，{{ activeCount }} 项启用</span>
          </div>
          <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索名称、值或备注" class="search-input" />
        </div>
      </template>

      <el-table :data="filteredItems" v-loading="loading" stripe>
        <el-table-column type="index" label="#" width="58" />
        <el-table-column prop="optionLabel" label="显示名称" min-width="170">
          <template #default="{ row }"><strong class="option-label">{{ row.optionLabel }}</strong></template>
        </el-table-column>
        <el-table-column prop="optionValue" label="存储值" min-width="190">
          <template #default="{ row }"><code>{{ row.optionValue }}</code></template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="86" align="center" />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              inline-prompt active-text="启" inactive-text="停"
              @change="toggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="172" />
        <el-table-column label="操作" width="142" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="removeItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && filteredItems.length === 0" description="当前分类暂无配置项" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑字段选项' : '新增字段选项'" width="540px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="字段分类" prop="fieldCode">
          <el-select v-model="form.fieldCode" style="width:100%" :disabled="Boolean(editingId)">
            <el-option v-for="item in categories" :key="item.code" :label="item.label" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="显示名称" prop="optionLabel">
          <el-input v-model="form.optionLabel" maxlength="128" show-word-limit placeholder="下拉框中展示的名称" @input="syncOptionValue" />
        </el-form-item>
        <el-form-item label="存储值" prop="optionValue">
          <el-input v-model="form.optionValue" maxlength="128" show-word-limit placeholder="保存到试剂目录的值" @input="valueManuallyEdited = true" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" />
          <span class="form-hint">数值越小越靠前</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="512" show-word-limit placeholder="选填，说明适用范围或维护依据" />
        </el-form-item>
      </el-form>
      <div class="dialog-note"><el-icon><InfoFilled /></el-icon> 已被试剂引用的选项不能修改存储值或删除，但可以更名、排序和停用。</div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled, Plus, Search, SetUp } from '@element-plus/icons-vue'
import {
  createCatalogFieldOption,
  deleteCatalogFieldOption,
  getCatalogFieldOptionList,
  toggleCatalogFieldOptionStatus,
  updateCatalogFieldOption
} from '@/api/catalogFieldOption'
import { useCatalogFieldOptions } from '@/composables/useCatalogFieldOptions'

const categories = [
  { index: '01', code: 'TEST_GROUP', label: '适用组别', description: '目录归属及页面筛选' },
  { index: '02', code: 'DETECTION_METHOD', label: '检测方法学', description: '试剂检测技术路线' },
  { index: '03', code: 'VOLUME_TYPE', label: '带量类型', description: '集采联盟与采购分类' },
  { index: '04', code: 'INSURANCE_PAYMENT_TYPE', label: '医保支付类型', description: '医保分类与自费标识' }
]

const { loadCatalogFieldOptions } = useCatalogFieldOptions()
const items = ref([])
const loading = ref(false)
const submitting = ref(false)
const activeCode = ref('TEST_GROUP')
const keyword = ref('')
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const valueManuallyEdited = ref(false)
const form = reactive(defaultForm())

const activeCategory = computed(() => categories.find(item => item.code === activeCode.value) || categories[0])
const activeItems = computed(() => items.value.filter(item => item.fieldCode === activeCode.value))
const filteredItems = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return activeItems.value
  return activeItems.value.filter(item => [item.optionLabel, item.optionValue, item.remark]
    .some(value => String(value || '').toLowerCase().includes(query)))
})
const activeCount = computed(() => activeItems.value.filter(item => item.status === 1).length)

const rules = {
  fieldCode: [{ required: true, message: '请选择字段分类', trigger: 'change' }],
  optionLabel: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  optionValue: [{ required: true, message: '请输入存储值', trigger: 'blur' }]
}

function defaultForm() {
  return { fieldCode: activeCode.value || 'TEST_GROUP', optionLabel: '', optionValue: '', sortOrder: 99, remark: '' }
}

function categoryCount(code) {
  const categoryItems = items.value.filter(item => item.fieldCode === code)
  return `${categoryItems.filter(item => item.status === 1).length}/${categoryItems.length}`
}

async function loadItems() {
  loading.value = true
  try { items.value = (await getCatalogFieldOptionList({ includeDisabled: true })) || [] }
  finally { loading.value = false }
}

function openDialog(row) {
  editingId.value = row?.id || null
  valueManuallyEdited.value = Boolean(row)
  Object.assign(form, row ? {
    fieldCode: row.fieldCode,
    optionLabel: row.optionLabel,
    optionValue: row.optionValue,
    sortOrder: row.sortOrder,
    remark: row.remark || ''
  } : defaultForm())
  dialogVisible.value = true
}

function syncOptionValue(value) {
  if (!editingId.value && !valueManuallyEdited.value) form.optionValue = value
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingId.value) await updateCatalogFieldOption(editingId.value, form)
    else await createCatalogFieldOption(form)
    ElMessage.success(editingId.value ? '配置项已更新' : '配置项已新增')
    dialogVisible.value = false
    await Promise.all([loadItems(), loadCatalogFieldOptions(true)])
  } finally { submitting.value = false }
}

async function toggleStatus(row) {
  try {
    await toggleCatalogFieldOptionStatus(row.id)
    ElMessage.success(row.status === 1 ? '配置项已停用' : '配置项已启用')
  } finally {
    await Promise.all([loadItems(), loadCatalogFieldOptions(true)])
  }
}

async function removeItem(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.optionLabel}」吗？`, '删除字段选项', {
      type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消'
    })
    await deleteCatalogFieldOption(row.id)
    ElMessage.success('配置项已删除')
    await Promise.all([loadItems(), loadCatalogFieldOptions(true)])
  } catch {
    // 取消确认或接口错误均已由 Element Plus / 请求拦截器给出提示。
  }
}

onMounted(loadItems)
</script>

<style scoped>
.field-config-page { display:flex; flex-direction:column; gap:16px; }
.page-hero { display:flex; align-items:center; gap:16px; padding:22px 24px; color:#e8f4ff; border-radius:14px; background:linear-gradient(120deg,#10233d 0%,#123c50 65%,#116466 100%); box-shadow:0 12px 28px rgba(15,35,57,.16); }
.hero-icon { display:grid; place-items:center; width:52px; height:52px; flex:none; color:#5eead4; font-size:26px; border:1px solid rgba(94,234,212,.3); border-radius:12px; background:rgba(15,23,42,.35); }
.hero-copy { flex:1; min-width:0; }
.eyebrow { color:#72d7ca; font-size:11px; font-weight:700; letter-spacing:1.8px; }
.hero-copy h2 { margin:3px 0 4px; color:#fff; font-size:23px; }
.hero-copy p { margin:0; color:rgba(226,240,252,.72); font-size:13px; }
.category-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:12px; }
.category-card { display:flex; align-items:center; gap:12px; min-height:82px; padding:14px; text-align:left; border:1px solid #dce7ed; border-radius:12px; background:#fff; cursor:pointer; transition:.2s ease; }
.category-card:hover { transform:translateY(-2px); border-color:#91c9c2; box-shadow:0 7px 18px rgba(17,100,102,.08); }
.category-card.active { border-color:#14a38b; box-shadow:inset 0 0 0 1px #14a38b,0 8px 20px rgba(20,163,139,.12); }
.category-index { color:#a4b5be; font:700 12px/1 ui-monospace,monospace; }
.category-info { display:flex; flex:1; flex-direction:column; gap:5px; min-width:0; }
.category-info strong { color:#17324a; font-size:15px; }
.category-info small { overflow:hidden; color:#7a8d99; text-overflow:ellipsis; white-space:nowrap; }
.category-count { min-width:42px; padding:6px 8px; color:#087c6c; font-size:12px; font-weight:700; text-align:center; border-radius:20px; background:#e8f8f4; }
.config-panel { border-radius:12px; }
.panel-header { display:flex; align-items:center; justify-content:space-between; gap:16px; }
.panel-header > div { display:flex; align-items:baseline; gap:10px; }
.panel-header strong { color:#17324a; font-size:16px; }
.panel-header span { color:#8a9aa4; font-size:12px; }
.search-input { width:260px; }
.option-label { color:#1d3549; }
code { padding:3px 7px; color:#31546b; border:1px solid #dce8ee; border-radius:5px; background:#f5f9fb; font-size:12px; }
.form-hint { margin-left:10px; color:#95a4ad; font-size:12px; }
.dialog-note { display:flex; align-items:center; gap:6px; margin-top:8px; padding:10px 12px; color:#607681; font-size:12px; border-radius:8px; background:#f3f8fa; }
@media (max-width:1000px) { .category-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } }
@media (max-width:700px) { .page-hero { align-items:flex-start; flex-wrap:wrap; } .category-grid { grid-template-columns:1fr; } .panel-header { align-items:flex-start; flex-direction:column; } .search-input { width:100%; } }
</style>
