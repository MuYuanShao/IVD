<!-- DeptManagement.vue — 科室管理 M00-2（左树 + 右表） -->
<template>
  <div class="page-container">
    <div class="page-title">
      <h2><span class="title-icon" style="background:#f0fdf4;color:#10b981;">🏥</span>科室管理</h2>
      <span class="sub-tag">M00-2 · 系统基础数据</span>
      <div class="header-right">
        <el-button type="primary" @click="openDialog()">新增科室</el-button>
      </div>
    </div>

    <el-row :gutter="16" style="height:calc(100vh - 180px)">
      <!-- 左侧科室树 -->
      <el-col :span="6">
        <el-card shadow="never" style="height:100%">
          <template #header><span style="font-weight:600">📂 科室树（启用状态）</span></template>
          <el-tree :data="treeData" :props="{ label: 'deptName', children: 'children' }" node-key="id"
            highlight-current :expand-on-click-node="true" @node-click="handleNodeClick" default-expand-all
            style="max-height:calc(100vh - 260px);overflow-y:auto">
            <template #default="{ node, data }">
              <span style="display:flex;align-items:center;justify-content:space-between;width:100%">
                <span>{{ data.deptName }}</span>
                <el-tag size="small" :type="data.status === 1 ? 'success' : 'info'">{{ data.status === 1 ? '启用' : '停用' }}</el-tag>
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <!-- 右侧表格 -->
      <el-col :span="18">
        <el-card shadow="never" style="margin-bottom:12px">
          <div style="display:flex;align-items:center;gap:10px;">
            <el-input v-model="filterKeyword" placeholder="搜索科室名称/编码" :prefix-icon="Search" clearable style="width:220px" @keyup.enter="fetchData" />
            <el-button type="primary" @click="fetchData">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </div>
        </el-card>
        <el-card shadow="never">
          <el-table :data="store.depts" stripe v-loading="store.deptLoading" style="width:100%">
            <el-table-column prop="deptCode" label="科室编码" width="120" />
            <el-table-column prop="deptName" label="科室名称" width="150" />
            <el-table-column prop="parentName" label="上级科室" width="120" />
            <el-table-column prop="contactPerson" label="联系人" width="100" />
            <el-table-column prop="contactPhone" label="联系电话" width="130" />
            <el-table-column prop="sortOrder" label="排序" width="70" />
            <el-table-column label="状态" width="80">
              <template #default="{row}">
                <el-switch :model-value="row.status === 1" @change="handleToggle(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{row}">
                <el-button link type="success" size="small" @click="handleDeptReagent(row)">试剂</el-button>
                <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top:16px;text-align:right">
            <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="store.deptTotal"
              :page-sizes="[10,15,30,50]" layout="total,sizes,prev,pager,next" @size-change="fetchData" @current-change="fetchData" />
          </div>
        </el-card>

        <!-- 科室试剂目录卡片 -->
        <el-card v-if="store.selectedDeptId" shadow="never" style="margin-top:16px">
          <template #header>
            <div style="display:flex;align-items:center;justify-content:space-between">
              <span style="font-weight:600">
                🧪 申领试剂范围 —
                <el-tag type="success" size="small" style="margin-left:8px">{{ selectedDeptName }}</el-tag>
              </span>
              <div>
                <el-button type="primary" size="small" @click="openReagentPicker">引用试剂</el-button>
                <el-button
                  v-if="selectedReagentIds.length"
                  type="danger" size="small" style="margin-left:8px"
                  @click="handleBatchRemove">批量移除 ({{ selectedReagentIds.length }})</el-button>
                <el-button size="small" style="margin-left:8px" @click="store.clearDeptReagents()">关闭</el-button>
              </div>
            </div>
          </template>
          <el-table
            :data="store.deptReagents" stripe v-loading="store.deptReagentLoading" size="small"
            @selection-change="handleReagentSelectionChange"
          >
            <el-table-column type="selection" width="45" />
            <el-table-column prop="hisCode" label="HIS编码" width="130" />
            <el-table-column prop="genericName" label="物料名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="materialTypeLabel" label="物料类型" width="90" />
            <el-table-column prop="specification" label="规格" width="130" show-overflow-tooltip />
            <el-table-column prop="manufacturer" label="生产厂家" min-width="140" show-overflow-tooltip />
            <el-table-column prop="unit" label="单位" width="70" />
            <el-table-column prop="registrationCertNo" label="注册证号" width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{row}">
                <el-button link type="danger" size="small" @click="handleRemoveReagent(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!store.deptReagentLoading && store.deptReagents.length === 0" description="该科室暂无试剂目录，请点击「引用试剂」添加" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="formId ? '编辑科室' : '新增科室'" width="520px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="科室名称" prop="deptName"><el-input v-model="form.deptName" placeholder="如：检验科" /></el-form-item>
        <el-form-item label="科室编码" prop="deptCode"><el-input v-model="form.deptCode" placeholder="唯一编码" /></el-form-item>
        <el-form-item label="上级科室"><el-tree-select v-model="form.parentId" :data="treeSelectData"
          :props="{ label: 'deptName', value: 'id', children: 'children' }" placeholder="无（顶级科室）" clearable check-strictly style="width:100%" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" :max="999" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactPerson" placeholder="联系人" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" placeholder="联系电话" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 试剂引用对话框 -->
    <el-dialog v-model="reagentPickerVisible" title="引用试剂到科室" width="780px" @close="resetReagentPicker">
      <template #header>
        <span>引用试剂 — <el-tag type="success" size="small">{{ selectedDeptName }}</el-tag></span>
      </template>
      <ReagentPicker
        v-model="pickerSelected"
        mode="batch"
        :allow-mode-switch="false"
        :items="catalogItems"
        :loading="catalogLoading"
        @confirm="handleReagentConfirm"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useSystemStore } from '@/stores/system'
