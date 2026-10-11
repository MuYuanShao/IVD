<template>
  <div class="page-container lab-catalog">
    <el-alert
      v-if="!enabled"
      title="检验业务尚未启用"
      description="需要先完成机构隔离迁移与权限验证。此页面不使用演示数据，也不会向未启用的后端提交资料。"
      type="info"
      :closable="false"
      show-icon
    />
    <template v-else>
      <div class="page-title">
        <h2>{{ title }}</h2>
        <el-button v-if="canWrite" type="primary" @click="emit('create')">{{
          createLabel
        }}</el-button>
      </div>
      <el-alert
        title="参考区间与测量范围独立维护；年龄按天、上下限与有效期均为闭区间；缺失或冲突需人工处理，不自动换算单位或生成诊断。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-card shadow="never" class="lab-table-card">
        <div class="lab-toolbar">
          <slot name="toolbar" /><el-button
            :loading="loading"
            @click="emit('search')"
            >查询</el-button
          >
        </div>
        <el-alert v-if="error" :title="error" type="error" :closable="false" />
        <el-table
          v-loading="loading"
          :data="rows"
          row-key="id"
          :empty-text="error ? '读取失败，请重试' : '暂无数据'"
        >
          <slot />
          <el-table-column label="状态" width="80" fixed="right"
            ><template #default="{ row }"
              ><el-tag :type="row.active ? 'success' : 'info'">{{
                row.active ? '启用' : '停用'
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column v-if="canWrite" label="操作" width="80" fixed="right"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="emit('edit', row)"
                >编辑</el-button
              ></template
            ></el-table-column
          >
        </el-table>
        <el-pagination
          :current-page="page"
          :page-size="20"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="emit('page-change', $event)"
        />
      </el-card>
    </template>
  </div>
</template>
<script setup>
defineProps({
  title: String,
  createLabel: String,
  enabled: Boolean,
  canWrite: Boolean,
  loading: Boolean,
  error: String,
  rows: { type: Array, default: () => [] },
  total: Number,
  page: Number,
})
const emit = defineEmits(['create', 'edit', 'search', 'page-change'])
</script>
<style scoped>
.lab-table-card {
  margin-top: 16px;
}
.lab-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.lab-toolbar :deep(.el-input),
.lab-toolbar :deep(.el-select) {
  max-width: 320px;
}
.el-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
