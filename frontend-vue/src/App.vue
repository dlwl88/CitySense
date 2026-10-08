<template>
  <div class="app-root">
    <RouterView v-slot="{ Component, route }">
      <Transition name="route-fade" mode="out-in">
        <component :is="Component" :key="route.fullPath" />
      </Transition>
    </RouterView>
    <AiAssistantDrawer />
    <LoginDialog />
  </div>
</template>

<script setup>
import AiAssistantDrawer from '@/components/ai/AiAssistantDrawer.vue';
import LoginDialog from '@/components/auth/LoginDialog.vue';
import { useUserStore } from '@/store/user';

const userStore = useUserStore();

onMounted(async () => {
  await userStore.initAuth();
  window.addEventListener('hm-auth-expired', handleAuthExpired);
});

onBeforeUnmount(() => {
  window.removeEventListener('hm-auth-expired', handleAuthExpired);
});

function handleAuthExpired() {
  userStore.clearAuth();
  userStore.openLoginDialog();
}
</script>

<style scoped>
.app-root {
  min-height: 100vh;
  background: #f7f7f7;
}

.route-fade-enter-active,
.route-fade-leave-active {
  transition: all 0.24s ease;
}

.route-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.route-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
