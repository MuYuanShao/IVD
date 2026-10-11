import { onBeforeUnmount, ref, watch } from 'vue'
export function useLabCatalogList(enabled, fetchPage) {
  const rows = ref([]),
    total = ref(0),
    page = ref(1),
    loading = ref(false),
    error = ref('')
  let controller,
    version = 0
  async function load() {
    controller?.abort()
    const current = ++version
    if (!enabled.value) {
      rows.value = []
      total.value = 0
      loading.value = false
      return
    }
    controller = new AbortController()
    loading.value = true
    error.value = ''
    try {
      const data = await fetchPage(page.value, controller.signal)
      if (current === version) {
        rows.value = data.records
        total.value = data.total
      }
    } catch (failure) {
      if (current === version && failure.code !== 'ERR_CANCELED') {
        rows.value = []
        total.value = 0
        error.value =
          failure.response?.data?.message || failure.message || '数据读取失败'
      }
    } finally {
      if (current === version) loading.value = false
    }
  }
  function search() {
    page.value = 1
    return load()
  }
  watch(enabled, search, { immediate: true })
  onBeforeUnmount(() => {
    controller?.abort()
    ++version
  })
  return { rows, total, page, loading, error, load, search }
}
