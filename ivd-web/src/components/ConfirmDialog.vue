<!--
  ConfirmDialog.vue — 通用确认弹窗
  Usage: <ConfirmDialog v-model="show" title="确认删除？" @confirm="doDelete" />
-->
<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    @closed="$emit('closed')"
  >
    <p v-if="message" style="margin:0 0 16px;color:var(--text-secondary)">{{ message }}</p>
    <slot />
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">{{ cancelText }}</el-button>
      <el-button :type="confirmType" :loading="loading" @click="$emit('confirm')">{{ confirmText }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean, required: true },
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '' },
  width: { type: String, default: '420px' },
  confirmText: { type: String, default: '确认' },
  cancelText: { type: String, default: '取消' },
  confirmType: { type: String, default: 'danger' },
  loading: { type: Boolean, default: false }
})
defineEmits(['update:modelValue', 'confirm', 'closed'])
</script>
