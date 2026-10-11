import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { isFullAccessAdmin } from '@/utils/adminAccess.mjs'
export function useClinicalAccess() {
  const auth = useAuthStore()
  const enabled = computed(() => auth.user?.features?.labPatients === true &&
    (isFullAccessAdmin(auth.user) || ['LAB_TECH', 'LAB_DOCTOR', 'LAB_DIRECTOR'].includes(auth.role)))
  return { enabled }
}
