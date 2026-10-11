<template>
  <el-dialog v-model="visible" title="报告 PDF 阅览" width="min(1100px,96vw)" top="5vh" class="scm-theme" destroy-on-close @closed="clear">
    <el-alert title="业务签发未使用 CA 数字签名；草稿有明确未签发标识。所有文件均从后端鉴权读取，不使用公共存储链接。" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><div v-loading="loading"><iframe v-if="url" :src="`${url}#toolbar=0`" title="受控报告PDF预览" class="report-pdf-frame" /></div>
    <template #footer><el-button @click="visible=false">关闭</el-button><el-button :disabled="!signed || loading" @click="exportPdf('PRINT')">打印阅览</el-button><el-button type="primary" :disabled="!signed || loading" @click="exportPdf('DOWNLOAD')">下载 PDF</el-button></template>
  </el-dialog>
</template>
<script setup>
import { onBeforeUnmount,ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getReportPdf } from '@/api/clinical'
import { clinicalError } from '@/utils/clinicalOptions.mjs'
const visible=ref(false),loading=ref(false),error=ref(''),url=ref(''),id=ref(null),signed=ref(false)
let generation=0
const downloads=new Set()
function clear() { ++generation;if(url.value)URL.revokeObjectURL(url.value);url.value='';id.value=null;error.value='';for(const value of downloads)URL.revokeObjectURL(value);downloads.clear() }
async function open(report) { clear();id.value=report.id;signed.value=report.status==='SIGNED';visible.value=true;loading.value=true;const current=++generation;try { const blob=await getReportPdf(report.id,'PREVIEW');if(current===generation)url.value=URL.createObjectURL(blob) } catch(failure) { if(current===generation)error.value=clinicalError(failure) } finally { if(current===generation)loading.value=false } }
async function exportPdf(purpose) { if(loading.value || !signed.value)return;loading.value=true;try { const blob=await getReportPdf(id.value,purpose);const fileUrl=URL.createObjectURL(blob);downloads.add(fileUrl);const link=document.createElement('a');link.href=fileUrl;if(purpose==='DOWNLOAD')link.download=`ivd-report-${id.value}.pdf`;else { link.target='_blank';link.rel='noopener' }link.click() } catch(failure) { ElMessage.error(clinicalError(failure)) } finally { loading.value=false } }
onBeforeUnmount(clear)
defineExpose({ open })
</script>
<style scoped>.report-pdf-frame { width:100%;height:calc(82vh - 150px);min-height:260px;border:1px solid var(--scm-border);margin-top:12px }</style>
