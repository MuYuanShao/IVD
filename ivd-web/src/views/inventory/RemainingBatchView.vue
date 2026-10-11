<template>
  <section class="page-container">
    <div class="page-header"><div><h2>在库批次列表</h2><p>剩余库存实时查询 · 数量大于零的批次，包含过期及受限库存</p></div><el-button @click="load" :loading="loading">刷新</el-button></div>
    <el-form inline @submit.prevent="search">
      <el-form-item label="关键字"><el-input v-model="keyword" placeholder="试剂名称、编码、厂家或批号" clearable maxlength="100" @keyup.enter="search" /></el-form-item>
      <el-form-item label="库房层级"><el-select v-model="level" placeholder="全部层级" clearable style="width: 140px"><el-option v-for="(name,index) in ['中心库','科室库','组库']" :key="index" :label="name" :value="index+1" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-table v-loading="loading" :data="rows" border stripe row-key="id" style="width: 100%">
      <el-table-column prop="code" label="试剂编码" min-width="120" show-overflow-tooltip />
      <el-table-column prop="name" label="试剂名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="manufacturer" label="生产厂家" min-width="140" show-overflow-tooltip />
      <el-table-column prop="specification" label="规格型号" min-width="130" show-overflow-tooltip />
      <el-table-column prop="batchNo" label="批号" min-width="130" />
      <el-table-column prop="expiryDate" label="有效期" width="110" />
      <el-table-column prop="quantity" label="剩余数量" width="100" align="right" />
      <el-table-column prop="unit" label="单位" width="70" />
      <el-table-column prop="warehouse" label="库房" min-width="120" />
      <el-table-column prop="location" label="库位" min-width="110" />
      <el-table-column prop="status" label="库存状态" min-width="130" />
      <el-table-column prop="updatedAt" label="库存同步时间" min-width="180" />
      <template #empty>{{ error ? '读取失败，请重试' : '暂无剩余库存批次' }}</template>
    </el-table>
    <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
  </section>
</template>
<script setup>
import { ref,onMounted,onBeforeUnmount } from 'vue'
import { getRemainingBatches } from '@/api/inventory'
const rows=ref([]),total=ref(0),page=ref(1),pageSize=ref(20),keyword=ref(''),level=ref(null),loading=ref(false),error=ref('')
let controller,version=0
async function load(){
  const current=++version;controller?.abort();controller=new AbortController();loading.value=true;error.value=''
  try {
    const data=await getRemainingBatches({page:page.value,pageSize:pageSize.value,keyword:keyword.value.trim(),level:level.value},controller.signal)
    if(current===version){rows.value=data.records;total.value=data.total}
  }catch(e){if(current===version && e.code!=='ERR_CANCELED'){rows.value=[];total.value=0;error.value=e.response?.data?.message || e.message || '读取失败'}}
  finally{if(current===version)loading.value=false}
}
function search(){page.value=1;load()}
function reset(){keyword.value='';level.value=null;search()}
onMounted(load)
onBeforeUnmount(()=>{++version;controller?.abort()})
</script>
<style scoped>
.page-header{display:flex;justify-content:space-between;align-items:center;gap:16px;margin-bottom:20px}.page-header h2{margin:0 0 6px}.page-header p{margin:0;color:var(--el-text-color-secondary);font-size:13px}.el-pagination{margin-top:20px;justify-content:flex-end}.el-alert{margin-bottom:12px}
</style>
