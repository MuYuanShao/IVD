<!--
  LayoutView.vue — IVD 主布局（侧边栏 + 顶栏面包屑 + 内容区）
  对照效果图: 侧边栏 17 页导航 + 系统状态栏 + 面包屑
  P0/P1修复: 新增 /rules /trace 菜单 + 面包屑 + 侧边栏底部状态
-->
<template>
  <el-container
    class="layout-container"
    :style="{ '--layout-aside-width': isCollapse ? '64px' : '230px' }"
  >
    <!-- 侧边栏（深色主题，匹配效果图 #0b1220 → #131c30） -->
    <el-aside :width="isCollapse ? '64px' : '230px'" class="layout-aside">
      <div class="logo-area">
        <div class="logo-icon">IVD</div>
        <span v-show="!isCollapse" class="logo-text">IVD 智慧实验室</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        background-color="transparent"
        text-color="rgba(255,255,255,0.65)"
        active-text-color="#10b981"
        class="sidebar-menu"
      >
        <!-- 工作台 -->
        <div class="menu-group-label" v-show="!isCollapse">工作台</div>
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span style="flex:1">首页看板</span>
        </el-menu-item>

        <!-- 进销存地基 -->
        <div class="menu-group-label" v-show="!isCollapse">进销存地基</div>
        <el-menu-item index="/catalog">
          <el-icon><Box /></el-icon>
          <span style="flex:1">试剂目录</span>
        </el-menu-item>
        <el-menu-item index="/license">
          <el-icon><Stamp /></el-icon>
          <span style="flex:1">证照管理</span>
          <em v-if="!isCollapse && badgeCounts.license" class="menu-badge menu-badge--danger">{{ badgeCounts.license }}</em>
        </el-menu-item>
        <el-menu-item index="/change-log">
          <el-icon><Clock /></el-icon>
          <span style="flex:1">信息变更</span>
        </el-menu-item>
        <el-menu-item index="/supplier">
          <el-icon><Goods /></el-icon>
          <span style="flex:1">供应商维护</span>
        </el-menu-item>
        <el-menu-item index="/requisition">
          <el-icon><Tickets /></el-icon>
          <span style="flex:1">申领管理</span>
          <em v-if="!isCollapse && badgeCounts.requisition" class="menu-badge menu-badge--warning">{{ badgeCounts.requisition }}</em>
        </el-menu-item>
        <el-menu-item index="/shortage">
          <el-icon><WarningFilled /></el-icon>
          <span style="flex:1">缺货单</span>
          <em v-if="!isCollapse && badgeCounts.shortage" class="menu-badge menu-badge--danger">{{ badgeCounts.shortage }}</em>
        </el-menu-item>
        <el-menu-item index="/purchase">
          <el-icon><Files /></el-icon>
          <span style="flex:1">采购订单</span>
          <em v-if="!isCollapse && badgeCounts.purchase" class="menu-badge">{{ badgeCounts.purchase }}</em>
        </el-menu-item>
        <el-menu-item index="/inbound">
          <el-icon><Camera /></el-icon>
          <span style="flex:1">扫码验收</span>
        </el-menu-item>
        <el-menu-item index="/receiving">
          <el-icon><EditPen /></el-icon>
          <span style="flex:1">人工验收</span>
        </el-menu-item>
        <el-menu-item index="/pick-and-ship">
          <el-icon><Goods /></el-icon>
          <span style="flex:1">拣货发货</span>
        </el-menu-item>
        <el-menu-item index="/dept-receive">
          <el-icon><Checked /></el-icon>
          <span style="flex:1">科室签收</span>
        </el-menu-item>
        <el-menu-item index="/trace">
          <el-icon><Search /></el-icon>
          <span style="flex:1">UDI追溯</span>
        </el-menu-item>
        <el-menu-item index="/central">
          <el-icon><Collection /></el-icon>
          <span style="flex:1">中心库 (一级)</span>
        </el-menu-item>
        <el-menu-item index="/dept">
          <el-icon><OfficeBuilding /></el-icon>
          <span style="flex:1">科室库 (二级)</span>
        </el-menu-item>
        <el-menu-item index="/expiry">
          <el-icon><AlarmClock /></el-icon>
          <span style="flex:1">效期预警</span>
          <em v-if="!isCollapse && badgeCounts.expiry" class="menu-badge">{{ badgeCounts.expiry }}</em>
        </el-menu-item>
        <el-menu-item index="/warehouse">
          <el-icon><Grid /></el-icon>
          <span style="flex:1">组库管理 (三级)</span>
        </el-menu-item>
        <el-menu-item index="/consumption">
          <el-icon><Connection /></el-icon>
          <span style="flex:1">上机消耗</span>
        </el-menu-item>

        <!-- 护城河特色 -->
        <div class="menu-group-label menu-group-label--star" v-show="!isCollapse">★ 护城河特色</div>
        <el-menu-item index="/analytics">
          <el-icon><TrendCharts /></el-icon>
          <span style="flex:1">效益分析</span>
          <em v-if="!isCollapse" class="menu-badge menu-badge--star">★</em>
        </el-menu-item>
        <el-menu-item index="/rules">
          <el-icon><Setting /></el-icon>
          <span style="flex:1">规则引擎</span>
        </el-menu-item>
        <el-menu-item index="/audit">
          <el-icon><Checked /></el-icon>
          <span style="flex:1">自动审核</span>
          <em v-if="!isCollapse && badgeCounts.audit" class="menu-badge">{{ badgeCounts.audit }}</em>
        </el-menu-item>

        <!-- 系统管理 -->
        <div class="menu-group-label" v-show="!isCollapse">系统管理</div>
        <el-menu-item index="/system/users">
          <el-icon><UserFilled /></el-icon>
          <span style="flex:1">用户管理</span>
        </el-menu-item>
        <el-menu-item index="/system/depts">
          <el-icon><OfficeBuilding /></el-icon>
          <span style="flex:1">科室管理</span>
        </el-menu-item>
        <el-menu-item index="/system/warehouses">
          <el-icon><Collection /></el-icon>
          <span style="flex:1">库房管理</span>
        </el-menu-item>
        <el-menu-item index="/system/catalog-fields">
          <el-icon><SetUp /></el-icon>
          <span style="flex:1">试剂目录字段配置</span>
        </el-menu-item>

        <!-- 系统 -->
        <div class="menu-group-label" v-show="!isCollapse">系统</div>
        <el-menu-item index="/finance">
          <el-icon><Money /></el-icon>
          <span style="flex:1">日清月结</span>
        </el-menu-item>
      </el-menu>

      <!-- ★ P1修复：侧边栏底部系统状态栏（对照效果图 line 670-673） -->
      <div class="sidebar-footer" v-show="!isCollapse">
        <div class="server-status">
          <span class="server-dot"></span>
          <span>系统运行正常 · MySQL/Redis 在线</span>
        </div>
        <div class="server-version">v1.1-MVP · {{ authStore.realName || '管理员' }} · {{ authStore.roleLabel || '检验科主任' }}</div>
      </div>
    </el-aside>

    <!-- 主体区域 -->
    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse" :size="20">
            <Fold v-if="!isCollapse" /><Expand v-else />
          </el-icon>
          <!-- ★ P1修复：面包屑导航（对照效果图 line 678-681） -->
          <el-breadcrumb separator="›" class="header-breadcrumb">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="breadcrumbParent">{{ breadcrumbParent }}</el-breadcrumb-item>
            <el-breadcrumb-item>{{ $route.meta.title || 'IVD 智慧实验室' }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <div class="global-search">
            <el-input v-model="searchText" placeholder="搜索试剂/批号/供应商..." :prefix-icon="Search" size="small" style="width:240px" clearable @keyup.enter="doGlobalSearch" />
          </div>
          <el-badge :value="notificationCount" class="notice-badge" :hidden="!notificationCount">
            <el-icon :size="20"><Bell /></el-icon>
          </el-badge>
          <!-- ★ P1修复：帮助按钮（对照效果图 line 688） -->
          <el-tooltip content="帮助文档" placement="bottom">
            <span class="help-btn" @click="handleHelp">❓</span>
          </el-tooltip>
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-icon><UserFilled /></el-icon>
              <span>{{ authStore.realName || '管理员' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>个人设置</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { ref, computed, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useProcurementStore } from '@/stores/procurement'
import { useInventoryStore } from '@/stores/inventory'
import { useLicenseStore } from '@/stores/license'
import { Search, DataBoard, Box, Stamp, Goods, Tickets, WarningFilled, Files, Camera, EditPen, Collection, OfficeBuilding, AlarmClock, Grid, Connection, TrendCharts, Setting, SetUp, Checked, UserFilled, Money, Bell, Clock, Fold, Expand } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const procurementStore = useProcurementStore()
const inventoryStore = useInventoryStore()
const licenseStore = useLicenseStore()

const isCollapse = ref(false)
const activeMenu = computed(() => route.path)
const searchText = ref('')
// TODO: notificationCount — 暂无 notification API 端点，保持为 0
const notificationCount = ref(0)

// ── 菜单 Badge 数量（由后端 API 填充，初始为 0）──
const badgeCounts = reactive({
  license: 0,     // 即将到期证照
  requisition: 0, // 待处理申领单
  shortage: 0,    // 活跃缺货单
  purchase: 0,    // 待确认采购订单
  expiry: 0,      // 近效期批次
  audit: 0,       // TODO: 待人工审核结果 — 暂无 audit stats API
})

// ── 从各 Store API 拉取 Badge 数量 ──
async function fetchBadgeCounts() {
  try {
    await procurementStore.fetchRequisitionStats()
    badgeCounts.requisition = procurementStore.reqStats.pending
  } catch { /* 静默 */ }
  try {
    await procurementStore.fetchShortageStats()
    badgeCounts.shortage = procurementStore.shStats.total
  } catch { /* 静默 */ }
  try {
    await procurementStore.fetchOrderStats()
    badgeCounts.purchase = procurementStore.poStats.pending
  } catch { /* 静默 */ }
  try {
    await licenseStore.fetchStats()
    badgeCounts.license = licenseStore.stats.expiring
  } catch { /* 静默 */ }
  try {
    await inventoryStore.fetchStats()
    badgeCounts.expiry = inventoryStore.stats.expiring30
  } catch { /* 静默 */ }
}

onMounted(() => {
  fetchBadgeCounts()
})

// ── 面包屑父级 ──
const breadcrumbParent = computed(() => {
  const parents = {
    '/requisition': '进销存地基',
    '/shortage': '进销存地基',
    '/purchase': '进销存地基',
    '/inbound': '进销存地基',
    '/receiving': '进销存地基',
    '/pick-and-ship': '进销存地基',
    '/dept-receive': '进销存地基',
    '/central': '进销存地基',
    '/dept': '进销存地基',
    '/expiry': '进销存地基',
    '/warehouse': '进销存地基',
    '/catalog': '进销存地基',
    '/license': '进销存地基',
    '/change-log': '进销存地基',
    '/supplier': '进销存地基',
    '/consumption': '进销存地基',
    '/trace': '进销存地基',
    '/rules': '★ 护城河特色',
    '/analytics': '★ 护城河特色',
    '/audit': '★ 护城河特色',
    '/finance': '系统',
    '/settlement': '系统',
    '/system/users': '系统管理',
    '/system/depts': '系统管理',
    '/system/warehouses': '系统管理',
    '/system/catalog-fields': '系统管理',
  }
  return parents[route.path] || ''
})

function doGlobalSearch() {
  if (!searchText.value.trim()) return
  const q = searchText.value.trim().toLowerCase()
  if (/目录|试剂|注册证|catalog/.test(q)) router.push('/catalog')
  else if (/申领|requisition/.test(q)) router.push('/requisition')
  else if (/缺货|shortage/.test(q)) router.push('/shortage')
  else if (/采购订单|采购单|订单|purchase/.test(q)) router.push('/purchase')
  else if (/人工验收|手动验收|receiving/.test(q)) router.push('/receiving')
  else if (/验收|扫码|入库|inbound/.test(q)) router.push('/inbound')
  else if (/中心库|一级库|central/.test(q)) router.push('/central')
  else if (/科室库|二级库|dept/.test(q)) router.push('/dept')
  else if (/效期|过期|近效期|expiry/.test(q)) router.push('/expiry')
  else if (/组库|三级库|FEFO|warehouse/.test(q)) router.push('/warehouse')
  else if (/调拨|库存|批次|inventory/.test(q)) router.push('/central')
  else if (/消耗|核销|上机|设备|consumption/.test(q)) router.push('/consumption')
  else if (/追溯|trace|udi|追溯码/.test(q)) router.push('/trace')
  else if (/签收|收货|receive/.test(q)) router.push('/dept-receive')
  else if (/规则|规则引擎|rules/.test(q)) router.push('/rules')
  else if (/效益|成本|浪费|analytics/.test(q)) router.push('/analytics')
  else if (/审核|audit/.test(q)) router.push('/audit')
  else if (/日结|月结|结算|财务|finance/.test(q)) router.push('/finance')
  else if (/用户|账号|user/.test(q)) router.push('/system/users')
  else if (/科室|dept/.test(q)) router.push('/system/depts')
  else if (/库房|仓库|warehouse/.test(q)) router.push('/system/warehouses')
  else if (/字段配置|目录配置|下拉配置|catalog field/.test(q)) router.push('/system/catalog-fields')
  else if (/证照|license/.test(q)) router.push('/license')
  else if (/看板|dashboard/.test(q)) router.push('/dashboard')
  else ElMessage.info('全局搜索: ' + searchText.value + ' — 未匹配特定页面，请尝试其他关键词')
  searchText.value = ''
}

function handleHelp() {
  ElMessage.info('IVD 智慧实验室管理系统 · v1.1-MVP · 帮助文档')
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

/* === 侧边栏 === */
.layout-aside {
  background: linear-gradient(180deg, #0b1220 0%, #131c30 100%);
  overflow-y: auto;
  overflow-x: hidden;
  border-right: 1px solid rgba(255, 255, 255, 0.05);
  transition: width 0.3s;
  display: flex;
  flex-direction: column;
}

.logo-area {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.logo-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #10b981, #059669);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-text {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
}

/* === 侧边栏菜单 === */
.sidebar-menu {
  border-right: none;
  flex: 1;
}

.sidebar-menu .el-menu-item {
  height: 42px;
  line-height: 42px;
  margin: 2px 8px;
  border-radius: 6px;
}

.sidebar-menu .el-menu-item:hover {
  background-color: rgba(255, 255, 255, 0.06) !important;
}

.sidebar-menu .el-menu-item.is-active {
  background: linear-gradient(90deg, rgba(16, 185, 129, 0.15), transparent) !important;
  border-left: 3px solid #10b981;
  color: #10b981 !important;
}

/* sub-menu：父级标题样式 */
.sidebar-menu .el-sub-menu .el-sub-menu__title {
  height: 42px;
  line-height: 42px;
  margin: 2px 8px;
  border-radius: 6px;
}

.sidebar-menu .el-sub-menu .el-sub-menu__title:hover {
  background-color: rgba(255, 255, 255, 0.06) !important;
}

/* sub-menu 子项缩进 */
.sidebar-menu .el-sub-menu .el-menu-item {
  padding-left: 56px !important;
  font-size: 13px;
}

/* sub-menu 展开时的箭头颜色 */
.sidebar-menu .el-sub-menu__icon-arrow {
  color: rgba(255, 255, 255, 0.45);
}

/* 菜单分组标签 */
.menu-group-label {
  padding: 16px 20px 6px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.30);
  text-transform: uppercase;
  letter-spacing: 1px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.menu-group-label::after {
  content: '';
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, rgba(148,163,184,.15) 0%, transparent 100%);
}
.menu-group-label--star {
  color: #10b981;
}

/* 菜单项 Badge 徽章 */
.menu-badge {
  font-style: normal;
  font-size: 10px;
  padding: 1px 7px;
  border-radius: 10px;
  font-weight: 700;
  background: #64748b;
  color: #fff;
  flex-shrink: 0;
  margin-left: 4px;
  line-height: 1.6;
}
.menu-badge--danger {
  background: #ef4444;
}
.menu-badge--warning {
  background: #f59e0b;
}
.menu-badge--star {
  background: linear-gradient(135deg, #f59e0b 0%, #ef4444 100%);
  animation: pulse 2s infinite;
}

/* ★ P1修复：侧边栏底部状态栏 */
.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.server-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.50);
  margin-bottom: 4px;
}

.server-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.6);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.server-version {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.30);
}

/* === 顶栏 === */
.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--border-color);
  padding: 0 20px;
  height: 60px;
  box-shadow: var(--shadow-xs);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  cursor: pointer;
  color: var(--text-secondary);
}

.collapse-btn:hover {
  color: var(--ivd-primary);
}

/* ★ P1修复：面包屑 */
.header-breadcrumb {
  margin-left: 4px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.notice-badge {
  cursor: pointer;
}

.help-btn {
  cursor: pointer;
  color: var(--text-secondary);
  transition: color 0.2s;
}

.help-btn:hover {
  color: var(--ivd-primary);
}

.user-info {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 14px;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.2s;
}

.user-info:hover {
  background: var(--bg-hover);
}

.global-search {
  display: flex;
  align-items: center;
}

/* === 内容区 === */
.layout-main {
  background: var(--bg-page);
  min-height: calc(100vh - 60px);
  overflow-y: auto;
}

/* === 响应式 === */
@media (max-width: 768px) {
  .layout-aside { width: 64px !important; }
  .logo-text, .menu-group-label, .global-search, .sidebar-footer { display: none; }
  .header-breadcrumb { display: none; }
}
</style>
