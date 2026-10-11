import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { resultStatuses, sampleStatuses, statusOption, localDateTimeNow,allowedImportResources,reportStatuses } from '../../src/utils/clinicalOptions.mjs'

test('missing clinical evidence never becomes normal or an invalid Element tag type', () => {
  for (const code of ['MISSING','CONFLICT','UNIT_MISMATCH','INVALID_RESULT','NOT_APPLICABLE','UNKNOWN']) {
    const value=statusOption(resultStatuses,code)
    assert.notEqual(value.type,'success');assert.ok(['info','warning','danger'].includes(value.type))
  }
  assert.equal(statusOption(sampleStatuses,'RECEIVED').label,'已接收')
  assert.equal(localDateTimeNow(new Date(2026,9,10,9,8,7)),'2026-10-10T09:08:07')
})
test('clinical routes and separate modal/panel components compile without storing patient data', () => {
  const files=['views/laboratory/LabPatientsView.vue','views/laboratory/LabSamplesView.vue','views/laboratory/LabResultsView.vue',
    'components/laboratory/ClinicalTable.vue','components/laboratory/PatientEditorModal.vue','components/laboratory/SampleEditorModal.vue',
    'components/laboratory/SampleDetailModal.vue','components/laboratory/ResultEditorModal.vue','components/laboratory/ResultListPanel.vue','components/laboratory/ResultHistoryModal.vue',
    'views/laboratory/LabDevicesView.vue','views/laboratory/LabReportsView.vue','views/laboratory/LabImportsView.vue','views/laboratory/LabEvidenceTraceView.vue',
    'components/laboratory/DeviceEditorModal.vue','components/laboratory/DeviceCapabilityModal.vue','components/laboratory/ResultEvidenceModal.vue',
    'components/laboratory/ReportEditorModal.vue','components/laboratory/ReportActionModal.vue','components/laboratory/ReportDetailModal.vue','components/laboratory/ReportPdfModal.vue']
  for(const file of files) {
    const source=readFileSync(new URL(`../../src/${file}`,import.meta.url),'utf8')
    const { descriptor,errors }=parse(source,{ filename:file });assert.deepEqual(errors,[])
    compileScript(descriptor,{ id:file });assert.deepEqual(compileTemplate({ source:descriptor.template.content,filename:file,id:file }).errors,[])
    assert.ok(!/localStorage|sessionStorage|console\.log/.test(source),file)
    if(file.startsWith('views/'))assert.ok(!source.includes('<el-dialog'),file)
  }
})
test('admin import choices exclude clinical records and report status tags remain valid',()=>{
  assert.deepEqual(allowedImportResources('LAB_TECH').map(row=>row.value),['PATIENTS','SAMPLES','RESULTS','REPORTS'])
  assert.ok(allowedImportResources('SYSTEM_ADMIN').every(row=>!row.clinical))
  assert.ok(!allowedImportResources('LAB_DIRECTOR').some(row=>row.value==='INSTITUTIONS'))
  for(const value of Object.values(reportStatuses))assert.ok(['info','primary','success','warning','danger'].includes(value.type))
  assert.equal(reportStatuses.REVOKED.type,'danger')
})
