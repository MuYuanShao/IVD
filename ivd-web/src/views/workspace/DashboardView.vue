<template>
  <div class="workspace-dashboard">
    <div v-if="!isDemo" class="scm-panel data-status" aria-live="polite">
      <span v-if="data"
        >{{ data.scope }} · {{ data.businessDate }} · {{ data.timezone }} ·
        更新于 {{ data.generatedAt.replace('T', ' ') }}</span
      >
      <span v-else>{{ loading ? '正在读取业务台账' : '业务数据未获取' }}</span>
      <el-button :loading="loading" @click="refresh">刷新数据</el-button>
    </div>
    <el-alert
      v-if="error"
      type="error"
      :closable="false"
      :title="error"
      :description="
        data
          ? '本次读取失败，以下为上次成功结果，请刷新重试。'
          : '业务数据未获取，不显示占位统计。'
      "
      show-icon
    />
    <el-alert
      v-if="!isDemo && data"
      type="info"
      :closable="false"
      title="统计口径：入库为外部验收入库，出库为内部调拨；消耗单独统计。不同商品单位不可相加，顶部数量按记录笔数展示。"
    />
    <el-alert
      v-if="
        !isDemo &&
        data &&
        (data.metrics.unpricedRecords ||
          data.trend.some((row) => row.unpricedRecords))
      "
      type="warning"
      :closable="false"
      title="存在未记录金额的流水，相应金额显示为 —，请核对业务台账。"
    />
    <el-skeleton v-if="!isDemo && loading && !data" :rows="8" animated />
    <el-row :gutter="16" class="statistics-row">
      <el-col
        v-for="item in statistics"
        :key="item.title"
        :xs="12"
        :sm="12"
        :md="6"
        ><statistic-card v-bind="item" @select="navigate(item.path)"
      /></el-col>
    </el-row>
    <el-row v-if="isDemo || data" :gutter="16" class="dashboard-columns">
      <el-col :xs="24" :sm="12" :md="7" class="dashboard-column">
        <section class="scm-panel">
          <div class="scm-panel-heading">
            <h2>
              待办事项
              <span class="todo-count">{{
                isDemo ? todos.length : data.todoTotal
              }}</span>
            </h2>
            <el-button
              link
              type="primary"
              @click="navigate('/workspace/trade/purchase')"
              >查看全部<el-icon><ArrowRight /></el-icon
            ></el-button>
          </div>
          <todo-list :items="todos" @select="openTodo" />
          <p v-if="!isDemo" class="scm-muted todo-explanation">
            按创建时间展示最早6单；进度为已足量发货明细项占比。
          </p>
        </section>
        <section class="scm-panel">
          <div class="scm-panel-heading">
            <h2>近30天出入库金额</h2>
            <span class="scm-muted">万元</span>
          </div>
          <trend-chart
            title="近30天出入库金额"
            :summary="`${isDemo ? '演示数据' : '业务台账'}：近30天外部入库与调拨出库金额，单位万元`"
            :labels="trend.map((item) => item.date)"
            :series="allSeries"
            variant="bar"
            :height="230"
          />
        </section>
      </el-col>
      <el-col :xs="24" :sm="12" :md="10" class="dashboard-column">
        <section class="scm-panel">
          <div class="scm-panel-heading">
            <h2>预警信息</h2>
            <span class="scm-muted"
              ><span class="status-indicator" />及时关注 · 高效处理</span
            >
          </div>
          <div class="alerts-grid">
            <alert-card
              v-for="item in alerts"
              :key="item.title"
              v-bind="item"
              @select="navigate(item.path)"
            />
          </div>
          <div class="alert-note">
            <el-icon><InfoFilled /></el-icon
            >请及时处理库存与效期预警，保障临床供应。
          </div>
        </section>
        <section class="scm-panel trend-panel">
          <div class="scm-panel-heading">
            <h2>出入库趋势</h2>
            <el-radio-group
              v-model="store.range"
              size="small"
              aria-label="趋势日期范围"
              ><el-radio-button :value="7">近7天</el-radio-button
              ><el-radio-button :value="30"
                >近30天</el-radio-button
              ></el-radio-group
            >
          </div>
          <div class="trend-summary">
            <div>
              <span>入库总额</span
              ><strong>{{ inboundTotal }}<small>万元</small></strong>
            </div>
            <div>
              <span>出库总额</span
              ><strong>{{ outboundTotal }}<small>万元</small></strong>
            </div>
            <el-tag size="small" type="success" effect="plain"
              >近 {{ store.range }} 天</el-tag
            >
          </div>
          <trend-chart
            title="出入库趋势"
            :summary="`${isDemo ? '演示数据' : '业务台账'}：近${store.range}天入库${inboundTotal}万元，出库${outboundTotal}万元`"
            :labels="visibleTrend.map((item) => item.date)"
            :series="visibleSeries"
            :height="240"
          />
        </section>
      </el-col>
      <el-col :xs="24" :sm="24" :md="7" class="dashboard-column right-column">
        <section class="scm-panel user-panel">
          <div class="user-greeting">
            <el-avatar :size="48" :icon="UserFilled" />
            <div>
              <h2>你好，{{ displayUser.name }}</h2>
              <p>{{ displayUser.hospital }} · {{ displayUser.department }}</p>
            </div>
            <span
              class="online-dot"
              :title="isDemo ? '演示用户' : '当前登录用户'"
            />
          </div>
          <div class="user-role">
            <span>当前角色</span
            ><el-tag type="success" size="small" effect="plain">{{
              displayUser.role
            }}</el-tag>
          </div>
          <div class="function-header">
            <h3>可用功能</h3>
            <el-button
              link
              type="primary"
              @click="editingFunctions = !editingFunctions"
              >{{ editingFunctions ? '完成' : '管理' }}</el-button
            >
          </div>
          <el-checkbox-group
            v-if="editingFunctions"
            v-model="store.selectedFunctions"
            class="function-editor"
            aria-label="选择常用功能"
            ><el-checkbox
              v-for="item in store.functions"
              :key="item.path"
              :value="item.path"
              >{{ item.title }}</el-checkbox
            ></el-checkbox-group
          >
          <div v-else class="function-tags">
            <el-button
              v-for="item in chosenFunctions"
              :key="item.path"
              size="small"
              plain
              @click="navigate(item.path)"
              >{{ item.title }}</el-button
            ><span v-if="!chosenFunctions.length" class="scm-muted"
              >点击“管理”添加常用功能</span
            >
          </div>
        </section>
        <section class="scm-panel">
          <div class="scm-panel-heading">
            <h2>系统公告</h2>
            <span class="scm-muted">{{
              isDemo ? `${notices.length} 条通知` : '数据源未配置'
            }}</span>
          </div>
          <notice-list v-if="isDemo" :items="notices" @select="openNotice" />
          <el-empty v-else description="公告数据暂不可用" :image-size="60" />
          <p v-if="!isDemo" class="scm-muted todo-explanation">
            公告发布模块尚未配置，无已发布公告数据源。
          </p>
        </section>
        <section class="care-banner">
          <div>
            <span class="care-eyebrow">精益管理 · 绿色医疗</span>
            <h2>每一份节约<br />都为健康多一份守护</h2>
            <p>合理用耗，让医疗资源更有价值</p>
          </div>
          <el-icon class="care-icon"><FirstAidKit /></el-icon>
        </section>
      </el-col>
    </el-row>
    <product-table
      v-if="isDemo || productData || productError"
      :items="products"
      :title="isDemo ? '进销存商品列表' : '在库批次列表'"
      :holder-label="isDemo ? '注册证持有人' : '生产厂家'"
      :remote="!isDemo"
      :total="productData?.total || 0"
      :current-page="page"
      :loading="productLoading"
      :error="productError"
      @search="search"
      @page-change="changePage"
      @retry="loadProducts"
      show-more
      @more="navigate('/workspace/central/query')"
    />
    <el-dialog
      v-model="detailOpen"
      :title="detailTitle"
      width="min(620px, 94vw)"
      class="scm-theme"
    >
      <el-descriptions v-if="selectedTodo" :column="1" border>
        <el-descriptions-item label="单据编号">{{
          selectedTodo.id
        }}</el-descriptions-item
        ><el-descriptions-item label="事项">{{
          selectedTodo.title
        }}</el-descriptions-item
        ><el-descriptions-item label="商品">{{
          selectedTodo.items
        }}</el-descriptions-item
        ><el-descriptions-item label="金额">{{
          selectedTodo.amount
        }}</el-descriptions-item
        ><el-descriptions-item label="状态">{{
          selectedTodo.status
        }}</el-descriptions-item>
      </el-descriptions>
      <div v-else-if="selectedNotice">
        <p class="notice-date">
          {{ selectedNotice.date }} · {{ selectedNotice.category }}
        </p>
        <p class="notice-content">{{ selectedNotice.content }}</p>
      </div>
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, inject, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  InfoFilled,
  UserFilled,
  FirstAidKit,
} from '@element-plus/icons-vue'
import StatisticCard from '@/components/business/StatisticCard.vue'
import AlertCard from '@/components/business/AlertCard.vue'
import TodoList from '@/components/business/TodoList.vue'
import NoticeList from '@/components/business/NoticeList.vue'
import TrendChart from '@/components/business/TrendChart.vue'
import ProductTable from '@/components/business/ProductTable.vue'
import { useWorkbenchData } from '@/composables/useWorkbenchData'
import { sumAmount } from '@/utils/workbenchContract.mjs'
import { useWorkspaceStore } from '@/stores/workspace'
import { useWorkspaceNavigation } from '@/composables/useWorkspaceNavigation'
import { businessDestinations } from '@/router/business'
const router = useRouter()
const store = useWorkspaceStore()
const { isDemo } = useWorkspaceNavigation()
const {
  data,
  productData,
  loading,
  productLoading,
  error,
  productError,
  page,
  statistics,
  alerts,
  todos,
  notices,
  products,
  trend,
  refresh,
  loadProducts,
  search,
  changePage,
} = useWorkbenchData(isDemo)
const displayUser = inject(
  'workspaceUser',
  computed(() => store.user),
)
function navigate(path) {
  router.push(isDemo.value ? path : businessDestinations[path] || path)
}
const editingFunctions = ref(false)
const chosenFunctions = computed(() =>
  store.functions.filter((item) => store.selectedFunctions.includes(item.path)),
)
const visibleTrend = computed(() => trend.value.slice(-store.range))
const toSeries = (rows) => [
  { name: '入库金额', data: rows.map((item) => item.inbound) },
  { name: '出库金额', data: rows.map((item) => item.outbound) },
]
const allSeries = computed(() => toSeries(trend.value))
const visibleSeries = computed(() => toSeries(visibleTrend.value))
const inboundTotal = computed(
  () => sumAmount(visibleTrend.value, 'inbound')?.toFixed(2) ?? '—',
)
const outboundTotal = computed(
  () => sumAmount(visibleTrend.value, 'outbound')?.toFixed(2) ?? '—',
)
const detailOpen = ref(false)
const detailTitle = ref('')
const selectedTodo = ref(null)
const selectedNotice = ref(null)
function openTodo(item) {
  selectedTodo.value = item
  selectedNotice.value = null
  detailTitle.value = '待办详情'
  detailOpen.value = true
}
function openNotice(item) {
  selectedNotice.value = item
  selectedTodo.value = null
  detailTitle.value = item.title
  detailOpen.value = true
}
</script>
<style scoped>
.data-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 12px;
  font-size: 12px;
}
.workspace-dashboard > .el-alert {
  margin-bottom: 12px;
}
.todo-explanation {
  padding: 8px 16px 14px;
}
.statistics-row {
  align-items: stretch;
  row-gap: 16px;
  margin-bottom: 18px;
}
.statistics-row :deep(.statistic-card) {
  height: 100%;
}
.dashboard-columns {
  margin-bottom: 18px;
  row-gap: 16px;
}
.dashboard-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.todo-count {
  margin-left: 6px;
  font-size: 11px;
  color: var(--scm-primary);
  background: var(--scm-primary-soft);
  border-radius: 9px;
  padding: 1px 6px;
}
.scm-panel-heading .el-button {
  font-size: 11px;
}
.alerts-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 16px 16px 12px;
}
.alert-note {
  padding: 0 16px 14px;
  display: flex;
  gap: 5px;
  align-items: center;
  color: var(--scm-muted);
  font-size: 10px;
}
.status-indicator {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--scm-primary);
  margin-right: 4px;
}
.trend-summary {
  display: flex;
  align-items: center;
  gap: 30px;
  padding: 17px 20px 0;
}
.trend-summary > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.trend-summary > div > span {
  color: var(--scm-muted);
  font-size: 11px;
}
.trend-summary strong {
  font-size: 22px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}
