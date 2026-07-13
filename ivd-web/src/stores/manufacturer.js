import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getManufacturerList, getManufacturerDetail, createManufacturer,
  updateManufacturer, deleteManufacturer, toggleManufacturerActive, getManufacturerOptions
} from '@/api/manufacturer'

/**
 * 厂家管理 Store
 */
export const useManufacturerStore = defineStore('manufacturer', () => {
  const list = ref([])
  const loading = ref(false)
  const total = ref(0)

  async function fetchList(params = {}) {
    loading.value = true
    try {
      const data = await getManufacturerList(params)
      list.value = data.records || []
      total.value = data.total || 0
    } finally {
      loading.value = false
    }
  }

  async function fetchDetail(id) {
    return await getManufacturerDetail(id)
  }

  async function create(data) {
    return await createManufacturer(data)
  }

  async function update(id, data) {
    return await updateManufacturer(id, data)
  }

  async function remove(id) {
    return await deleteManufacturer(id)
  }

  async function toggle(id) {
    return await toggleManufacturerActive(id)
  }

  async function fetchOptions(keyword) {
    return await getManufacturerOptions(keyword)
  }

  return { list, loading, total, fetchList, fetchDetail, create, update, remove, toggle, fetchOptions }
})
