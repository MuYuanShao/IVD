<template>
  <lab-catalog-table
    title="参考区间与测量范围"
    create-label="新增区间"
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
      ><el-select
        v-model="kind"
        placeholder="所有类型"
        aria-label="区间类型"
        @change="search"
        ><el-option label="所有类型" value="" /><el-option
          v-for="option in intervalKindOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value" /></el-select
    ></template>

    <el-table-column label="检验项目" min-width="160" show-overflow-tooltip
      ><template #default="{ row }"
        ><div>{{ row.itemName }}</div>
        <small class="lab-hint">{{ row.itemCode }}</small></template
      ></el-table-column
    >
    <el-table-column label="区间类型" min-width="140"
      ><template #default="{ row }">{{
        optionLabel(intervalKindOptions, row.kind)
      }}</template></el-table-column
    >
    <el-table-column label="设备 ID" min-width="100"
      ><template #default="{ row }">{{
        row.deviceId || '不限设备'
      }}</template></el-table-column
    >
    <el-table-column prop="methodology" label="方法学" min-width="100" />
    <el-table-column label="适用性别" width="100"
      ><template #default="{ row }">{{
        referenceSexLabels[row.sex]
      }}</template></el-table-column
    >
    <el-table-column label="年龄（天）" min-width="130"
      ><template #default="{ row }"
        >{{ row.minAgeDays }} ～ {{ row.maxAgeDays }}</template
      ></el-table-column
    >
    <el-table-column label="参考值 / 范围" min-width="170"
      ><template #default="{ row }">{{
        row.resultType === ResultType.NUMERIC
          ? `${row.lowerBound} ～ ${row.upperBound} ${row.unit}`
          : row.normalText
      }}</template></el-table-column
    >
    <el-table-column label="有效期" min-width="210"
      ><template #default="{ row }"
        >{{ row.validFrom }} ～ {{ row.validTo }}</template
      ></el-table-column
    >
  </lab-catalog-table>
  <lab-interval-editor-modal v-if="canWrite" ref="editor" @saved="load" />
</template>
<script setup>
import { ref } from 'vue'
import LabCatalogTable from '@/components/business/LabCatalogTable.vue'
import LabIntervalEditorModal from '@/components/laboratory/LabIntervalEditorModal.vue'
import { useLabCatalogAccess } from '@/composables/useLabCatalogAccess'
import { useLabCatalogList } from '@/composables/useLabCatalogList'
import { getLabIntervals } from '@/api/laboratory'
import {
  optionLabel,
  intervalKindOptions,
  referenceSexLabels,
  ResultType,
} from '@/utils/laboratoryOptions.mjs'
const { enabled, canWrite } = useLabCatalogAccess()
const kind = ref(''),
  editor = ref()
const { rows, total, page, loading, error, load, search } = useLabCatalogList(
  enabled,
  (page, signal) =>
    getLabIntervals(
      { page, pageSize: 20, kind: kind.value || undefined },
      signal,
    ),
)
function changePage(value) {
  page.value = value
  load()
}
</script>
<style scoped>
.lab-hint {
  font-size: 12px;
  color: var(--scm-muted);
  line-height: 1.6;
}
</style>
