<template>
  <section class="page-container import-page">
    <el-alert v-if="!enabled" title="导入未启用或当前角色无权访问" type="info" :closable="false" />
    <template v-else>
      <header><div><h2>检验业务 Excel 导入</h2><p>模板 → 预检 → 人工确认 → 事务执行回执</p></div></header>
      <el-alert title="只接受 xlsx，最多10MB / 5000行。编码须为文本，拒绝公式、未知列、重复键、错误单位与跨机构引用。不覆盖已存在资料或已签发报告，不自动复核、签发或扣库存。" type="info" :closable="false" show-icon />
      <el-card shadow="never" class="import-card"><div class="import-controls"><el-select v-model="resource" :disabled="busy" aria-label="导入资源" @change="reset"><el-option v-for="option in resources" :key="option.value" :value="option.value" :label="option.label" /></el-select><el-button :disabled="busy || !resource" @click="downloadTemplate">下载空模板</el-button><el-upload :auto-upload="false" :show-file-list="false" accept=".xlsx" :disabled="busy" :on-change="choose"><el-button :disabled="busy || !resource">选择工作簿</el-button></el-upload><span>{{ file ? '已选择工作簿' : '未选择文件' }}</span><el-button type="primary" :loading="busy && stage==='preflight'" :disabled="busy || !file" @click="preflight">预检</el-button></div>
        <el-alert v-if="error" :title="error" type="error" :closable="false" />
        <template v-if="batch"><el-descriptions :column="3" border class="import-summary"><el-descriptions-item label="批次 ID">{{ batch.id }}</el-descriptions-item><el-descriptions-item label="状态">{{ stateLabels[batch.status] || '未知' }}</el-descriptions-item><el-descriptions-item label="数据行数">{{ batch.rows }}</el-descriptions-item><el-descriptions-item label="已成功">{{ batch.succeeded }}</el-descriptions-item><el-descriptions-item label="错误行">{{ batch.errors.length }}</el-descriptions-item></el-descriptions>
          <div class="import-controls"><el-button type="success" :loading="busy && stage==='execute'" :disabled="busy || !['READY','EXECUTING'].includes(batch.status)" @click="execute">{{ batch.status==='EXECUTING' ? '查询 / 恢复执行' : '确认执行导入' }}</el-button><el-button :disabled="busy" @click="refresh">刷新回执</el-button><span v-if="batch.status==='EXECUTING'">执行中；重复确认不会重复写入，异常中断后可恢复未完成单元。</span></div>
          <el-table :data="batch.errors"><el-table-column prop="row" label="工作表行号" width="130" /><el-table-column prop="field" label="校验范围" width="110" /><el-table-column prop="message" label="错误说明（不显示患者原始值）" min-width="400" /></el-table>
        </template><el-empty v-else description="下载当前资源的空模板，填写后先预检" />
      </el-card>
    </template>
  </section>
</template>
<script setup>
import { computed,onBeforeUnmount,ref,watch } from 'vue'
import { ElMessage,ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getImportTemplate,preflightImport,executeImport,getImportBatch } from '@/api/clinical'
import { allowedImportResources,clinicalError } from '@/utils/clinicalOptions.mjs'
const auth=useAuthStore(),resources=computed(()=>allowedImportResources(auth.role)),enabled=computed(()=>auth.user?.features?.labPatients===true && resources.value.length>0),resource=ref(''),file=ref(null),batch=ref(null),busy=ref(false),stage=ref(''),error=ref('')
const stateLabels={ READY:'预检通过，待确认',INVALID:'预检未通过',EXECUTING:'执行中',EXECUTED:'执行完成' }
let generation=0,timer
function reset() { ++generation;file.value=null;batch.value=null;error.value='';clearTimeout(timer) }
watch(resources,(options)=>{ if(!options.some(option=>option.value===resource.value)) { resource.value=options[0]?.value || '';reset() } },{ immediate:true })
function choose(upload) { reset();const selected=upload.raw;if(!selected || !selected.name.toLowerCase().endsWith('.xlsx') || selected.size>10*1024*1024) { ElMessage.error('请选择不超过10MB的 xlsx 文件');return }file.value=selected }
async function downloadTemplate() { try { const blob=await getImportTemplate(resource.value);const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=`ivd-import-${resource.value}.xlsx`;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000) } catch(failure) { ElMessage.error(clinicalError(failure)) } }
async function preflight() { if(busy.value || !file.value)return;busy.value=true;stage.value='preflight';error.value='';const current=++generation;try { const result=await preflightImport(resource.value,file.value);if(current===generation)batch.value=result } catch(failure) { if(current===generation)error.value=clinicalError(failure) } finally { if(current===generation)busy.value=false } }
async function execute() { if(busy.value || !batch.value)return;try { await ElMessageBox.confirm('确认执行预检批次？基础资料按行处理，同份报告和结果整组提交；不会自动签发。','确认导入',{ type:'warning' }) } catch { return }busy.value=true;stage.value='execute';error.value='';const current=++generation;try { const result=await executeImport(batch.value.id);if(current===generation){batch.value=result;schedule()} } catch(failure) { if(current===generation)error.value=clinicalError(failure) } finally { if(current===generation)busy.value=false } }
function schedule() { clearTimeout(timer);if(batch.value?.status==='EXECUTING')timer=setTimeout(refresh,15000) }
async function refresh() { if(!batch.value)return;const current=generation;try { const result=await getImportBatch(batch.value.id);if(current===generation){batch.value=result;schedule()} } catch(failure) { if(current===generation)error.value=clinicalError(failure) } }
onBeforeUnmount(()=>{ ++generation;clearTimeout(timer);file.value=null;batch.value=null })
</script>
<style scoped>header { margin-bottom:16px }header p { color:var(--scm-muted);margin-top:4px }.import-card { margin-top:16px }.import-controls { display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:16px }.import-controls .el-select { width:280px }.import-summary { margin:16px 0 }</style>
