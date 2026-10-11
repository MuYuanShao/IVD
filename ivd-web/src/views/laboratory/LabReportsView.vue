<template>
  <clinical-table title="检验报告" :enabled="enabled" :rows="rows" :total="total" :page="page" :loading="loading" :error="error" @refresh="load" @page-change="changePage">
    <template #actions><el-button @click="router.push('/lab/samples')">从样本建立报告</el-button></template><template #toolbar><el-tag type="info">业务签发 · 未使用 CA 数字签名</el-tag><el-button v-if="sampleId" link @click="router.replace('/lab/reports')">清除样本条件</el-button></template>
    <el-table-column prop="maskedNumber" label="报告编号（脱敏）" min-width="160" /><el-table-column prop="edition" label="版本" width="90" /><el-table-column prop="sampleId" label="样本 ID" min-width="160" /><el-table-column label="状态" width="140"><template #default="{ row }"><el-tag :type="statusOption(reportStatuses,row.status).type">{{ statusOption(reportStatuses,row.status).label }}</el-tag></template></el-table-column><el-table-column label="操作" width="130" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="detail.open(row.id)">详情 / 流转</el-button></template></el-table-column>
  </clinical-table>
  <report-detail-modal v-if="enabled" ref="detail" @changed="load" />
</template>
<script setup>
import { computed,ref,watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import ClinicalTable from '@/components/laboratory/ClinicalTable.vue'
import ReportDetailModal from '@/components/laboratory/ReportDetailModal.vue'
import { useClinicalAccess } from '@/composables/useClinicalAccess'
import { useLabCatalogList } from '@/composables/useLabCatalogList'
import { getReports } from '@/api/clinical'
import { reportStatuses,statusOption } from '@/utils/clinicalOptions.mjs'
const route=useRoute(),router=useRouter(),detail=ref(),sampleId=computed(()=>typeof route.query.sampleId==='string' ? route.query.sampleId : null)
const { enabled }=useClinicalAccess(),{ rows,total,page,loading,error,load,search }=useLabCatalogList(enabled,(value,signal)=>getReports({ page:value,pageSize:20,sampleId:sampleId.value },signal))
watch(sampleId,search)
function changePage(value) { page.value=value;load() }
</script>
