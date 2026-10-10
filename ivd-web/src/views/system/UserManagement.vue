<!-- UserManagement.vue — 用户管理 M00-1 -->
<template>
  <div class="page-container">
    <div class="page-title">
      <h2><span class="title-icon" style="background:#eff6ff;color:#3b82f6;">👥</span>用户管理</h2>
      <span class="sub-tag">M00-1 · 系统基础数据</span>
      <div class="header-right">
        <el-button type="primary" @click="openDialog()">新增用户</el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6"><el-card shadow="hover" class="stat-card"><el-statistic title="用户总数" :value="store.userTotal" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--success"><el-statistic title="启用中" :value="enabledCount" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--info"><el-statistic title="停用" :value="disabledCount" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--warning"><el-statistic title="角色类型" :value="roleList.length" suffix="种" /></el-card></el-col>
    </el-row>

    <!-- 筛选工具栏 -->
    <el-card shadow="never" style="margin-bottom:16px">
      <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
        <el-input v-model="filterKeyword" placeholder="搜索用户名/姓名/手机号" :prefix-icon="Search" clearable style="width:220px" @keyup.enter="fetchData" />
        <el-select v-model="filterRole" placeholder="全部角色" clearable style="width:150px" @change="fetchData">
          <el-option v-for="r in roleList" :key="r.value" :label="r.label" :value="r.value" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width:120px" @change="fetchData">
          <el-option label="启用" :value="1" /><el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="fetchData">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never">
      <el-table :data="store.users" stripe v-loading="store.userLoading" style="width:100%">
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column label="角色" width="120">
          <template #default="{row}">
            <el-tag :type="getRoleTag(row.role)" size="small">{{ row.roleLabel || row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="所属科室" width="130" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="状态" width="80">
          <template #default="{row}">
            <el-switch :model-value="row.status === 1" @change="handleToggle(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="handleResetPwd(row)">重置密码</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;text-align:right">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="store.userTotal"
          :page-sizes="[10,15,30,50]" layout="total,sizes,prev,pager,next" @size-change="fetchData" @current-change="fetchData" />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="formId ? '编辑用户' : '新增用户'" width="520px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" :disabled="!!formId" placeholder="登录账号" /></el-form-item>
        <el-form-item label="姓名" prop="realName"><el-input v-model="form.realName" placeholder="真实姓名" /></el-form-item>
        <el-form-item label="角色" prop="role"><el-select v-model="form.role" placeholder="选择角色" style="width:100%">
          <el-option v-for="r in roleList" :key="r.value" :label="r.label" :value="r.value" /></el-select></el-form-item>
        <el-form-item label="科室"><el-select v-model="form.deptId" placeholder="选择科室" clearable style="width:100%">
          <el-option v-for="d in deptOpts" :key="d.id" :label="d.deptName" :value="d.id" /></el-select></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" placeholder="手机号" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" placeholder="邮箱" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useSystemStore } from '@/stores/system'

const store = useSystemStore()

const page = ref(1), pageSize = ref(15)
const filterKeyword = ref(''), filterRole = ref(''), filterStatus = ref(null)
const dialogVisible = ref(false), submitting = ref(false)
const formRef = ref(null), formId = ref(null)
const form = ref({ username: '', realName: '', role: '', deptId: null, phone: '', email: '' })
const deptOpts = ref([])

const roleList = [
  { value: 'SYSTEM_ADMIN', label: '系统管理员' },
  { value: 'PURCHASER', label: '采购员' },
  { value: 'STOREKEEPER', label: '库管员' },
  { value: 'LAB_TECH', label: '检验技师' },
  { value: 'LAB_DOCTOR', label: '检验医师' },
  { value: 'LAB_DIRECTOR', label: '检验科主任' },
  { value: 'FINANCE', label: '财务人员' },
  { value: 'IT_STAFF', label: '信息科' },
]

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

const enabledCount = computed(() => store.users.filter(u => u.status === 1).length)
const disabledCount = computed(() => store.users.filter(u => u.status === 0).length)

function getRoleTag(role) {
  const m = { SYSTEM_ADMIN: 'danger', LAB_DIRECTOR: 'warning', PURCHASER: 'success', STOREKEEPER: 'info', LAB_TECH: 'info', LAB_DOCTOR: 'info', FINANCE: 'warning', IT_STAFF: 'info' }
  return m[role] || 'info'
}

// ── data ──
async function fetchData() {
  await store.fetchUsers({ page: page.value, pageSize: pageSize.value, keyword: filterKeyword.value || undefined, role: filterRole.value || undefined, status: filterStatus.value })
}

function resetFilters() {
  filterKeyword.value = ''; filterRole.value = ''; filterStatus.value = null; page.value = 1; fetchData()
}

// ── dialog ──
async function openDialog(row) {
  try { await loadDeptOpts() } catch (e) { /* pre-load optional, dialog opens regardless */ }
  if (row) {
    formId.value = row.id
    form.value = { username: row.username, realName: row.realName, role: row.role, deptId: row.deptId, phone: row.phone, email: row.email }
  } else {
    formId.value = null
    resetForm()
  }
  dialogVisible.value = true
}

function resetForm() {
  form.value = { username: '', realName: '', role: '', deptId: null, phone: '', email: '' }
  formRef.value?.resetFields()
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (formId.value) {
      await store.editUser(formId.value, form.value)
      ElMessage.success('用户已更新')
    } else {
      await store.addUser(form.value)
      ElMessage.success('用户已创建（默认密码 IVD@2026）')
    }
    dialogVisible.value = false
    await fetchData()
  } catch (e) { /* handled by interceptor */ }
  finally { submitting.value = false }
}

async function handleToggle(row) {
  try { await store.toggleUser(row.id); await fetchData(); ElMessage.success(row.status === 1 ? '已停用' : '已启用') }
  catch (e) { /* handled */ }
}

async function handleResetPwd(row) {
  try {
    await ElMessageBox.confirm(`确定将用户「${row.realName}」的密码重置为默认密码 IVD@2026 吗？`, '重置密码', { type: 'warning' })
    await store.resetPwd(row.id)
    ElMessage.success('密码已重置为 IVD@2026')
  } catch (e) { /* cancelled */ }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.realName}」吗？此操作为逻辑删除。`, '删除用户', { type: 'warning' })
    await store.removeUser(row.id)
    ElMessage.success('用户已删除')
    await fetchData()
  } catch (e) { /* cancelled */ }
}

async function loadDeptOpts() {
  if (deptOpts.value.length === 0) {
    deptOpts.value = (await store.fetchDeptOptions()) || []
  }
}

onMounted(() => fetchData())
</script>

<style scoped>
.stat-cards { margin-bottom: 16px; }
.stat-card { text-align: center; }
</style>
