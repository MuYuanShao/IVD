<template>
  <el-dialog v-model="visible" title="结果版本与区间快照" width="min(850px,94vw)" class="scm-theme" destroy-on-close @closed="clear">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-loading="loading"><el-empty v-if="!loading && !rows.length" description="暂无版本记录" />
      <el-card v-for="row in rows" :key="row.version" shadow="never" class="version-card">
        <h3>版本 {{ row.version }} · {{ row.itemName }}</h3>
        <p>结果：{{ row.result }} {{ row.unit }} · 检测时间：{{ row.testedAt?.replace('T',' ') }}</p>
        <p>设备：{{ row.deviceName || '不绑定本地设备' }} · 检测机构：{{ row.testingInstitutionName }}</p>
        <p>参考提示：{{ statusOption(resultStatuses,row.referenceStatus).label }} · {{ row.referenceReason }}</p>
        <p v-if="row.referenceInterval">参考快照：{{ intervalText(row.referenceInterval) }}</p>
        <p>测量提示：{{ statusOption(resultStatuses,row.measurementStatus).label }} · {{ row.measurementReason }}</p>
        <p v-if="row.measurementInterval">测量快照：{{ intervalText(row.measurementInterval) }}</p>
      </el-card>
    </div>
  </el-dialog>
</template>
<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { getResultVersions } from '@/api/clinical'
import { clinicalError, resultStatuses, statusOption } from '@/utils/clinicalOptions.mjs'
const visible=ref(false), loading=ref(false), error=ref(''), rows=ref([])
let generation=0
function clear() { ++generation;rows.value=[];error.value='';loading.value=false }
function intervalText(value) { return `${value.resultType==='NUMERIC' ? `${value.lowerBound} – ${value.upperBound} ${value.unit}` : value.normalText}；${value.methodology}；有效期 ${value.validFrom} 至 ${value.validTo}` }
async function open(id) {
  clear();visible.value=true;loading.value=true;const current=++generation
  try { const data=await getResultVersions(id);if(current===generation)rows.value=data }
  catch(failure) { if(current===generation)error.value=clinicalError(failure) }
  finally { if(current===generation)loading.value=false }
}
onBeforeUnmount(clear)
defineExpose({ open })
</script>
<style scoped>
.version-card { margin:12px 0 }
.version-card p { margin-top:8px;overflow-wrap:anywhere }
</style>
