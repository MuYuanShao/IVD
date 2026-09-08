<template>
  <div>
    <div class="module-heading">
      <div>
        <h1>{{ route.meta.title }}</h1>
        <p class="scm-muted">{{ parentTitle }} / {{ route.meta.title }}</p>
      </div>
      <el-tag effect="plain" type="info">演示工作区</el-tag>
    </div>
    <product-table
      v-if="kind === 'products'"
      :key="route.path"
      :title="route.meta.title"
      :items="
        route.path.endsWith('/department')
          ? products.filter((item) => item.department === '检验科')
          : products
      "
      :page-size="10"
    />
    <section v-else-if="kind === 'analysis'" class="scm-panel">
      <div class="scm-panel-heading">
        <h2>{{ route.meta.title }} · 出入库金额</h2>
        <span class="scm-muted">近30天 · 演示数据</span>
      </div>
      <trend-chart
        :title="route.meta.title"
        :labels="trend.map((item) => item.date)"
        :series="series"
        :height="420"
      />
    </section>
    <section v-else class="scm-panel module-table">
      <div class="scm-panel-heading">
        <h2>{{ route.meta.title }}列表</h2>
        <el-input
          v-model="query"
          clearable
          placeholder="搜索列表"
          :prefix-icon="Search"
          aria-label="搜索列表"
        />
      </div>
      <el-table :data="filteredRows" row-key="id" empty-text="暂无匹配结果">
        <el-table-column
          v-for="[key, label] in fixture.columns"
          :key="key"
          :prop="key"
          :label="label"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column label="操作" width="85"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="selected = row"
              >详情</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <p class="module-count scm-muted">共 {{ filteredRows.length }} 项</p>
    </section>
    <el-dialog
      :model-value="!!selected"
      :title="`${route.meta.title}详情`"
      width="min(600px, 94vw)"
      class="scm-theme"
      @close="selected = null"
      ><el-descriptions v-if="selected" :column="1" border
        ><el-descriptions-item
          v-for="[key, label] in fixture.columns"
          :key="key"
          :label="label"
          >{{ selected[key] }}</el-descriptions-item
        ></el-descriptions
      ></el-dialog
    >
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import ProductTable from '@/components/business/ProductTable.vue'
import TrendChart from '@/components/business/TrendChart.vue'
import { products, trend, moduleFixtures } from '@/mock/workspace'
const route = useRoute()
const kind = computed(() => route.meta.kind)
const parentTitle = computed(() => route.matched.at(-2)?.meta.title)
const fixture = computed(
  () => moduleFixtures[kind.value] || { rows: [], columns: [] },
)
const query = ref('')
const selected = ref(null)
const filteredRows = computed(() =>
  fixture.value.rows.filter((row) =>
    Object.values(row)
      .join(' ')
      .toLowerCase()
      .includes(query.value.trim().toLowerCase()),
  ),
)
const series = [
  { name: '入库金额', data: trend.map((item) => item.inbound) },
  { name: '出库金额', data: trend.map((item) => item.outbound) },
]
watch(
  () => route.path,
  () => {
    query.value = ''
    selected.value = null
  },
)
</script>
<style scoped>
.module-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 4px 0 20px;
}
.module-heading h1 {
  font-size: 20px;
  font-weight: 500;
  margin-bottom: 6px;
}
.module-table .el-input {
  width: 230px;
}
.module-count {
  padding: 16px;
}
</style>
