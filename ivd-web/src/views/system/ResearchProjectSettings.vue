<template>
  <div class="page-container">
    <div class="page-title">
      <h2>科研项目设置</h2>
      <el-button
        v-if="canManage"
        type="primary"
        :icon="Plus"
        @click="openEditor()"
        >新增科研项目</el-button
      >
    </div>
    <el-alert
      title="仅启用项目可在新建申领中选择。停用或修改项目名称不影响已提交申领的历史记录。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-card shadow="never" class="project-table">
      <div class="project-toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索项目名称 / 负责人"
          :prefix-icon="Search"
          clearable
          aria-label="搜索科研项目"
        />
        <el-select v-model="status" aria-label="项目状态"
          ><el-option label="全部状态" value="all" /><el-option
            label="启用"
            value="enabled" /><el-option label="停用" value="disabled"
        /></el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-table
        :data="paged"
        row-key="id"
        v-loading="loading"
        :empty-text="
          error
            ? '数据加载失败，请刷新重试'
            : '暂无科研项目，请点击新增科研项目'
        "
      >
        <el-table-column
          prop="name"
          label="科研项目名称"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column prop="leader" label="负责人" min-width="110"
          ><template #default="{ row }">{{
            row.leader || '—'
          }}</template></el-table-column
        >
        <el-table-column prop="sortOrder" label="排序" width="75" />
        <el-table-column label="状态" width="90"
          ><template #default="{ row }"
            ><el-tag :type="row.enabled ? 'success' : 'info'">{{
              row.enabled ? '启用' : '停用'
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column
          prop="remark"
          label="备注"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column v-if="canManage" label="操作" width="140" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openEditor(row)"
              >编辑</el-button
            ><el-button
              link
              :type="row.enabled ? 'warning' : 'success'"
              :loading="changingId === row.id"
              @click="toggle(row)"
              >{{ row.enabled ? '停用' : '启用' }}</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <el-pagination
        v-model:current-page="page"
        :page-size="10"
        :total="filtered.length"
        layout="total, prev, pager, next"
        class="project-pagination"
      />
    </el-card>
    <el-dialog
      v-model="editorOpen"
      :title="editingId ? '编辑科研项目' : '新增科研项目'"
      width="min(560px, 94vw)"
      class="scm-theme"
      :close-on-click-modal="!saving"
      :close-on-press-escape="!saving"
      :show-close="!saving"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="项目名称" prop="name"
          ><el-input
            v-model="form.name"
            maxlength="120"
            show-word-limit
            placeholder="请输入科研项目名称"
        /></el-form-item>
        <el-form-item label="负责人"
          ><el-input v-model="form.leader" maxlength="80" placeholder="选填"
        /></el-form-item>
        <el-form-item label="排序"
          ><el-input-number
            v-model="form.sortOrder"
            :min="0"
            :max="9999"
            :precision="0"
        /></el-form-item>
        <el-form-item label="启用"
          ><el-switch v-model="form.enabled"
        /></el-form-item>
        <el-form-item label="备注"
          ><el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button :disabled="saving" @click="editorOpen = false"
          >取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存</el-button
        ></template
      >
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import {
  getResearchProjects,
  createResearchProject,
  updateResearchProject,
} from '@/api/researchProject'
const auth = useAuthStore()
const canManage = computed(() =>
  ['SYSTEM_ADMIN', 'ADMIN', 'LAB_DIRECTOR'].includes(auth.role),
)
const rows = ref([]),
  loading = ref(false),
  error = ref(''),
  keyword = ref(''),
  status = ref('all'),
  page = ref(1)
const editorOpen = ref(false),
  editingId = ref(null),
  saving = ref(false),
  changingId = ref(null),
  formRef = ref(null)
const form = reactive({
  name: '',
  leader: '',
  remark: '',
  sortOrder: 0,
  enabled: true,
})
const rules = {
  name: [
    {
      required: true,
      whitespace: true,
      message: '请输入科研项目名称',
      trigger: 'blur',
    },
  ],
}
const filtered = computed(() =>
  rows.value.filter(
    (row) =>
      `${row.name} ${row.leader || ''}`
        .toLowerCase()
        .includes(keyword.value.trim().toLowerCase()) &&
      (status.value === 'all' || row.enabled === (status.value === 'enabled')),
  ),
)
const paged = computed(() =>
  filtered.value.slice((page.value - 1) * 10, page.value * 10),
)
watch([keyword, status], () => {
  page.value = 1
})
async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getResearchProjects()
    if (!Array.isArray(data)) throw new Error('科研项目数据格式异常')
    rows.value = data
    page.value = Math.min(
      page.value,
      Math.max(1, Math.ceil(filtered.value.length / 10)),
    )
  } catch (e) {
    rows.value = []
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}
async function openEditor(row) {
  editingId.value = row?.id || null
  Object.assign(form, {
    name: row?.name || '',
    leader: row?.leader || '',
    remark: row?.remark || '',
    sortOrder: row?.sortOrder ?? 0,
    enabled: row?.enabled ?? true,
  })
  editorOpen.value = true
  await nextTick()
  formRef.value?.clearValidate()
}
async function save() {
  if (saving.value || !(await formRef.value?.validate().catch(() => false)))
    return
  saving.value = true
  try {
    const data = { ...form, name: form.name.trim() }
    if (editingId.value) await updateResearchProject(editingId.value, data)
    else await createResearchProject(data)
    editorOpen.value = false
    ElMessage.success('科研项目已保存')
    await load()
  } catch {
  } finally {
    saving.value = false
  }
}
async function toggle(row) {
  try {
    await ElMessageBox.confirm(
      `确认${row.enabled ? '停用' : '启用'}“${row.name}”？`,
      '科研项目状态',
      { type: 'warning' },
    )
    changingId.value = row.id
    await updateResearchProject(row.id, {
      name: row.name,
      leader: row.leader || '',
      remark: row.remark || '',
      sortOrder: row.sortOrder,
      enabled: !row.enabled,
    })
    ElMessage.success('项目状态已更新')
    await load()
  } catch {
  } finally {
    changingId.value = null
  }
}
onMounted(load)
</script>
<style scoped>
.project-table {
  margin-top: 16px;
}
.project-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.project-toolbar .el-input {
  width: 280px;
}
.project-toolbar .el-select {
  width: 140px;
}
.project-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
