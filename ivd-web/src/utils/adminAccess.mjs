// Mirror the authenticated server policy; never use this as a backend authorization substitute.
export function isFullAccessAdmin(user) {
  return user?.username === 'admin' && user?.role === 'SYSTEM_ADMIN'
}
