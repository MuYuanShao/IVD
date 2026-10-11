<template>
  <el-dialog v-model="visible" :title="`可执行项目 · ${device?.name || ''}`" width="min(800px,94vw)" class="scm-theme" destroy-on-close :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" @closed="clear">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert title="区间配置不等于设备能力。仅启用的项目能力可用于新仪器结果；停用不重写历史结果。" type="info" :closable="false" />
    <div class="capability-toolbar"><el-select v-model="itemId" filterable remote :remote-method="searchItems" placeholder="搜索项目编码或名称（最多20项）" aria-label="检验项目"><el-option v-for="item in items" :key="item.id" :value="item.id" :label="`${item.code} · ${item.name}`" :disabled="!item.active" /></el-select><el-button v-if="canWrite" type="primary" :loading="saving" :disabled="!itemId" @click="add">新增能力</el-button></div>
    <el-table v-loading="loading" :data="rows" row-key="itemId"><el-table-column prop="itemCode" label="项目编码" min-width="120" /><el-table-column prop="itemName" label="检验项目" min-width="180" /><el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column v-if="canWrite" label="操作" width="100"><template #default="{ row }"><el-button link type="primary" :disabled="saving" @click="toggle(row)">{{ row.active ? '停用' : '启用' }}</el-button></template></el-table-column></el-table>
  </el-dialog>
</template>
<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDeviceCapabilities,setDeviceCapability } from '@/api/clinical'
import { getLabItems } from '@/api/laboratory'
import { clinicalError } from '@/utils/clinicalOptions.mjs'
defineProps({ canWrite:Boolean })
const visible=ref(false),device=ref(null),rows=ref([]),items=ref([]),itemId=ref(null),loading=ref(false),saving=ref(false),error=ref('')
let generation=0,itemGeneration=0
function clear() { ++generation;++itemGeneration;device.value=null;rows.value=[];items.value=[];itemId.value=null;error.value='' }
async function searchItems(keyword='') { const current=++itemGeneration;try { const data=await getLabItems({ page:1,pageSize:20,keyword });if(current===itemGeneration)items.value=data.records } catch(failure) { if(current===itemGeneration)ElMessage.error(clinicalError(failure)) } }
async function load() { const current=++generation;loading.value=true;try { const data=await getDeviceCapabilities(device.value.id);if(current===generation)rows.value=data } catch(failure) { if(current===generation)error.value=clinicalError(failure) } finally { if(current===generation)loading.value=false } }
async function open(row) { if(saving.value)return;clear();device.value=row;visible.value=true;await Promise.all([load(),searchItems()]) }
async function write(data) { if(saving.value)return;saving.value=true;try { await setDeviceCapability(device.value.id,data);ElMessage.success('能力配置已保存');await load() } catch(failure) { ElMessage.error(clinicalError(failure)) } finally { saving.value=false } }
function add() { const existing=rows.value.find(row=>row.itemId===itemId.value);if(existing){ElMessage.warning('项目已配置，请使用启用或停用操作');return}write({ itemId:itemId.value,active:true,version:null }) }
function toggle(row) { write({ itemId:row.itemId,active:!row.active,version:row.version }) }
onBeforeUnmount(clear)
defineExpose({ open })
</script>
<style scoped>.capability-toolbar { display:flex;gap:12px;margin:16px 0 }.capability-toolbar .el-select { flex:1 }</style>
