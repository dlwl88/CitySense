<template>
  <div class="page-shell">
    <div class="page-container ai-page">
      <section class="ai-hero card-panel">
        <div>
          <h1>AI 美食助手</h1>
          <p>支持附近美食推荐、当前商家菜品推荐、美食问答和预订建议。</p>
        </div>
        <div class="hero-actions">
          <el-button @click="aiStore.startNewConversation()">新建会话</el-button>
          <el-button @click="aiStore.clearHistory()">清空窗口</el-button>
          <el-button type="primary" @click="quickAsk('推荐附近美食')">一键推荐</el-button>
        </div>
      </section>

      <section class="ai-layout">
        <article class="left-panel card-panel">
          <AiConversationPanel />

          <div class="section-title quick-section-title">
            <h2>快捷入口</h2>
            <p>点击后优先命中后端专用推荐/分析接口</p>
          </div>
          <div class="quick-list">
            <button
              v-for="scene in quickScenes"
              :key="scene"
              class="quick-item hover-lift"
              @click="quickAsk(scene)"
            >
              {{ scene }}
            </button>
          </div>
        </article>

        <article class="chat-panel card-panel">
          <div class="chat-head">
            <strong>{{ aiStore.currentConversationTitle }}</strong>
            <span>当前会话消息会实时同步到后端</span>
          </div>

          <div class="chat-list">
            <AiBubble v-for="message in aiStore.history" :key="message.id" :message="message" />
          </div>

          <div class="chat-editor">
            <el-input
              v-model="inputValue"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 5 }"
              resize="none"
              placeholder="例如：北京朝阳区火锅推荐"
              @keydown.enter.prevent="submitMessage"
            />
            <div class="editor-actions">
              <el-button type="primary" :loading="aiStore.pending" @click="submitMessage">
                发送
              </el-button>
            </div>
          </div>
        </article>
      </section>
    </div>

    <BottomNav current-tab="ai" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import BottomNav from '@/components/layout/BottomNav.vue';
import AiBubble from '@/components/ai/AiBubble.vue';
import AiConversationPanel from '@/components/ai/AiConversationPanel.vue';
import { quickScenes } from '@/mock/data';
import { useAiStore } from '@/store/ai';

const aiStore = useAiStore();
const inputValue = ref('');

onMounted(() => {
  aiStore.bootstrapConversations();
});

async function submitMessage() {
  const content = inputValue.value.trim();
  if (!content) {
    return;
  }
  inputValue.value = '';
  await aiStore.sendMessage(content);
}

async function quickAsk(scene) {
  await aiStore.sendQuickScene(scene);
}
</script>

<style scoped lang="scss">
.ai-page {
  padding-top: 18px;
}

.ai-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 20px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, #fff5ec 0%, #ffffff 100%);
}

.ai-hero h1 {
  margin: 0 0 10px;
}

.ai-hero p {
  margin: 0;
  color: #666;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.ai-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
}

.left-panel,
.chat-panel {
  padding: 18px;
}

.left-panel {
  display: grid;
  align-content: start;
  gap: 18px;
}

.quick-section-title {
  margin-top: 4px;
}

.quick-list {
  display: grid;
  gap: 12px;
}

.quick-item {
  padding: 14px 16px;
  text-align: left;
  border: 1px solid #eee;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
}

.chat-panel {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: 620px;
}

.chat-head {
  display: grid;
  gap: 6px;
  margin-bottom: 12px;
}

.chat-head span {
  font-size: 13px;
  color: #999;
}

.chat-list {
  min-height: 0;
  padding-right: 8px;
  overflow-y: auto;
}

.chat-editor {
  position: sticky;
  bottom: 0;
  display: grid;
  gap: 12px;
  padding-top: 12px;
  background: #fff;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 767px) {
  .ai-hero,
  .ai-layout {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .hero-actions {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>