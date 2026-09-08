<template>
  <div class="todo-list">
    <el-empty
      v-if="!items.length"
      description="暂无待办事项"
      :image-size="60"
    />
    <button
      v-for="item in items"
      :key="item.id"
      class="todo-item"
      @click="emit('select', item)"
    >
      <span class="todo-heading"
        ><strong>{{ item.title }}</strong
        ><el-tag :type="item.type || 'info'" size="small" effect="light">{{
          item.status
        }}</el-tag></span
      >
      <span class="todo-meta"
        >{{ item.id }}<span>{{ item.time }}</span></span
      >
      <span class="todo-progress"
        ><el-progress
          :percentage="item.progress"
          :show-text="false"
          :stroke-width="4"
          color="#2eb4a5"
        /><small>{{ item.progress }}%</small></span
      >
    </button>
  </div>
</template>
<script setup>
defineProps({ items: { type: Array, default: () => [] } })
const emit = defineEmits(['select'])
</script>
<style scoped>
.todo-list {
  padding: 0 16px;
}
.todo-item {
  display: block;
  width: 100%;
  padding: 13px 0;
  background: transparent;
  border: 0;
  border-bottom: 1px dashed var(--scm-border);
  cursor: pointer;
  text-align: left;
  color: var(--scm-text);
}
.todo-item:last-child {
  border: 0;
}
.todo-item:hover strong {
  color: var(--scm-primary);
}
.todo-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.todo-heading strong {
  font-size: 12px;
  font-weight: 500;
}
.todo-meta {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: var(--scm-muted);
  margin: 7px 0;
}
.todo-progress {
  display: flex;
  gap: 10px;
  align-items: center;
}
.todo-progress .el-progress {
  flex: 1;
}
.todo-progress small {
  font-size: 10px;
  color: var(--scm-muted);
}
</style>
