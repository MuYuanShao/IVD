import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { isFullAccessAdmin } from '@/utils/adminAccess.mjs'

export function useWorkspaceNavigation() {
  const route = useRoute()
  const router = useRouter()
  const auth = useAuthStore()
  const isDemo = computed(() =>
    route.matched.some((item) => item.path === '/workspace'),
  )
  const homePath = computed(() =>
    isDemo.value ? '/workspace/home' : '/dashboard',
  )
  const tree = computed(() =>
    router.options.routes.find(
      (item) => item.path === (isDemo.value ? '/workspace' : '/'),
    ),
  )
  const mapEntry = (item, parent) => ({
    title: item.meta?.title,
    path: `${parent}/${item.path}`,
    children: item.children
      ?.filter((child) => !child.meta?.hidden)
      .map((child) => mapEntry(child, `${parent}/${item.path}`)),
  })
  const menus = computed(() => {
    if (isDemo.value)
      return tree.value.children
        .filter((item) => !item.meta?.hidden)
        .map((item) => mapEntry(item, tree.value.path))
    const result = []
    for (const item of tree.value.children.filter(
      (item) => item.name && !item.meta?.hidden &&
        (isFullAccessAdmin(auth.user) ||
          ((!item.meta?.feature || auth.user?.features?.[item.meta.feature] === true) &&
           (!item.meta?.roles || item.meta.roles.includes(auth.role)))),
    )) {
      const entry = { title: item.meta.title, path: `/${item.path}` }
      if (!item.meta.navGroup) {
        result.push(entry)
        continue
      }
      let group = result.find(
        (menu) => menu.title === item.meta.navGroup && menu.children,
      )
      if (!group) {
        group = {
          title: item.meta.navGroup,
          path: `group:${item.meta.navGroup}`,
          children: [],
        }
        result.push(group)
      }
      group.children.push(entry)
    }
    return result
  })
  const activeGroup = computed(() =>
    menus.value.find(
      (item) =>
        route.path === item.path ||
        item.children?.some((child) => child.path === route.path) ||
        route.path.startsWith(`${item.path}/`),
    ),
  )
  const subMenus = computed(
    () =>
      activeGroup.value?.children ||
      menus.value.flatMap((item) => item.children || [item]),
  )
  const breadcrumbs = computed(() => {
    if (!isDemo.value)
      return [
        ...(activeGroup.value?.children
          ? [
              {
                title: activeGroup.value.title,
                path: activeGroup.value.children[0].path,
              },
            ]
          : []),
        { title: route.meta.title, path: route.path },
      ]
    return route.matched
      .filter((item) => item.meta.title && item.path !== '/workspace')
      .map((item) => ({ title: item.meta.title, path: item.path }))
  })
  return { menus, subMenus, breadcrumbs, activeGroup, isDemo, homePath }
}