import { getCatalogList } from '@/api/catalog'
import ReagentPicker from '@/components/ReagentPicker.vue'

const store = useSystemStore()

const page = ref(1), pageSize = ref(15)
const filterKeyword = ref('')
const dialogVisible = ref(false), submitting = ref(false)
const formRef = ref(null), formId = ref(null)
const form = ref({ deptName: '', deptCode: '', parentId: null, sortOrder: 99, contactPerson: '', contactPhone: '', remark: '' })
const treeData = ref([]), treeSelectData = ref([])

const rules = {
  deptName: [{ required: true, message: '请输入科室名称', trigger: 'blur' }],
  deptCode: [{ required: true, message: '请输入科室编码', trigger: 'blur' }],
}

// ── 试剂管理 ──
const selectedDeptName = ref('')
const reagentPickerVisible = ref(false)
const catalogItems = ref([])
const catalogLoading = ref(false)
const pickerSelected = ref([])
const selectedReagentIds = ref([])

function buildTree(list) {
  const map = {}, roots = []
  list.forEach(d => { map[d.id] = { ...d, children: [] } })
  list.forEach(d => {
    if (d.parentId && map[d.parentId]) map[d.parentId].children.push(map[d.id])
    else if (!d.parentId) roots.push(map[d.id])
  })
  return roots
}

async function loadTree() {
  const list = await store.fetchDeptTree()
  treeData.value = buildTree(list)
  treeSelectData.value = buildTree(list)
}

async function handleNodeClick(data) {
  filterKeyword.value = data.deptName
  fetchData()
  // 同时加载该科室的试剂目录
  selectedDeptName.value = data.deptName
  await store.fetchDeptReagents(data.id)
}

// ── data ──
async function fetchData() {
  await store.fetchDepts({ page: page.value, pageSize: pageSize.value, keyword: filterKeyword.value || undefined })
}

function resetFilters() { filterKeyword.value = ''; page.value = 1; fetchData() }

// ── dialog ──
async function openDialog(row) {
  try { await loadTree() } catch (e) { /* pre-load optional, dialog opens regardless */ }
  if (row) {
    formId.value = row.id
    form.value = { deptName: row.deptName, deptCode: row.deptCode, parentId: row.parentId, sortOrder: row.sortOrder ?? 99, contactPerson: row.contactPerson, contactPhone: row.contactPhone, remark: row.remark }
  } else {
    formId.value = null
    resetForm()
  }
  dialogVisible.value = true
}

