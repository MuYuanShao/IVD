<template>
  <div class="page-container">
    <div class="page-title"><h2>采购计划</h2><span class="sub-tag">计划 → 提交 → 审批 → 按供应商及验收方式转订单</span></div>
    <el-alert title="计划不是采购订单：只有审批通过后才能转单；本人提交的计划不能由本人审批。历史订单不补造计划。" type="info" :closable="false" show-icon />
    <el-card shadow="never" style="margin-top:16px">
      <div class="plan-toolbar">
        <el-input v-model="keyword" placeholder="计划编号" clearable style="width:230px" @keyup.enter="search" @clear="search" />
        <el-select v-model="status" clearable placeholder="全部状态" style="width:170px" @change="search">
          <el-option v-for="(label,value) in statuses" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button v-if="canManage" @click="router.push({path:'/purchase',query:{createPlan:'1'}})">新建计划</el-button>
        <el-button @click="router.push('/purchase')">采购订单</el-button>
      </div>
      <el-table v-loading="loading" :data="records" border empty-text="暂无采购计划">
        <el-table-column prop="planNo" label="计划编号" min-width="220" />
        <el-table-column prop="sourceType" label="来源" width="150" />
        <el-table-column label="状态" width="120"><template #default="{row}"><el-tag :type="statusType(row.status)">{{ statuses[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="orderDate" label="计划日期" width="125" />
        <el-table-column prop="expectedDeliveryDate" label="预计到货" width="125" />
        <el-table-column prop="totalAmount" label="计划金额" width="125" />
        <el-table-column label="操作" fixed="right" width="100"><template #default="{row}"><el-button link type="primary" @click="openDetail(row.id)">详情</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
        :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper" style="margin-top:16px;justify-content:flex-end" @change="load" />
    </el-card>
    <el-dialog v-model="showDetail" title="采购计划详情" width="min(1050px,96vw)" :close-on-click-modal="false"
      :close-on-press-escape="!busy" :show-close="!busy">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="3" border>
          <el-descriptions-item label="编号">{{ detail.planNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statuses[detail.status] || detail.status }}</el-descriptions-item>
          <el-descriptions-item label="结算模式">{{ detail.settlementMode }}</el-descriptions-item>
          <el-descriptions-item label="计划日期">{{ detail.orderDate }}</el-descriptions-item>
          <el-descriptions-item label="预计到货">{{ detail.expectedDeliveryDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="审批意见">{{ detail.reviewComment || '—' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ detail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="detail?.items || []" border style="margin-top:16px">
          <el-table-column prop="reagentName" label="物料" min-width="180" />
          <el-table-column prop="supplierId" label="供应商编号" min-width="130" />
          <el-table-column prop="acceptanceMethod" label="验收方式" width="110" />
          <el-table-column prop="quantity" label="数量" width="100" />
          <el-table-column prop="unitPrice" label="单价" width="100" />
          <el-table-column prop="amount" label="金额" width="110" />
        </el-table>
        <template v-if="detail?.orders?.length">
          <el-divider>已转换采购订单</el-divider>
          <el-table :data="detail.orders" border>
            <el-table-column prop="poNo" label="订单号" min-width="200" />
            <el-table-column prop="supplierId" label="供应商编号" min-width="140" />
            <el-table-column prop="acceptanceMethod" label="验收方式" />
            <el-table-column label="入口"><template #default="{row}"><el-button link type="primary" @click="router.push({path:'/receiving',query:{orderId:String(row.id),poNo:row.poNo}})">验收</el-button></template></el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button :disabled="busy" @click="showDetail=false">关闭</el-button>
        <template v-if="detail && !detailLoading">
          <el-button v-if="canModify && editable(detail)" :disabled="busy" @click="showEditor=true">编辑草稿</el-button>
          <el-button v-if="canModify && editable(detail)" type="primary" :loading="busy" @click="act('submit')">提交审批</el-button>
          <template v-if="canReview">
            <el-button type="danger" :loading="busy" @click="act('reject')">驳回</el-button>
            <el-button type="success" :loading="busy" @click="act('approve')">批准计划</el-button>
          </template>
          <el-button v-if="canManage && detail.status==='APPROVED'" type="primary" :loading="busy" @click="act('convert')">转采购订单</el-button>
        </template>
      </template>
    </el-dialog>
    <purchase-plan-editor-modal v-model="showEditor" :plan="detail" @saved="refreshDetail" />
  </div>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getPurchasePlans,getPurchasePlan,submitPurchasePlan,approvePurchasePlan,rejectPurchasePlan,convertPurchasePlan } from '@/api/procurement'
import PurchasePlanEditorModal from './components/PurchasePlanEditorModal.vue'
const router=useRouter(),route=useRoute(),auth=useAuthStore()
const page=ref(1),pageSize=ref(10),total=ref(0),keyword=ref(''),status=ref('')
const records=ref([]),loading=ref(false),showDetail=ref(false),detail=ref(null),detailLoading=ref(false),busy=ref(false),showEditor=ref(false)
const statuses={DRAFT:'草稿',PENDING_REVIEW:'待审批',APPROVED:'已批准',REJECTED:'已驳回',ORDERED:'已转订单'}
const canManage=computed(()=>['SYSTEM_ADMIN','PURCHASER','STOREKEEPER'].includes(auth.role))
const canModify=computed(()=>canManage.value && detail.value
  && (auth.role==='SYSTEM_ADMIN' || String(detail.value.createdBy)===String(auth.user?.userId)))
const editable=row=>['DRAFT','REJECTED'].includes(row.status)
const canReview=computed(()=>detail.value?.status==='PENDING_REVIEW' && ['SYSTEM_ADMIN','LAB_DIRECTOR'].includes(auth.role)
  && String(detail.value.createdBy)!==String(auth.user?.userId))
const statusType=value=>({DRAFT:'info',PENDING_REVIEW:'warning',APPROVED:'success',REJECTED:'danger',ORDERED:'primary'}[value] || 'info')
let listGeneration=0,detailGeneration=0
async function load() {
  const generation=++listGeneration;loading.value=true
  try {
    const result=await getPurchasePlans({page:page.value,pageSize:pageSize.value,status:status.value || undefined,keyword:keyword.value.trim() || undefined})
    if(generation!==listGeneration)return
    records.value=result.records || [];total.value=result.total || 0
  } catch { if(generation===listGeneration){records.value=[];total.value=0;ElMessage.error('采购计划加载失败')} }
  finally {if(generation===listGeneration)loading.value=false}
}
function search(){page.value=1;load()}
async function openDetail(id) {
  const generation=++detailGeneration
  showDetail.value=true;detail.value=null;detailLoading.value=true
  try {const result=await getPurchasePlan(id);if(generation===detailGeneration)detail.value=result}
  catch {ElMessage.error('计划详情加载失败')}
  finally {if(generation===detailGeneration)detailLoading.value=false}
}
async function refreshDetail(){if(detail.value?.id)await openDetail(detail.value.id);await load()}
async function act(action) {
  if(busy.value || !detail.value)return
  const id=detail.value.id;busy.value=true
  try {
    let comment=''
    if(action==='reject') {
      const input=await ElMessageBox.prompt('请填写驳回原因','驳回采购计划',{inputValidator:value=>!!value?.trim() || '驳回原因不能为空'})
      comment=input.value.trim()
    } else await ElMessageBox.confirm(action==='convert'?'按供应商和验收方式生成采购订单？':'确认'+(action==='approve'?'批准该采购计划':'提交审批')+'？','采购计划',{type:'warning'})
    if(action==='submit')await submitPurchasePlan(id)
    else if(action==='approve')await approvePurchasePlan(id,'审核通过')
    else if(action==='reject')await rejectPurchasePlan(id,comment)
    else await convertPurchasePlan(id)
    ElMessage.success('操作成功')
    await openDetail(id);await load()
  } catch(error) {if(error!=='cancel'&&error!=='close')ElMessage.error('操作未确认，请刷新核对计划状态后重试')}
  finally {busy.value=false}
}
onMounted(()=>{load();if(route.query.planId)openDetail(route.query.planId)})
</script>
<style scoped>.plan-toolbar{display:flex;gap:12px;flex-wrap:wrap;margin-bottom:16px}</style>
