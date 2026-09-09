<template>
  <div class="research-project-select">
    <el-select
      :model-value="modelValue"
      @update:model-value="emit('update:modelValue', $event || null)"
      placeholder="无（不关联科研项目）"
      aria-label="科研项目"
      clearable
      filterable
      :loading="loading"
      :disabled="loading"
      style="width: 100%"
    >
      <el-option label="无" value="" />
      <el-option
        v-for="project in options"
        :key="project.id"
        :label="project.name"
        :value="project.id"
      />
    </el-select>
    <div v-if="error" class="project-load-error" role="alert">
      科研项目加载失败，可重试或保持无项目继续。
      <el-button link type="primary" @click="load">重试</el-button>
    </div>
    <span v-else-if="!loading && !options.length" class="project-hint"
      >暂无启用项目，可在“系统管理 → 科研项目设置”中维护。</span
    >
  </div>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { getResearchProjectOptions } from '@/api/researchProject'
const props = defineProps({
  modelValue: { type: [String, Number], default: null },
  open: Boolean,
})
const emit = defineEmits(['update:modelValue'])
const options = ref([]),
  loading = ref(false),
  error = ref(false)
let controller,
  version = 0
async function load() {
  const current = ++version
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  error.value = false
  options.value = []
  try {
    const rows = await getResearchProjectOptions(controller.signal)
    if (!Array.isArray(rows)) throw new Error('科研项目数据格式异常')
    if (current === version)
      options.value = rows.map((row) => ({ ...row, id: String(row.id) }))
  } catch (e) {
    if (current === version && e.code !== 'ERR_CANCELED') error.value = true
  } finally {
    if (current === version) loading.value = false
  }
}
watch(
  () => props.open,
  (open) => {
    if (open) {
      emit('update:modelValue', null)
      load()
    } else {
      ++version
      controller?.abort()
      loading.value = false
    }
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  ++version
  controller?.abort()
})
</script>
<style scoped>
.research-project-select {
  width: 100%;
}
.project-hint,
.project-load-error {
  display: block;
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}
.project-load-error {
  color: var(--el-color-danger);
}
</style>
