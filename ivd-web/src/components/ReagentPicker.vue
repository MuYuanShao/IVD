<!--
  ReagentPicker.vue — 试剂选择器（共享组件）
  支持 single 单选 / batch 批量两种模式
  卡片网格布局 + 组别 Tab 切换 + 搜索框
-->
<template>
  <div class="reagent-picker">
    <div class="picker-toolbar">
      <el-input v-model="search" placeholder="搜索试剂名称/通用名/注册证号" :prefix-icon="Search" clearable style="flex:1" />
      <el-radio-group v-model="currentMode" size="small" v-if="allowModeSwitch">
        <el-radio-button value="single">单选</el-radio-button>
        <el-radio-button value="batch">批量</el-radio-button>
      </el-radio-group>
    </div>
    <el-tabs v-model="activeGroup" @tab-change="onTabChange">
      <el-tab-pane v-for="g in groups" :key="g.key" :label="g.label + ' (' + (filteredItems.filter(i => i.testGroup === g.key).length) + ')'" :name="g.key" />
    </el-tabs>
    <div class="picker-grid" v-loading="loading">
      <div
        v-for="item in filteredItems"
        :key="item.id"
        class="picker-card"
        :class="{ selected: isSelected(item.id), batchSelected: currentMode === 'batch' && isSelected(item.id) }"
        @click="toggleSelect(item)"
      >
        <div class="card-name">{{ item.genericName || item.name || item.reagentName }}</div>
        <div class="card-spec">{{ item.spec || item.specification || '—' }}</div>
        <div class="card-meta">
          <el-tag size="small" type="info">{{ item.testGroup || '—' }}</el-tag>
          <span class="card-vendor">{{ item.supplierName || item.manufacturer || '—' }}</span>
        </div>
      </div>
      <el-empty v-if="!loading && !filteredItems.length" description="暂无匹配试剂" :image-size="80" />
    </div>
    <div class="picker-footer" v-if="currentMode === 'batch' && selectedIds.length > 0">
      <span>已选 <b>{{ selectedIds.length }}</b> 种试剂</span>
      <el-button type="primary" size="small" @click="confirmBatch">确认选择</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: [Array, Object, Number], default: null },
  mode: { type: String, default: 'single' },
  allowModeSwitch: { type: Boolean, default: true },
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'update:mode', 'select', 'confirm'])

const search = ref('')
const activeGroup = ref('all')
const selectedIds = ref([])
const currentMode = ref(props.mode)

watch(() => props.mode, (v) => { currentMode.value = v })

const groups = computed(() => {
  const set = new Set(props.items.map(i => i.testGroup || i.groupName))
  return [{ key: 'all', label: '全部' }, ...Array.from(set).filter(Boolean).map(g => ({ key: g, label: g }))]
})

const filteredItems = computed(() => {
  let list = props.items
  if (activeGroup.value !== 'all') {
    list = list.filter(i => (i.testGroup || i.groupName) === activeGroup.value)
  }
  if (search.value.trim()) {
    const q = search.value.trim().toLowerCase()
    list = list.filter(i =>
      (i.genericName || i.name || i.reagentName || '').toLowerCase().includes(q) ||
      (i.spec || i.specification || '').toLowerCase().includes(q) ||
      (i.certNo || '').toLowerCase().includes(q)
    )
  }
  return list
})

function isSelected(id) {
  if (currentMode.value === 'single') {
    if (Array.isArray(props.modelValue)) return props.modelValue.includes(id)
    return props.modelValue === id
  }
  return selectedIds.value.includes(id)
}

function toggleSelect(item) {
  if (currentMode.value === 'single') {
    emit('select', item)
    emit('update:modelValue', item.id)
  } else {
    const idx = selectedIds.value.indexOf(item.id)
    if (idx >= 0) selectedIds.value.splice(idx, 1)
    else selectedIds.value.push(item.id)
  }
}

function confirmBatch() {
  const selected = props.items.filter(i => selectedIds.value.includes(i.id))
  emit('confirm', selected)
  emit('update:modelValue', selected)
}

function onTabChange() {}

watch(() => currentMode.value, () => { selectedIds.value = [] })

onMounted(() => {
  if (currentMode.value === 'batch' && Array.isArray(props.modelValue)) {
    selectedIds.value = [...props.modelValue]
  }
})
</script>

<style scoped>
.reagent-picker { display: flex; flex-direction: column; gap: 12px; }
.picker-toolbar { display: flex; gap: 12px; align-items: center; }
.picker-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; max-height: 400px; overflow-y: auto; }
.picker-card {
  border: 1px solid var(--border-color, #e2e8f0); border-radius: 8px; padding: 12px;
  cursor: pointer; transition: all 0.2s;
}
.picker-card:hover { border-color: var(--ivd-primary, #10b981); background: #f0fdf4; }
.picker-card.selected { border-color: var(--ivd-primary, #10b981); background: #ecfdf5; box-shadow: 0 0 0 2px rgba(16,185,129,0.15); }
.picker-card.batchSelected { border-color: var(--ivd-primary, #10b981); background: #ecfdf5; }
.card-name { font-size: 13px; font-weight: 600; color: var(--text-primary); margin-bottom: 4px; }
.card-spec { font-size: 12px; color: var(--text-secondary); margin-bottom: 6px; }
.card-meta { display: flex; align-items: center; justify-content: space-between; }
.card-vendor { font-size: 11px; color: var(--text-tertiary); }
.picker-footer { display: flex; align-items: center; justify-content: space-between; padding: 10px 0; border-top: 1px solid var(--border-color); }
</style>
