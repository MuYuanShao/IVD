<template>
  <el-dialog :model-value="modelValue" title="修改采购计划草稿" width="min(1050px, 96vw)"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving"
    @update:model-value="value => emit('update:modelValue', value)">
    <el-alert v-if="form.sourceType !== 'MANUAL'" title="来源计划保留原缺货单或补货建议关联；明细按来源核对，不在这里替换来源。" type="info" :closable="false" />
    <el-form label-width="100px" :disabled="saving">
      <el-row :gutter="16">
        <el-col :span="12"><el-form-item label="计划日期" required><el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="预计到货"><el-date-picker v-model="form.expectedDeliveryDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
      </el-row>
      <el-form-item label="结算模式" required>
        <el-select v-model="form.settlementMode">
          <el-option v-for="mode in modes" :key="mode" :value="mode" :label="mode" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" maxlength="1000" /></el-form-item>
    </el-form>
    <el-table :data="form.items" border max-height="350">
      <el-table-column label="物料" min-width="230">
        <template #default="{row}">
          <el-select v-if="form.sourceType === 'MANUAL'" v-model="row.reagentId" filterable remote
            :remote-method="searchCatalog" :loading="loadingCatalog" :disabled="saving" placeholder="输入名称查找已批准目录">
            <el-option v-for="item in optionsFor(row)" :key="item.id" :value="item.id" :label="item.genericName" />
          </el-select>
          <span v-else>{{ row.reagentName || row.reagentId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="供应商" min-width="190">
        <template #default="{row}">
          <el-select v-model="row.supplierId" filterable :disabled="saving || form.sourceType !== 'MANUAL'">
            <el-option v-for="supplier in suppliers" :key="supplier.id" :value="supplier.id" :label="supplier.name" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="数量" width="180">
        <template #default="{row}"><el-input-number v-model="row.quantity" :min="0.01" :precision="2" :disabled="saving || form.sourceType !== 'MANUAL'" /></template>
      </el-table-column>
      <el-table-column v-if="form.sourceType === 'MANUAL'" label="操作" width="85">
        <template #default="{$index}"><el-button link type="danger" :disabled="saving || form.items.length < 2" @click="form.items.splice($index,1)">移除</el-button></template>
      </el-table-column>
    </el-table>
    <el-button v-if="form.sourceType === 'MANUAL'" :disabled="saving" style="margin-top:12px" @click="form.items.push({reagentId:null,supplierId:null,quantity:1})">添加物料</el-button>
    <template #footer>
      <el-button :disabled="saving" @click="emit('update:modelValue',false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存草稿</el-button>
    </template>
  </el-dialog>
</template>
<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getCatalogList } from '@/api/catalog'
import { getSuppliers } from '@/api/license'
import { updatePurchasePlan } from '@/api/procurement'
const props = defineProps({ modelValue:Boolean, plan:Object })
const emit = defineEmits(['update:modelValue','saved'])
const modes = ['消耗结算','领用结算','验收结算','调拨结算']
const form = reactive({sourceType:'MANUAL',orderDate:'',expectedDeliveryDate:'',settlementMode:'消耗结算',remark:'',items:[]})
const saving = ref(false), suppliers = ref([]), catalogOptions = ref([]), loadingCatalog = ref(false)
let request = 0
async function searchCatalog(name='') {
  const generation = ++request
  loadingCatalog.value = true
  try {
    const result = await getCatalogList({page:1,pageSize:50,genericName:name || undefined,approvalStatus:'APPROVED'})
    if (generation === request) catalogOptions.value = result.records || []
  } catch { ElMessage.error('目录加载失败') }
  finally { if (generation === request) loadingCatalog.value = false }
}
function optionsFor(row) {
  if (!row.reagentId || catalogOptions.value.some(item => String(item.id) === String(row.reagentId))) return catalogOptions.value
  return [{id:row.reagentId,genericName:row.reagentName || String(row.reagentId)},...catalogOptions.value]
}
watch(() => props.modelValue, async open => {
  if (!open || !props.plan) return
  Object.assign(form, {
    sourceType:props.plan.sourceType,orderDate:props.plan.orderDate,expectedDeliveryDate:props.plan.expectedDeliveryDate || '',
    settlementMode:props.plan.settlementMode,remark:props.plan.remark || '',
    items:(props.plan.items || []).map(item => ({...item}))
  })
  try {suppliers.value = (await getSuppliers() || []).filter(item => item.isActive === true)}
  catch {suppliers.value=[];ElMessage.error('供应商加载失败')}
  await searchCatalog()
})
async function save() {
  if (saving.value || !props.plan?.id) return
  if (!form.orderDate || !form.items.length || form.items.some(item => !item.reagentId || !item.supplierId || !(Number(item.quantity)>0))) {
    ElMessage.warning('请核对日期、物料、供应商及正数数量')
    return
  }
  const data = {
    sourceType:form.sourceType,shortageId:props.plan.shortageId,
    supplierId:form.sourceType === 'SHORTAGE' ? form.items[0]?.supplierId : null,
    orderDate:form.orderDate,expectedDeliveryDate:form.expectedDeliveryDate || null,
    settlementMode:form.settlementMode,remark:form.remark,
    items:form.items.map(({reagentId,supplierId,quantity}) => ({reagentId,supplierId,quantity})),
    suggestions:form.items.filter(item => item.suggestionId).map(item => ({
      id:item.suggestionId,reagentId:item.reagentId,supplierId:item.supplierId,suggestedQty:item.quantity
    }))
  }
  saving.value = true
  try {
    await updatePurchasePlan(props.plan.id,data)
    ElMessage.success('计划草稿已保存，请重新提交审批')
    emit('update:modelValue',false);emit('saved')
  } catch {ElMessage.error('计划未保存，请核对当前状态')}
  finally {saving.value=false}
}
</script>
