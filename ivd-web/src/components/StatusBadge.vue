<!--
  StatusBadge.vue — 通用状态标签组件
  Props: status (string), type (string: 'success'|'warning'|'danger'|'info')
  Usage: <StatusBadge status="已批准" type="success" />
-->
<template>
  <el-tag :type="tagType" :effect="effect" :size="size" v-bind="$attrs">
    <slot>{{ label || status }}</slot>
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, required: true },
  type: { type: String, default: 'info' },
  effect: { type: String, default: 'dark' },
  size: { type: String, default: 'small' },
  label: { type: String, default: '' }
})

const tagType = computed(() => {
  const map = { success: 'success', warning: 'warning', danger: 'danger', info: 'info' }
  return map[props.type] || 'info'
})
</script>
