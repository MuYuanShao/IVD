import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
export function useLabCatalogAccess() {
  const auth = useAuthStore()
  const enabled = computed(
    () =>
      auth.user?.features?.labCatalog === true &&
      ['SYSTEM_ADMIN', 'LAB_DIRECTOR', 'LAB_DOCTOR', 'LAB_TECH'].includes(
        auth.role,
      ),
  )
  const canWrite = computed(
    () => enabled.value && ['SYSTEM_ADMIN', 'LAB_DIRECTOR'].includes(auth.role),
  )
  return { enabled, canWrite }
}
