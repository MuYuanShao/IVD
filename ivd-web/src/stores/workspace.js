import { defineStore } from 'pinia'
import { ref } from 'vue'
import { user as mockUser } from '@/mock/workspace'

export const useWorkspaceStore = defineStore('workspace', () => {
  const user = ref({ ...mockUser })
  const functions = [
    { title: '采购申请', path: '/workspace/trade/purchase' },
    { title: '库存查询', path: '/workspace/central/query' },
    { title: '科室库存', path: '/workspace/central/department' },
    { title: '入库管理', path: '/workspace/trade/inbound' },
    { title: '批次效期', path: '/workspace/quality/expiry' },
    { title: '运营分析', path: '/workspace/analysis/overview' },
  ]
  const selectedFunctions = ref(functions.slice(0, 4).map((item) => item.path))
  const range = ref(30)
  function resetPreferences() {
    selectedFunctions.value = functions.slice(0, 4).map((item) => item.path)
    range.value = 30
  }
  return { user, functions, selectedFunctions, range, resetPreferences }
})
