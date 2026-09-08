<template>
  <button class="alert-card" :class="`tone-${tone}`" @click="emit('select')">
    <span class="alert-title"
      >{{ title }}<el-icon><ArrowRight /></el-icon
    ></span>
    <span class="alert-value"
      >{{ value }}<small>{{ unit }}</small></span
    >
    <span class="alert-description">{{ description }}</span>
  </button>
</template>
<script setup>
import { ArrowRight } from '@element-plus/icons-vue'
defineProps({
  title: String,
  value: [Number, String],
  unit: String,
  description: String,
  tone: { type: String, default: 'teal' },
})
const emit = defineEmits(['select'])
</script>
<style scoped>
.alert-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  text-align: left;
  border: 1px solid var(--tint);
  border-radius: 4px;
  padding: 12px 15px;
  background: var(--tint);
  cursor: pointer;
}
.alert-card:hover {
  border-color: var(--tone);
}
.tone-orange {
  --tone: #bf741f;
  --tint: #fff7ea;
}
.tone-purple {
  --tone: #806abb;
  --tint: #f5f1fc;
}
.tone-blue {
  --tone: #4b85bd;
  --tint: #eff6fe;
}
.tone-teal {
  --tone: #168f85;
  --tint: #edf8f5;
}
.alert-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  font-size: 12px;
  color: var(--scm-text);
}
.alert-title .el-icon {
  color: #9aa6aa;
}
.alert-value {
  color: var(--tone);
  font-size: 29px;
  font-weight: 600;
  line-height: 1.6;
  font-variant-numeric: tabular-nums;
}
.alert-value small {
  font-size: 11px;
  margin-left: 7px;
  font-weight: 400;
}
.alert-description {
  color: var(--scm-muted);
  font-size: 11px;
}
</style>
