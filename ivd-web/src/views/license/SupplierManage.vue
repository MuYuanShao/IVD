<!--
  SupplierManage.vue — 厂家 & 配送商主数据维护
  功能：双 Tab（厂家 / 配送商），各自 CRUD + 启用停用 + 搜索
-->
<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2 class="page-title">供应商维护</h2>
      <p class="page-desc">维护生产厂家与配送商基础资料，供试剂目录、采购等模块引用</p>
    </div>

    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="生产厂家" name="manufacturer" />
      <el-tab-pane label="配送商" name="distributor" />
    </el-tabs>

    <!-- 工具栏 -->
    <el-card class="toolbar-card">
      <el-row :gutter="16" align="middle">
        <el-col :span="6">
          <el-input
            v-model="searchText"
            placeholder="搜索名称 / 简称 / 联系人"
            clearable
            :prefix-icon="Search"
            @input="onSearch"
          />
        </el-col>
        <el-col :span="18" style="text-align: right">
          <el-dropdown @command="handleExport" style="margin-right:8px">
            <el-button :icon="Download">
              导出 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="xlsx">Excel (.xlsx)</el-dropdown-item>
                <el-dropdown-item command="csv">CSV (.csv)</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button :icon="Upload" @click="handleImportClick">导入</el-button>
          <el-button type="primary" :icon="Plus" @click="handleCreate">
            {{ activeTab === 'manufacturer' ? '新增厂家' : '新增配送商' }}
          </el-button>
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

    <!-- 表格 -->
    <el-card shadow="never">
      <el-table :data="currentList" stripe v-loading="currentLoading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="shortName" label="简称" width="120" show-overflow-tooltip />
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column prop="contactEmail" label="邮箱" width="160" show-overflow-tooltip />
        <el-table-column prop="address" label="地址" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
              {{ row.isActive ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button
              link
              :type="row.isActive ? 'warning' : 'success'"
              size="small"
              @click="handleToggle(row)"
            >
              {{ row.isActive ? '停用' : '启用' }}
            </el-button>
            <el-popconfirm
              title="确定删除该记录？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="handleDelete(row.id)"
            >
              <template #reference>
                <el-button link type="danger" size="small" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="currentTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @change="fetchData"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="showDialog"
      :title="dialogTitle"
      width="560px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入名称" maxlength="200" />
        </el-form-item>
        <el-form-item label="简称">
          <el-input v-model="form.shortName" placeholder="请输入简称" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactPerson" placeholder="请输入联系人" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" placeholder="请输入联系电话" maxlength="20" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.contactEmail" placeholder="请输入邮箱" maxlength="128" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="请输入地址" maxlength="256" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editingId ? '保 存' : '确 定' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus, Edit, Delete, Download, Upload, ArrowDown } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useManufacturerStore } from '@/stores/manufacturer'
import { useDistributorStore } from '@/stores/distributor'

const activeTab = ref('manufacturer')
const searchText = ref('')
const currentPage = ref(1)
const pageSize = ref(20)

const manufacturerStore = useManufacturerStore()
const distributorStore = useDistributorStore()

// 当前激活 Tab 的 store 代理
const currentList = computed(() =>
  activeTab.value === 'manufacturer' ? manufacturerStore.list : distributorStore.list
)
const currentLoading = computed(() =>
  activeTab.value === 'manufacturer' ? manufacturerStore.loading : distributorStore.loading
)
const currentTotal = computed(() =>
  activeTab.value === 'manufacturer' ? manufacturerStore.total : distributorStore.total
)

const currentStore = () =>
  activeTab.value === 'manufacturer' ? manufacturerStore : distributorStore

// 弹窗
const showDialog = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)

const defaultForm = {
  name: '', shortName: '', contactPerson: '', contactPhone: '', contactEmail: '', address: ''
}
const form = reactive({ ...defaultForm })

const formRules = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }]
}

const dialogTitle = computed(() => {
  const prefix = activeTab.value === 'manufacturer' ? '厂家' : '配送商'
  return editingId.value ? `编辑${prefix}` : `新增${prefix}`
})

