<template>
  <clinical-table title="患者档案" :enabled="enabled" :rows="rows" :total="total" :page="page" :loading="loading" :error="error" @refresh="load" @page-change="changePage">
    <template #actions><el-button type="primary" @click="editor.open()">新增患者</el-button></template>
    <el-table-column prop="maskedNumber" label="患者编号（脱敏）" min-width="170" />
    <el-table-column prop="maskedName" label="姓名（脱敏）" min-width="130" />
    <el-table-column prop="version" label="资料版本" width="100" />
    <el-table-column label="操作" min-width="250" fixed="right"><template #default="{ row }">
      <el-button link type="primary" @click="editor.open(row.id)">详情 / 编辑</el-button>
      <el-button link type="primary" @click="sampleEditor.open(row)">登记样本</el-button>
      <el-button link type="primary" @click="router.push({ path:'/lab/samples', query:{ patientId:row.id } })">样本与结果</el-button>
      <el-button link type="primary" @click="router.push({ path:'/lab/results', query:{ patientId:row.id } })">检测关联</el-button>
    </template></el-table-column>
  </clinical-table>
  <patient-editor-modal v-if="enabled" ref="editor" @saved="load" />
  <sample-editor-modal v-if="enabled" ref="sampleEditor" @saved="openSamples" />
</template>
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import ClinicalTable from '@/components/laboratory/ClinicalTable.vue'
import PatientEditorModal from '@/components/laboratory/PatientEditorModal.vue'
import SampleEditorModal from '@/components/laboratory/SampleEditorModal.vue'
import { useClinicalAccess } from '@/composables/useClinicalAccess'
import { useLabCatalogList } from '@/composables/useLabCatalogList'
import { getPatients } from '@/api/clinical'
const router = useRouter(), editor = ref(), sampleEditor = ref()
const { enabled } = useClinicalAccess()
const { rows, total, page, loading, error, load } = useLabCatalogList(enabled, (value, signal) => getPatients({ page:value, pageSize:20 }, signal))
function changePage(value) { page.value = value; load() }
function openSamples(sample) { router.push({ path:'/lab/samples', query:{ patientId:sample.patientId } }) }
</script>
