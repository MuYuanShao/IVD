import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
export function useClinicalAccess() {
  const auth = useAuthStore()
  const enabled = computed(() => auth.user?.features?.labPatients === true &&
    ['LAB_TECH', 'LAB_DOCTOR', 'LAB_DIRECTOR'].includes(auth.role))
  return { enabled }
}