let searchTimer = null
function onSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { currentPage.value = 1; fetchData() }, 300)
}

function handleTabChange() {
  searchText.value = ''
  currentPage.value = 1
  fetchData()
}

onMounted(() => fetchData())

async function fetchData() {
  const store = currentStore()
  await store.fetchList({ page: currentPage.value, pageSize: pageSize.value, keyword: searchText.value })
}

function handleCreate() {
  editingId.value = null
  Object.assign(form, defaultForm)
  showDialog.value = true
}

async function handleEdit(row) {
  editingId.value = row.id
  const store = currentStore()
  try {
    const data = await store.fetchDetail(row.id)
    Object.assign(form, {
      name: data.name || '',
      shortName: data.shortName || '',
      contactPerson: data.contactPerson || '',
      contactPhone: data.contactPhone || '',
      contactEmail: data.contactEmail || '',
      address: data.address || ''
    })
    showDialog.value = true
  } catch {
    ElMessage.error('加载详情失败')
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  const store = currentStore()
  const label = activeTab.value === 'manufacturer' ? '厂家' : '配送商'
  try {
    if (editingId.value) {
      await store.update(editingId.value, { ...form })
      ElMessage.success(`${label}编辑成功`)
    } else {
      await store.create({ ...form })
      ElMessage.success(`${label}新增成功`)
    }
    showDialog.value = false
    fetchData()
  } catch {
    // 错误由 request 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function handleToggle(row) {
  const store = currentStore()
  const label = activeTab.value === 'manufacturer' ? '厂家' : '配送商'
  try {
    await store.toggle(row.id)
    ElMessage.success(`${label}已${row.isActive ? '停用' : '启用'}`)
    fetchData()
  } catch {
    // handled
  }
}

async function handleDelete(id) {
  const store = currentStore()
  const label = activeTab.value === 'manufacturer' ? '厂家' : '配送商'
  try {
    await store.remove(id)
    ElMessage.success(`${label}已删除`)
    fetchData()
  } catch {
    // handled
  }
}

function resetForm() {
  editingId.value = null
  formRef.value?.resetFields()
  Object.assign(form, defaultForm)
}

// ─── 导入/导出 ───

const importFileRef = ref(null)

function handleImportClick() {
  importFileRef.value?.click()
}

async function handleImportFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const label = activeTab.value === 'manufacturer' ? '厂家' : '配送商'
  const apiPath = activeTab.value === 'manufacturer' ? '/manufacturer/import' : '/distributor/import'
  const formData = new FormData()
  formData.append('file', file)
  try {
    const res = await request({
      url: apiPath, method: 'post', data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success(`${label}导入完成：成功 ${res.success} 条，跳过 ${res.skipped} 条`)
    fetchData()
  } catch {
    // handled by interceptor
  } finally {
    // reset input so same file can be re-selected
    importFileRef.value.value = ''
  }
}

function handleExport(format) {
  const apiPath = activeTab.value === 'manufacturer'
    ? `/manufacturer/export?format=${format}`
    : `/distributor/export?format=${format}`
  const token = localStorage.getItem('ivd-token') || ''
  const url = `/api/v1${apiPath}`
  fetch(url, { headers: { Authorization: `Bearer ${token}` } })
    .then(res => res.blob())
    .then(blob => {
      const ext = format === 'csv' ? 'csv' : 'xlsx'
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = `${activeTab.value === 'manufacturer' ? '厂家列表' : '配送商列表'}.${ext}`
      a.click()
      URL.revokeObjectURL(a.href)
    })
    .catch(() => ElMessage.error('导出失败'))
}
</script>

<style scoped>
.page-header { margin-bottom: 20px; }
.page-title { font-size: 20px; font-weight: 600; color: var(--text-primary); margin: 0 0 4px; }
.page-desc { font-size: 13px; color: var(--text-tertiary); margin: 0; }
.toolbar-card { margin-bottom: 16px; }
:deep(.el-table th) { background: var(--bg-page); color: var(--text-secondary); font-weight: 600; }
</style>
