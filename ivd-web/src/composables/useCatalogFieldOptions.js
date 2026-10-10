import { ref } from 'vue'
import { getCatalogFieldOptionGroups } from '@/api/catalogFieldOption'

const optionGroups = ref({
  TEST_GROUP: [],
  DETECTION_METHOD: [],
  VOLUME_TYPE: [],
  INSURANCE_PAYMENT_TYPE: []
})
let loaded = false
let pending = null

export function useCatalogFieldOptions() {
  async function loadCatalogFieldOptions(force = false) {
    if (loaded && !force) return optionGroups.value
    if (pending && !force) return pending
    pending = getCatalogFieldOptionGroups()
      .then(data => {
        optionGroups.value = { ...optionGroups.value, ...(data || {}) }
        loaded = true
        return optionGroups.value
      })
      .finally(() => { pending = null })
    return pending
  }

  return { optionGroups, loadCatalogFieldOptions }
}
