<template>
  <clinical-table title="样本与检测结果" :enabled="enabled" :rows="rows" :total="total" :page="page" :loading="loading" :error="error" @refresh="load" @page-change="changePage">
    <template #actions><el-button @click="router.push('/lab/patients')">患者档案 / 登记样本</el-button></template>
    <template #toolbar><el-tag v-if="patientId" type="info">指定患者的授权样本</el-tag><el-button v-if="patientId" link @click="router.replace('/lab/samples')">查看本科室全部样本</el-button></template>
    <el-table-column prop="maskedNumber" label="样本编号（脱敏）" min-width="180" />
    <el-table-column prop="patientId" label="患者档案 ID" min-width="160" />
    <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusOption(sampleStatuses,row.status).type">{{ statusOption(sampleStatuses,row.status).label }}</el-tag></template></el-table-column>
    <el-table-column label="操作" width="140" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="detail.open(row.id)">详情 / 检测结果</el-button></template></el-table-column>
  </clinical-table>
  <sample-detail-modal v-if="enabled" ref="detail" @changed="load" />
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ClinicalTable from '@/components/laboratory/ClinicalTable.vue'
import SampleDetailModal from '@/components/laboratory/SampleDetailModal.vue'
import { useClinicalAccess } from '@/composables/useClinicalAccess'
import { useLabCatalogList } from '@/composables/useLabCatalogList'
import { getSamples } from '@/api/clinical'
import { sampleStatuses, statusOption } from '@/utils/clinicalOptions.mjs'
const route=useRoute(), router=useRouter(), detail=ref()
const patientId=computed(()=>typeof route.query.patientId==='string' ? route.query.patientId : null)
const { enabled }=useClinicalAccess()
const { rows,total,page,loading,error,load,search }=useLabCatalogList(enabled,(value,signal)=>getSamples({ page:value,pageSize:20,patientId:patientId.value },signal))
watch(patientId,search)
function changePage(value) { page.value=value; load() }
</script>
