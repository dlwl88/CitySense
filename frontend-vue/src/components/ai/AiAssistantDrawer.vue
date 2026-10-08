<template>
  <div class="assistant-trigger hover-lift" @click="aiStore.openDrawer()">
    <el-icon><MagicStick /></el-icon>
    <span class="desktop-only">AI美食助手</span>
  </div>

  <el-drawer
    v-model="aiStore.drawerVisible"
    title="AI美食助手"
    size="420px"
    class="assistant-drawer"
    append-to-body
  >
    <div class="drawer-content">
      <AiConversationPanel compact />

      <div class="quick-actions">
        <el-button
          v-for="action in quickScenes"
          :key="action"
          round
          @click="handleQuickAsk(action)"
        >
          {{ action }}
        </el-button>
      </div>

      <div class="message-list">
        <AiBubble v-for="message in aiStore.history" :key="message.id" :message="message" />
      </div>

      <div class="bottom-bar">
        <el-input
          v-model="inputValue"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          resize="none"
          placeholder="输入你的美食问题，回车发送"
          @keydown.enter.prevent="submitMessage"
        />
        <div class="actions">
          <el-button @click="aiStore.clearHistory()">清空窗口</el-button>
          <el-button type="primary" :loading="aiStore.pending" @click="submitMessage">
            发送
          </el-button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue';
import { MagicStick } from '@element-plus/icons-vue';
import AiBubble from '@/components/ai/AiBubble.vue';
import AiConversationPanel from '@/components/ai/AiConversationPanel.vue';
import { quickScenes } from '@/mock/data';
import { useAiStore } from '@/store/ai';

const aiStore = useAiStore();
const inputValue = ref('');

watch(
  () => aiStore.drawerVisible,
  (visible) => {
    if (visible) {
      aiStore.bootstrapConversations();
    }
  }
);

async function submitMessage() {
  const content = inputValue.value.trim();
  if (!content) {
    return;
  }
  inputValue.value = '';
  await aiStore.sendMessage(content);
}

async function handleQuickAsk(action) {
  await aiStore.sendQuickScene(action);
}
</script>

<style scoped lang="scss">
.assistant-trigger {
  position: fixed;
  right: 18px;
  bottom: 112px;
  z-index: 21;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  color: #fff;
  background: linear-gradient(135deg, #ff6700 0%, #ff914d 100%);
  border-radius: 999px;
  box-shadow: 0 10px 20px rgba(255, 103, 0, 0.24);
  cursor: pointer;
}

.drawer-content {
  display: grid;
  grid-template-rows: auto auto 1fr auto;
  gap: 14px;
  height: 100%;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.message-list {
  min-height: 0;
  padding: 6px;
  overflow-y: auto;
  background: #fafafa;
  border-radius: 12px;
}

.bottom-bar {
  display: grid;
  gap: 12px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 767px) {
  .assistant-trigger {
    right: 12px;
    bottom: 90px;
    padding: 12px;
  }
}
</style>