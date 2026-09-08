<template>
  <section class="scm-panel product-panel">
    <div class="scm-panel-heading">
      <h2>
        {{ title }} <span class="scm-muted">共 {{ filtered.length }} 项</span>
      </h2>
      <div class="table-tools">
        <el-input
          v-model="query"
          placeholder="搜索产品名称 / 注册证持有人"
          clearable
          :prefix-icon="Search"
          aria-label="搜索商品"
        />
        <el-button v-if="showMore" link type="primary" @click="emit('more')"
          >更多<el-icon><ArrowRight /></el-icon
        ></el-button>
      </div>
    </div>
    <el-table
      :data="paged"
      row-key="id"
      style="width: 100%"
      empty-text="未找到匹配的商品，请调整搜索条件"
    >
      <el-table-column
        label="产品名称"
        prop="name"
        min-width="200"
        show-overflow-tooltip
      >
        <template #default="{ row }"
          ><el-button link type="primary" @click="selected = row">{{
            row.name
          }}</el-button
          ><small class="product-code">{{ row.id }}</small></template
        >
      </el-table-column>
      <el-table-column
        label="注册证持有人"
        prop="holder"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column label="有效期" prop="expiry" min-width="115" />
      <el-table-column
        label="规格型号"
        prop="specification"
        min-width="155"
        show-overflow-tooltip
      />
      <el-table-column label="数量" prop="quantity" min-width="95" align="right"
        ><template #default="{ row }"
          >{{ row.quantity.toLocaleString() }}
          <span class="scm-muted">{{ row.unit }}</span></template
        ></el-table-column
      >
      <el-table-column
        label="库位"
        prop="location"
        min-width="95"
        align="center"
      />
    </el-table>
    <div class="table-footer">
      <span class="scm-muted"
        >展示 {{ filtered.length ? (page - 1) * pageSize + 1 : 0 }}–{{
          Math.min(page * pageSize, filtered.length)
        }}
        项</span
      >
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="filtered.length"
        layout="prev, pager, next"
        size="small"
        background
      />
    </div>
    <el-dialog
      :model-value="!!selected"
      title="商品详情"
      width="min(620px, 94vw)"
      class="scm-theme"
      @close="selected = null"
    >
      <el-descriptions v-if="selected" :column="1" border>
        <el-descriptions-item
          v-for="[key, label] in detailFields"
          :key="key"
          :label="label"
          >{{ selected[key] }}</el-descriptions-item
        >
      </el-descriptions>
    </el-dialog>
  </section>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { Search, ArrowRight } from '@element-plus/icons-vue'
const props = defineProps({
  title: { type: String, default: '进销存商品列表' },
  items: { type: Array, default: () => [] },
  pageSize: { type: Number, default: 5 },
  showMore: { type: Boolean, default: false },
})
const emit = defineEmits(['more'])
const query = ref('')
const page = ref(1)
const selected = ref(null)
const detailFields = [
  ['id', '产品编号'],
  ['name', '产品名称'],
  ['holder', '注册证持有人'],
  ['specification', '规格型号'],
  ['expiry', '有效期'],
  ['batch', '批号'],
  ['quantity', '数量'],
  ['unit', '单位'],
  ['department', '所属库房'],
  ['location', '库位'],
]
const filtered = computed(() =>
  props.items.filter((item) =>
    `${item.name} ${item.holder} ${item.id}`
      .toLowerCase()
      .includes(query.value.trim().toLowerCase()),
  ),
)
const paged = computed(() =>
  filtered.value.slice(
    (page.value - 1) * props.pageSize,
    page.value * props.pageSize,
  ),
)
watch([query, () => props.items, () => props.pageSize], () => {
  page.value = 1
})
</script>
<style scoped>
.product-panel {
  overflow: hidden;
}
.scm-panel-heading {
  min-height: 61px;
}
.scm-panel-heading h2 span {
  margin-left: 9px;
  font-weight: 400;
}
.table-tools {
  display: flex;
  align-items: center;
  gap: 16px;
}
.table-tools .el-input {
  width: 270px;
}
.product-code {
  display: block;
  color: var(--scm-muted);
  font-size: 10px;
}
.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 13px 16px;
  gap: 8px;
}
:deep(.el-table .el-button) {
  max-width: 100%;
  display: inline;
  white-space: normal;
  text-align: left;
  line-height: 1.5;
}
@media (max-width: 650px) {
  .scm-panel-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .table-tools {
    width: 100%;
  }
  .table-tools .el-input {
    width: 100%;
  }
  .table-footer {
    flex-wrap: wrap;
  }
}
</style>
