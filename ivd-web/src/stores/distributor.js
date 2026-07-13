import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getDistributorList, getDistributorDetail, createDistributor,
  updateDistributor, deleteDistributor, toggleDistributorActive, getDistributorOptions
} from '@/api/distributor'

/**
 * 配送商管理 Store
 */
export const useDistributorStore = defineStore('distributor', () => {
  const list = ref([])
  const loading = ref(false)
  const total = ref(0)

  async function fetchList(params = {}) {
    loading.value = true
    try {
      const data = await getDistributorList(params)
      list.value = data.records || []
      total.value = data.total || 0
    } finally {
      loading.value = false
    }
  }

  async function fetchDetail(id) {
    return await getDistributorDetail(id)
  }

  async function create(data) {
    return await createDistributor(data)
  }

  async function update(id, data) {
    return await updateDistributor(id, data)
  }

  async function remove(id) {
    return await deleteDistributor(id)
  }

  async function toggle(id) {
    return await toggleDistributorActive(id)
  }

  async function fetchOptions(keyword) {
    return await getDistributorOptions(keyword)
  }

  return { list, loading, total, fetchList, fetchDetail, create, update, remove, toggle, fetchOptions }
})
