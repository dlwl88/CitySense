<template>
  <div class="bubble-row" :class="message.role">
    <div class="bubble">
      <p>{{ message.content }}</p>
      <div v-if="message.cards?.length" class="card-list">
        <div v-for="card in message.cards" :key="card.name" class="mini-card">
          <strong>{{ card.name }}</strong>
          <span>评分/标签：{{ card.score }}</span>
          <span>人均/价格：¥{{ card.price }}</span>
          <p>{{ card.reason }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  message: {
    type: Object,
    required: true
  }
});
</script>

<style scoped lang="scss">
.bubble-row {
  display: flex;
  margin-bottom: 12px;
}

.bubble-row.user {
  justify-content: flex-end;
}

.bubble-row.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: min(86%, 520px);
  padding: 12px 14px;
  border-radius: 18px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.user .bubble {
  color: #222;
  background: #ffffff;
}

.assistant .bubble {
  color: #444;
  background: #f3f4f6;
}

.bubble p {
  margin: 0;
  line-height: 1.7;
  white-space: pre-wrap;
}

.card-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.mini-card {
  display: grid;
  gap: 6px;
  padding: 12px;
  border-radius: 12px;
  background: #fff;
}

.mini-card span,
.mini-card p {
  font-size: 13px;
  color: #666;
}
</style>
