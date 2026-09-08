<template>
  <nav class="scm-submenu" aria-label="二级页面入口">
    <div class="submenu-shell">
      <button
        v-if="overflow"
        class="scroll-control"
        aria-label="向左滚动页面入口"
        :disabled="atStart"
        @click="scroll(-1)"
      >
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <div ref="strip" class="submenu-inner" @scroll="measure">
        <router-link
          v-for="item in subMenus"
          :key="item.path"
          :to="item.path"
          :aria-current="route.path === item.path ? 'page' : undefined"
          >{{ item.title }}</router-link
        >
      </div>
      <button
        v-if="overflow"
        class="scroll-control"
        aria-label="向右滚动页面入口"
        :disabled="atEnd"
        @click="scroll(1)"
      >
        <el-icon><ArrowRight /></el-icon>
      </button>
    </div>
  </nav>
</template>
<script setup>
import { useRoute } from 'vue-router'
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useWorkspaceNavigation } from '@/composables/useWorkspaceNavigation'
const route = useRoute()
const { subMenus } = useWorkspaceNavigation()
const strip = ref(null)
const overflow = ref(false)
const atStart = ref(true)
const atEnd = ref(false)
let observer
function measure() {
  const el = strip.value
  if (!el) return
  overflow.value = el.scrollWidth > el.clientWidth + 2
  atStart.value = el.scrollLeft < 2
  atEnd.value = el.scrollLeft + el.clientWidth >= el.scrollWidth - 2
}
function scroll(direction) {
  strip.value?.scrollBy({
    left: direction * 280,
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
      ? 'instant'
      : 'smooth',
  })
}
onMounted(() => {
  observer = new ResizeObserver(measure)
  observer.observe(strip.value)
  measure()
})
watch(
  () => route.path,
  async () => {
    await nextTick()
    strip.value
      ?.querySelector('[aria-current="page"]')
      ?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
    measure()
  },
)
onBeforeUnmount(() => observer?.disconnect())
</script>
<style scoped>
.scm-submenu {
  background: white;
  border-bottom: 1px solid var(--scm-border);
}
.submenu-shell {
  display: flex;
  max-width: var(--scm-content-width);
  margin: auto;
}
.submenu-inner {
  display: flex;
  gap: 4px;
  overflow-x: auto;
  flex: 1;
  min-width: 0;
  scrollbar-width: none;
  padding: 0 2px;
}
.submenu-inner::-webkit-scrollbar {
  display: none;
}
.scroll-control {
  width: 28px;
  flex-shrink: 0;
  border: 0;
  background: white;
  color: var(--scm-primary);
  cursor: pointer;
}
.scroll-control:disabled {
  color: #cbd5d8;
  cursor: default;
}
a {
  padding: 13px 14px 11px;
  color: #718089;
  font-size: 12px;
  white-space: nowrap;
  border-bottom: 2px solid transparent;
}
a:hover {
  color: var(--scm-primary);
  background: #f6fbfa;
}
a.router-link-active {
  color: var(--scm-primary);
  background: var(--scm-primary-soft);
  border-bottom-color: var(--scm-primary);
}
</style>