function resetForm() {
  form.value = { deptName: '', deptCode: '', parentId: null, sortOrder: 99, contactPerson: '', contactPhone: '', remark: '' }
  formRef.value?.resetFields()
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (formId.value) {
      await store.editDept(formId.value, form.value)
      ElMessage.success('科室已更新')
    } else {
      await store.addDept(form.value)
      ElMessage.success('科室已创建，关联库房已自动生成')
    }
    dialogVisible.value = false
    await Promise.all([fetchData(), loadTree()])
  } catch (e) { /* handled */ }
  finally { submitting.value = false }
}

async function handleToggle(row) {
  try { await store.toggleDept(row.id); await fetchData(); ElMessage.success(row.status === 1 ? '已停用' : '已启用') }
  catch (e) { /* handled */ }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除科室「${row.deptName}」吗？需确保无子科室。`, '删除科室', { type: 'warning' })
    await store.removeDept(row.id)
    ElMessage.success('科室已删除')
    await Promise.all([fetchData(), loadTree()])
  } catch (e) { /* cancelled */ }
}

// ── 试剂管理方法 ──

async function handleDeptReagent(row) {
  selectedDeptName.value = row.deptName
  await store.fetchDeptReagents(row.id)
}

function handleReagentSelectionChange(selection) {
  selectedReagentIds.value = selection.map(r => r.reagentId)
}

async function openReagentPicker() {
  reagentPickerVisible.value = true
  catalogLoading.value = true
  pickerSelected.value = []
  try {
    const result = await getCatalogList({ page: 1, pageSize: 500, approvalStatus: 'APPROVED' })
    const records = result.records || []
    catalogItems.value = records.map(r => ({
      ...r,
      reagentName: r.genericName || '—',
      supplierName: r.manufacturer || '—',
      spec: r.specification || '—',
      testGroup: r.testGroups || '通用'
    }))
  } finally {
    catalogLoading.value = false
  }
}

async function handleReagentConfirm(selected) {
  const reagentIds = selected.map(i => i.id)
  if (!reagentIds.length) { reagentPickerVisible.value = false; return }
  try {
    await store.batchAddDeptReagents(store.selectedDeptId, reagentIds)
    ElMessage.success(`已成功引用 ${reagentIds.length} 种试剂`)
    reagentPickerVisible.value = false
    await store.fetchDeptReagents(store.selectedDeptId)
  } catch (e) { /* handled by interceptor */ }
}

async function handleRemoveReagent(row) {
  try {
    await ElMessageBox.confirm(
      `确定从科室「${selectedDeptName.value}」中移除试剂「${row.genericName}」吗？`,
      '移除试剂',
      { type: 'warning', confirmButtonText: '确定移除', cancelButtonText: '取消' }
    )
    await store.batchRemoveDeptReagents(store.selectedDeptId, [row.reagentId])
    ElMessage.success('试剂已从科室目录移除')
    await store.fetchDeptReagents(store.selectedDeptId)
  } catch (e) { /* cancelled or error */ }
}

async function handleBatchRemove() {
  if (!selectedReagentIds.value.length) {
    ElMessage.warning('请先选择要移除的试剂')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定从科室「${selectedDeptName.value}」中移除 ${selectedReagentIds.value.length} 种试剂吗？`,
      '批量移除试剂',
      { type: 'warning', confirmButtonText: '确定移除', cancelButtonText: '取消' }
    )
    await store.batchRemoveDeptReagents(store.selectedDeptId, selectedReagentIds.value)
    ElMessage.success(`已移除 ${selectedReagentIds.value.length} 种试剂`)
    selectedReagentIds.value = []
    await store.fetchDeptReagents(store.selectedDeptId)
  } catch (e) { /* cancelled or error */ }
}

function resetReagentPicker() {
  catalogItems.value = []
  pickerSelected.value = []
}

onMounted(async () => {
  await loadTree()
  fetchData()
})
</script>

