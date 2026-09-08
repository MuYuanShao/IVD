<template>
  <el-timeline v-if="items.length" class="notice-list">
    <el-timeline-item
      v-for="item in items"
      :key="item.id"
      color="#c0dcd7"
      :hide-timestamp="true"
    >
      <button @click="emit('select', item)">
        <span>{{ item.title }}</span
        ><small
          >{{ item.date }} <span>· {{ item.category }}</span></small
        >
      </button>
    </el-timeline-item>
  </el-timeline>
  <el-empty v-else description="暂无公告" :image-size="60" />
</template>
<script setup>
defineProps({ items: { type: Array, default: () => [] } })
const emit = defineEmits(['select'])
</script>
<style scoped>
.notice-list {
  margin: 0;
  padding: 18px 18px 0 24px;
}
.notice-list :deep(.el-timeline-item) {
  padding-bottom: 18px;
}
.notice-list :deep(.el-timeline-item__node) {
  width: 7px;
  height: 7px;
  left: 2px;
  top: 5px;
}
.notice-list :deep(.el-timeline-item__tail) {
  border-left: 1px solid var(--scm-border);
}
.notice-list :deep(.el-timeline-item__wrapper) {
  padding-left: 20px;
}
button {
  border: 0;
  background: transparent;
  padding: 0;
  text-align: left;
  cursor: pointer;
  color: var(--scm-text);
  font-size: 12px;
  line-height: 1.7;
}
button:hover {
  color: var(--scm-primary);
}
small {
  display: block;
  margin-top: 3px;
  color: var(--scm-muted);
  font-size: 10px;
}
small span {
  margin-left: 8px;
}
</style>
