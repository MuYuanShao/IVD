<!-- WarehouseManagement.vue — 库房管理 M00-3（左树 + 右表） -->
<template>
  <div class="page-container">
    <div class="page-title">
      <h2><span class="title-icon" style="background:#fff7ed;color:#f97316;">🏗️</span>库房管理</h2>
      <span class="sub-tag">M00-3 · 系统基础数据</span>
      <div class="header-right">
        <el-button type="primary" @click="openDialog()">新增库房</el-button>
      </div>
    </div>

    <el-row :gutter="16" style="height:calc(100vh - 180px)">
      <!-- 左侧库房树 -->
      <el-col :span="6">
        <el-card shadow="never" style="height:100%">
          <template #header><span style="font-weight:600">📂 库房树（启用状态）</span></template>
          <el-tree :data="treeData" :props="{ label: 'name', children: 'children' }" node-key="id"
            highlight-current :expand-on-click-node="true" @node-click="handleNodeClick" default-expand-all
            style="max-height:calc(100vh - 260px);overflow-y:auto">
            <template #default="{ node, data }">
              <span style="display:flex;align-items:center;justify-content:space-between;width:100%">
                <span>{{ data.name }}</span>
                <el-tag size="small" :type="getLevelTagType(data.level)">{{ data.levelLabel || '—' }}</el-tag>
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <!-- 右侧表格 -->
      <el-col :span="18">
        <el-card shadow="never" style="margin-bottom:12px">
          <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
            <el-input v-model="filterKeyword" placeholder="搜索库房名称/编码" :prefix-icon="Search" clearable style="width:200px" @keyup.enter="fetchData" />
            <el-select v-model="filterLevel" placeholder="全部级别" clearable style="width:130px" @change="fetchData">
              <el-option label="中心库" :value="1" /><el-option label="科室库" :value="2" /><el-option label="小组库" :value="3" />
            </el-select>
            <el-button type="primary" @click="fetchData">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </div>
        </el-card>
        <el-card shadow="never">
          <el-table :data="store.warehouses" stripe v-loading="store.whLoading" style="width:100%">
            <el-table-column prop="code" label="编码" width="130" />
            <el-table-column prop="name" label="库房名称" width="150" />
            <el-table-column label="级别" width="100">
              <template #default="{row}">
                <el-tag :type="getLevelTagType(row.level)" size="small">{{ row.levelLabel || '—' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="parentName" label="上级库房" width="130" />
            <el-table-column prop="deptName" label="关联科室" width="130" />
            <el-table-column prop="contactPerson" label="联系人" width="100" />
            <el-table-column prop="location" label="位置" width="120" />
            <el-table-column label="状态" width="80">
              <template #default="{row}">
                <el-switch :model-value="row.status === 1" @change="handleToggle(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top:16px;text-align:right">
            <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="store.whTotal"
              :page-sizes="[10,15,30,50]" layout="total,sizes,prev,pager,next" @size-change="fetchData" @current-change="fetchData" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="formId ? '编辑库房' : '新增库房'" width="520px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="库房名称" prop="name"><el-input v-model="form.name" placeholder="如：检验科库" /></el-form-item>
        <el-form-item label="库房编码"><el-input v-model="form.code" placeholder="唯一编码" /></el-form-item>
        <el-form-item label="级别" prop="level">
          <el-select v-model="form.level" placeholder="选择级别" style="width:100%" @change="onLevelChange">
            <el-option label="中心库（一级）" :value="1" /><el-option label="科室库（二级）" :value="2" /><el-option label="小组库（三级）" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="上级库房" v-if="form.level > 1">
          <el-tree-select v-model="form.parentId" :data="parentWhOpts"
            :props="{ label: 'name', value: 'id', children: 'children' }" placeholder="选择上级库房" check-strictly style="width:100%" />
        </el-form-item>
        <el-form-item label="关联科室"><el-select v-model="form.deptId" placeholder="选择科室" clearable style="width:100%">
          <el-option v-for="d in deptOpts" :key="d.id" :label="d.deptName" :value="d.id" /></el-select></el-form-item>
        <el-form-item label="物理位置"><el-input v-model="form.location" placeholder="如：3楼B区" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactPerson" placeholder="联系人" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" placeholder="联系电话" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" :max="999" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useSystemStore } from '@/stores/system'

const store = useSystemStore()

const page = ref(1), pageSize = ref(15)
const filterKeyword = ref(''), filterLevel = ref(null)
const dialogVisible = ref(false), submitting = ref(false)
const formRef = ref(null), formId = ref(null)
const form = ref({ name: '', code: '', level: null, parentId: null, deptId: null, location: '', contactPerson: '', contactPhone: '', sortOrder: 99 })
const treeData = ref([]), parentWhOpts = ref([]), deptOpts = ref([])

const rules = {
  name: [{ required: true, message: '请输入库房名称', trigger: 'blur' }],
  level: [{ required: true, message: '请选择库房级别', trigger: 'change' }],
}

function buildTree(list) {
  const map = {}, roots = []
  list.forEach(d => { map[d.id] = { ...d, children: [] } })
  list.forEach(d => {
    if (d.parentId && map[d.parentId]) map[d.parentId].children.push(map[d.id])
    else if (!d.parentId) roots.push(map[d.id])
  })
  return roots
}

function getLevelTagType(level) {
  return { 1: 'danger', 2: 'warning', 3: 'info' }[level] || 'info'
}

async function loadTree() {
  const list = await store.fetchWhTree()
  treeData.value = buildTree(list)
  parentWhOpts.value = buildTree(list)
}

function handleNodeClick(data) { filterKeyword.value = data.name; fetchData() }

// ── level-linked parent options ──
function filterParentOptions(level) {
  const filtered = store.whTree.filter(w => w.level === level - 1)
  parentWhOpts.value = buildTree(filtered)
}

function onLevelChange(level) {
  form.value.parentId = null  // clear parent when user explicitly changes level
  filterParentOptions(level)
}

// ── data ──
async function fetchData() {
  await store.fetchWarehouses({ page: page.value, pageSize: pageSize.value, keyword: filterKeyword.value || undefined, level: filterLevel.value })
}

function resetFilters() { filterKeyword.value = ''; filterLevel.value = null; page.value = 1; fetchData() }

// ── dialog ──
async function openDialog(row) {
  try { await Promise.all([loadTree(), loadDeptOpts()]) } catch (e) { /* pre-load optional */ }
  if (row) {
    formId.value = row.id
    // filter parent options BEFORE setting form values, so parentId survives
    if (row.level > 1) filterParentOptions(row.level)
    form.value = { name: row.name, code: row.code, level: row.level, parentId: row.parentId, deptId: row.deptId, location: row.location, contactPerson: row.contactPerson, contactPhone: row.contactPhone, sortOrder: row.sortOrder ?? 99 }
  } else {
    formId.value = null
    resetForm()
  }
  dialogVisible.value = true
}

function resetForm() {
  form.value = { name: '', code: '', level: null, parentId: null, deptId: null, location: '', contactPerson: '', contactPhone: '', sortOrder: 99 }
  formRef.value?.resetFields()
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (formId.value) {
      await store.editWarehouse(formId.value, form.value)
      ElMessage.success('库房已更新')
    } else {
      await store.addWarehouse(form.value)
      ElMessage.success('库房已创建')
    }
    dialogVisible.value = false
    await Promise.all([fetchData(), loadTree()])
  } catch (e) { /* handled */ }
  finally { submitting.value = false }
}

async function handleToggle(row) {
  try { await store.toggleWh(row.id); await fetchData(); ElMessage.success(row.status === 1 ? '已停用' : '已启用') }
  catch (e) { /* handled */ }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除库房「${row.name}」吗？需确保无子库房。`, '删除库房', { type: 'warning' })
    await store.removeWarehouse(row.id)
    ElMessage.success('库房已删除')
    await Promise.all([fetchData(), loadTree()])
  } catch (e) { /* cancelled */ }
}

async function loadDeptOpts() {
  if (deptOpts.value.length === 0) {
    deptOpts.value = (await store.fetchDeptOptions()) || []
  }
}

onMounted(async () => {
  await loadTree()
  fetchData()
})
</script>

