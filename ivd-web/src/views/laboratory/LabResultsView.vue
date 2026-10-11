<template>
  <clinical-table title="患者 / 设备检测关联查询" :enabled="enabled" :rows="rows" :total="total" :page="page" :loading="loading" :error="error" @refresh="load" @page-change="changePage">
    <template #toolbar>
      <el-select v-model="deviceId" clearable filterable remote :remote-method="searchDevices" placeholder="按授权设备查询（输入编码搜索）" style="width:300px" @change="search"><el-option v-for="device in devices" :key="device.id" :value="device.id" :label="`${device.code} · ${device.name}`" /></el-select>
      <el-tag v-if="patientId" type="info">指定患者</el-tag><el-button v-if="patientId" link @click="router.replace('/lab/results')">清除患者条件</el-button>
    </template>
    <el-table-column prop="patientId" label="患者档案 ID" min-width="140" />
    <el-table-column prop="itemName" label="检验项目" min-width="140" />
    <el-table-column prop="deviceName" label="实际设备" min-width="140"><template #default="{ row }">{{ row.deviceName || '不绑定本地设备' }}</template></el-table-column>
    <el-table-column prop="testingInstitutionName" label="实际检测机构" min-width="160" />
    <el-table-column prop="testedAt" label="检测时间" min-width="175"><template #default="{ row }">{{ row.testedAt?.replace('T',' ') }}</template></el-table-column>
    <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="detail.open(row.sampleId)">查看样本</el-button></template></el-table-column>
  </clinical-table>
  <sample-detail-modal v-if="enabled" ref="detail" @changed="load" />
</template>
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ClinicalTable from '@/components/laboratory/ClinicalTable.vue'
import SampleDetailModal from '@/components/laboratory/SampleDetailModal.vue'
import { useClinicalAccess } from '@/composables/useClinicalAccess'
import { useLabCatalogList } from '@/composables/useLabCatalogList'
import { getLabDeviceOptions } from '@/api/laboratory'
import { searchClinicalResults } from '@/api/clinical'
import { clinicalError } from '@/utils/clinicalOptions.mjs'
const route=useRoute(),router=useRouter(),detail=ref(),deviceId=ref(null),devices=ref([])
const patientId=computed(()=>typeof route.query.patientId==='string' ? route.query.patientId : null)
const { enabled }=useClinicalAccess()
const { rows,total,page,loading,error,load,search }=useLabCatalogList(enabled,(value,signal)=>searchClinicalResults({ page:value,pageSize:20,deviceId:deviceId.value,patientId:patientId.value },signal))
let generation=0
async function searchDevices(keyword='') {
  const current=++generation
  try { const options=await getLabDeviceOptions(keyword);if(current===generation)devices.value=options }
  catch(failure) { if(current===generation)ElMessage.error(clinicalError(failure)) }
}
watch(patientId,search)
watch(enabled,(value)=>{ if(value)searchDevices();else { ++generation;devices.value=[] } },{ immediate:true })
onBeforeUnmount(()=>++generation)
function changePage(value) { page.value=value;load() }
</script>
