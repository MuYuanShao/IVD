<template>
  <clinical-table title="批次 / 消耗临床关联追溯" description="仅展示明确关联的历史结果版本；不按批号或患者哈希猜测关联，不重复计费" :enabled="enabled" :rows="rows" :total="total" :page="page" :loading="loading" :error="error" @refresh="search" @page-change="changePage">
    <template #toolbar><el-input v-model="batchId" clearable placeholder="实际批次 ID（可从库存追溯获得）" aria-label="实际批次ID" style="max-width:300px" /><el-input v-model="consumptionId" clearable placeholder="消耗记录 ID（可选）" aria-label="消耗记录ID" style="max-width:250px" /><el-button type="primary" @click="search">查询</el-button></template>
    <el-table-column label="患者档案 ID" min-width="130"><template #default="{ row }">{{ row.result.patientId }}</template></el-table-column><el-table-column label="检验项目 / 版本" min-width="180"><template #default="{ row }">{{ row.result.itemName }} · {{ row.result.version }}</template></el-table-column><el-table-column label="批号" min-width="130"><template #default="{ row }">{{ row.evidence.batch.batchNo }}</template></el-table-column><el-table-column label="设备" min-width="130"><template #default="{ row }">{{ row.result.deviceName || '无本地设备' }}</template></el-table-column><el-table-column label="消耗 ID" min-width="160"><template #default="{ row }">{{ row.evidence.consumption?.id || '未关联' }}</template></el-table-column><el-table-column label="操作" width="110" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="detail.open(row.result.sampleId)">查看样本</el-button></template></el-table-column>
  </clinical-table><sample-detail-modal v-if="enabled" ref="detail" />
</template>
<script setup>
import { computed,ref } from 'vue'
import ClinicalTable from '@/components/laboratory/ClinicalTable.vue'
import SampleDetailModal from '@/components/laboratory/SampleDetailModal.vue'
import { useClinicalAccess } from '@/composables/useClinicalAccess'
import { useLabCatalogList } from '@/composables/useLabCatalogList'
import { traceClinicalEvidence } from '@/api/clinical'
const { enabled }=useClinicalAccess(),batchId=ref(''),consumptionId=ref(''),detail=ref(),submitted=ref(false)
const ready=computed(()=>enabled.value && submitted.value)
const { rows,total,page,loading,error,load }=useLabCatalogList(ready,(value,signal)=>traceClinicalEvidence({ page:value,pageSize:20,batchId:batchId.value || undefined,consumptionId:consumptionId.value || undefined },signal))
function search() { page.value=1;if(!batchId.value && !consumptionId.value){error.value='请提供实际批次 ID 或消耗 ID';rows.value=[];total.value=0;return}const already=submitted.value;submitted.value=true;if(already)load() }
function changePage(value) { page.value=value;load() }
</script>
