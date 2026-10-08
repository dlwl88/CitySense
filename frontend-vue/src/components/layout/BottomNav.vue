<template>
  <nav class="bottom-nav card-panel">
    <RouterLink
      v-for="item in navItems"
      :key="item.path"
      class="nav-item"
      :class="{ active: currentTab === item.name }"
      :to="item.path"
    >
      <el-icon><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>

<script setup>
import { Grid, House, MagicStick, User } from '@element-plus/icons-vue';

defineProps({
  currentTab: {
    type: String,
    default: 'home'
  }
});

const navItems = [
  { name: 'home', label: '首页', path: '/', icon: House },
  { name: 'category', label: '分类', path: '/category', icon: Grid },
  { name: 'profile', label: '我的', path: '/profile', icon: User },
  { name: 'ai', label: 'AI助手', path: '/ai-assistant', icon: MagicStick }
];
</script>

<style scoped lang="scss">
.bottom-nav {
  position: fixed;
  left: 50%;
  bottom: 10px;
  transform: translateX(-50%);
  z-index: 20;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  width: min(680px, calc(100% - 20px));
  padding: 10px 8px;
  border-radius: 18px;
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 8px;
  color: #666;
  border-radius: 12px;
  transition: all 0.2s ease;
}

.nav-item:hover,
.nav-item.active {
  color: var(--dp-primary);
  background: #fff3eb;
}

.nav-item span {
  font-size: 12px;
}

@media (max-width: 767px) {
  .bottom-nav {
    bottom: 0;
    width: 100%;
    border-radius: 18px 18px 0 0;
  }
}
</style>