.trend-summary small {
  font-weight: 400;
  font-size: 10px;
  margin-left: 5px;
  color: var(--scm-muted);
}
.trend-summary .el-tag {
  margin-left: auto;
}
.user-panel {
  padding: 18px 16px;
}
.user-greeting {
  display: flex;
  align-items: center;
  gap: 10px;
}
.user-greeting .el-avatar {
  background: #e9f6f3;
  color: #69afa0;
  flex-shrink: 0;
}
.user-greeting h2 {
  font-size: 15px;
  font-weight: 500;
}
.user-greeting p {
  font-size: 10px;
  margin-top: 4px;
  color: var(--scm-muted);
}
.online-dot {
  background: #43b69d;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-left: auto;
}
.user-role {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--scm-border);
  font-size: 11px;
  color: var(--scm-muted);
}
.function-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.function-header h3 {
  font-size: 12px;
  font-weight: 500;
}
.function-header .el-button {
  font-size: 11px;
}
.function-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.function-tags .el-button {
  margin: 0;
  color: #6a7d83;
  border-color: #e1e9e9;
  background: #fbfdfd;
  font-size: 11px;
}
.function-tags .el-button:hover {
  color: var(--scm-primary);
  border-color: var(--scm-primary);
}
.function-editor {
  display: grid;
  grid-template-columns: 1fr 1fr;
}
.function-editor .el-checkbox {
  margin-right: 0;
}
.function-editor :deep(.el-checkbox__label) {
  font-size: 11px;
}
.care-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px;
  color: #23766d;
  border: 1px solid #d5ebe4;
  border-radius: var(--scm-radius);
  background: linear-gradient(110deg, #e2f3ec, #f1f8ee);
  min-height: 135px;
  position: relative;
  overflow: hidden;
}
.care-eyebrow {
  font-size: 9px;
  letter-spacing: 1px;
}
.care-banner h2 {
  font-size: 17px;
  line-height: 1.65;
  margin: 6px 0;
  font-weight: 500;
}
.care-banner p {
  font-size: 9px;
  color: #6b968c;
}
.care-icon {
  font-size: 68px;
  color: #90c6b0;
  transform: rotate(-12deg);
  opacity: 0.7;
  margin-left: -16px;
}
.notice-date {
  color: var(--scm-muted);
  font-size: 12px;
  margin-bottom: 16px !important;
}
.notice-content {
  line-height: 1.9;
}
@media (min-width: 768px) and (max-width: 991px) {
  .right-column {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
  .care-banner {
    grid-column: 1 / -1;
  }
}
@media (max-width: 1100px) {
  .trend-summary {
    gap: 18px;
    padding-left: 16px;
  }
  .trend-summary .el-tag {
    display: none;
  }
  .user-greeting h2 {
    font-size: 13px;
  }
}
</style>
