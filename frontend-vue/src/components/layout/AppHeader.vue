<template>
  <header class="app-header card-panel">
    <div class="location-box hover-lift" @click="cityDialogVisible = true">
      <el-icon><Location /></el-icon>
      <span>{{ userStore.city }}</span>
      <el-icon><ArrowDown /></el-icon>
    </div>

    <el-input
      v-model="keywordModel"
      class="search-input"
      placeholder="搜索商家 / 美食关键词"
      clearable
      @keyup.enter="emitSearch"
    >
      <template #prefix>
        <el-icon><Search /></el-icon>
      </template>
    </el-input>

    <div class="header-actions">
      <el-button class="ai-entry desktop-only" type="primary" @click="openAiDrawer">
        <el-icon><MagicStick /></el-icon>
        AI助手
      </el-button>

      <button class="user-entry hover-lift" @click="handleUserClick">
        <el-avatar :src="userStore.displayAvatar" :size="30" />
        <span class="desktop-only">{{ userStore.displayName }}</span>
      </button>
    </div>
  </header>

  <el-dialog v-model="cityDialogVisible" title="选择城市" width="360px">
    <div class="city-list">
      <button
        v-for="city in cities"
        :key="city"
        class="city-item hover-lift"
        :class="{ active: city === userStore.city }"
        @click="selectCity(city)"
      >
        {{ city }}
      </button>
    </div>
  </el-dialog>
</template>

<script setup>
import { ArrowDown, Location, MagicStick, Search } from '@element-plus/icons-vue';
import { useAiStore } from '@/store/ai';
import { useUserStore } from '@/store/user';

const props = defineProps({
  keyword: {
    type: String,
    default: ''
  },
  cities: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['update:keyword', 'search']);

const router = useRouter();
const userStore = useUserStore();
const aiStore = useAiStore();
const cityDialogVisible = ref(false);
const keywordModel = ref(props.keyword);

watch(
  () => props.keyword,
  (value) => {
    keywordModel.value = value;
  }
);

function emitSearch() {
  emit('update:keyword', keywordModel.value);
  emit('search', keywordModel.value);
}

function selectCity(city) {
  userStore.setCity(city);
  cityDialogVisible.value = false;
  emitSearch();
}

function openAiDrawer() {
  aiStore.openDrawer();
}

function handleUserClick() {
  if (userStore.isLoggedIn) {
    router.push('/profile');
    return;
  }
  userStore.openLoginDialog();
}
</script>

<style scoped lang="scss">
.app-header {
  position: sticky;
  top: 12px;
  z-index: 15;
  display: grid;
  grid-template-columns: 150px 1fr auto;
  gap: 12px;
  padding: 14px;
  margin: 12px 0 18px;
}

.location-box {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: 999px;
  background: #fff7f0;
  color: var(--dp-primary);
  cursor: pointer;
  font-weight: 600;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 999px;
  box-shadow: none;
  background: #f6f6f6;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.ai-entry {
  border-radius: 999px;
}

.user-entry {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px 6px 6px;
  border: none;
  border-radius: 999px;
  background: #fff7f0;
  color: #333;
  cursor: pointer;
}

.city-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.city-item {
  padding: 12px 14px;
  border: 1px solid #eee;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.city-item.active {
  color: var(--dp-primary);
  border-color: rgba(255, 103, 0, 0.35);
  background: #fff4eb;
}

@media (max-width: 767px) {
  .app-header {
    top: 0;
    grid-template-columns: 96px 1fr auto;
    margin: 0 0 12px;
    border-radius: 0 0 12px 12px;
  }

  .user-entry {
    padding-right: 6px;
  }
}
</style>
