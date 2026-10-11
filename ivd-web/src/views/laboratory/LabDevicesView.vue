<template>
  <clinical-table title="设备档案与项目能力" description="复用既有设备与上机记录 · 按机构和科室授权 · 能力配置不等于区间配置" :enabled="enabled" :rows="rows" :total="total" :page="page" :loading="loading" :error="error" @refresh="load" @page-change="changePage">
    <template #actions><el-button v-if="canWrite" type="primary" @click="editor.open()">新增设备</el-button></template><template #toolbar><el-input v-model="keyword" clearable placeholder="设备编码 / 名称" aria-label="设备搜索" style="max-width:300px" @keyup.enter="search" @clear="search" /><el-button @click="search">查询</el-button></template>
    <el-table-column prop="code" label="设备编码" min-width="130" /><el-table-column prop="name" label="设备名称" min-width="180" /><el-table-column prop="model" label="型号" min-width="120" /><el-table-column prop="manufacturer" label="厂家" min-width="150" /><el-table-column prop="nextCalibrationDate" label="下次校准日期" width="140" />
    <el-table-column label="状态" width="85"><template #default="{ row }"><el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '启用' : '停用' }}</el-tag></template></el-table-column>
    <el-table-column label="操作" width="170" fixed="right"><template #default="{ row }"><el-button v-if="canWrite" link type="primary" @click="editor.open(row)">编辑</el-button><el-button link type="primary" @click="capability.open(row)">可执行项目</el-button></template></el-table-column>
  </clinical-table>
  <device-editor-modal v-if="enabled && canWrite" ref="editor" @saved="load" /><device-capability-modal v-if="enabled" ref="capability" :can-write="canWrite" />
</template>
<script setup>
import { computed,ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import ClinicalTable from '@/components/laboratory/ClinicalTable.vue'
import DeviceEditorModal from '@/components/laboratory/DeviceEditorModal.vue'
import DeviceCapabilityModal from '@/components/laboratory/DeviceCapabilityModal.vue'
import { useLabCatalogList } from '@/composables/useLabCatalogList'
import { getDeviceRegistry } from '@/api/clinical'
const auth=useAuthStore(),keyword=ref(''),editor=ref(),capability=ref()
const enabled=computed(()=>auth.user?.features?.labPatients===true && ['SYSTEM_ADMIN','LAB_DIRECTOR','LAB_DOCTOR','LAB_TECH'].includes(auth.role)),canWrite=computed(()=>enabled.value && ['SYSTEM_ADMIN','LAB_DIRECTOR'].includes(auth.role))
const { rows,total,page,loading,error,load,search }=useLabCatalogList(enabled,(value,signal)=>getDeviceRegistry({ page:value,pageSize:20,keyword:keyword.value },signal))
function changePage(value) { page.value=value;load() }
</script>
