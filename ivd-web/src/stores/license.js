import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import {
  getLicenseList, getLicenseDetail, createLicense, updateLicense,
  getLicenseStats, getExpiryAlerts,
  deleteAttachment as deleteAttachmentApi,
  replaceAttachment as replaceAttachmentApi,
  getChangeLogs
} from '@/api/license'

/**
 * M03 证照管理 Store
 */
export const useLicenseStore = defineStore('license', () => {
  const list = ref([])
  const loading = ref(false)
  const total = ref(0)

  const stats = reactive({ total: 0, active: 0, expiring: 0, expiredFrozen: 0 })
  const expiryAlerts = ref([])

  async function fetchList(params = {}) {
    loading.value = true
    try {
      const data = await getLicenseList(params)
      list.value = data.records || []
      total.value = data.total || 0
    } finally {
      loading.value = false
    }
  }

  async function fetchStats() {
    try {
      const data = await getLicenseStats()
      Object.assign(stats, data)
    } catch { /* 静默 */ }
  }

  async function fetchDetail(id) {
    return await getLicenseDetail(id)
  }

  async function create(data) {
    return await createLicense(data)
  }

  async function update(id, data) {
    return await updateLicense(id, data)
  }

  async function fetchExpiryAlerts(days = 90) {
    try {
      expiryAlerts.value = await getExpiryAlerts(days)
    } catch { /* 静默 */ }
  }

  // ─── 附件操作 ───
  async function deleteAttachment(licenseId, objectKey) {
    return await deleteAttachmentApi(licenseId, objectKey)
  }

  async function replaceAttachment(licenseId, objectKey, formData) {
    return await replaceAttachmentApi(licenseId, objectKey, formData)
  }

  // ─── 变更记录 ───
  const changeLogs = ref([])
  const changeLogTotal = ref(0)
  const changeLogLoading = ref(false)

  async function fetchChangeLogs(params = {}) {
    changeLogLoading.value = true
    try {
      const data = await getChangeLogs(params)
      changeLogs.value = data.records || []
      changeLogTotal.value = data.total || 0
    } finally {
      changeLogLoading.value = false
    }
  }

  return {
    list, loading, total, stats, expiryAlerts,
    fetchList, fetchStats, fetchDetail, create, update, fetchExpiryAlerts,
    deleteAttachment, replaceAttachment,
    changeLogs, changeLogTotal, changeLogLoading, fetchChangeLogs
  }
})
