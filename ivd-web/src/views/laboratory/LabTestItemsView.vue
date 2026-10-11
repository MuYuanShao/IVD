<template>
  <lab-catalog-table
    title="检验项目"
    create-label="新增项目"
    :enabled="enabled"
    :can-write="canWrite"
    :rows="rows"
    :total="total"
    :page="page"
    :loading="loading"
    :error="error"
    @create="editor.open()"
    @edit="editor.open($event)"
    @search="search"
    @page-change="changePage"
  >
    <template #toolbar
      ><el-input
        v-model="keyword"
        clearable
        placeholder="搜索编码 / 项目名称"
        aria-label="搜索检验项目"
        @keyup.enter="search"
        @clear="search"
    /></template>

    <el-table-column prop="code" label="项目编码" min-width="110" />
    <el-table-column
      prop="name"
      label="项目名称"
      min-width="160"
      show-overflow-tooltip
    />
    <el-table-column prop="methodology" label="方法学" min-width="110" />
    <el-table-column label="结果类型" width="100"
      ><template #default="{ row }">{{
        optionLabel(resultTypeOptions, row.resultType)
      }}</template></el-table-column
    >
    <el-table-column prop="unit" label="单位" min-width="90" />
  </lab-catalog-table>
  <lab-test-item-editor-modal v-if="canWrite" ref="editor" @saved="load" />
</template>
<script setup>
import { ref } from 'vue'
import LabCatalogTable from '@/components/business/LabCatalogTable.vue'
import LabTestItemEditorModal from '@/components/laboratory/LabTestItemEditorModal.vue'
import { useLabCatalogAccess } from '@/composables/useLabCatalogAccess'
import { useLabCatalogList } from '@/composables/useLabCatalogList'
import { getLabItems } from '@/api/laboratory'
import { optionLabel, resultTypeOptions } from '@/utils/laboratoryOptions.mjs'
const { enabled, canWrite } = useLabCatalogAccess()
const keyword = ref(''),
  editor = ref()
const { rows, total, page, loading, error, load, search } = useLabCatalogList(
  enabled,
  (page, signal) =>
    getLabItems({ page, pageSize: 20, keyword: keyword.value }, signal),
)
function changePage(value) {
  page.value = value
  load()
}
</script>
