<template>
  <article class="comment-item">
    <div class="header">
      <div class="user-info">
        <el-avatar :src="comment.avatar" :size="42" />
        <div>
          <strong>{{ comment.user }}</strong>
          <el-rate :model-value="comment.score" disabled size="small" />
        </div>
      </div>
    </div>

    <p class="content">{{ comment.content }}</p>

    <div v-if="comment.images?.length" class="image-list">
      <img v-for="image in comment.images" :key="image" :src="image" alt="评论图片" />
    </div>
  </article>
</template>

<script setup>
defineProps({
  comment: {
    type: Object,
    required: true
  }
});
</script>

<style scoped lang="scss">
.comment-item {
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}

.header,
.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.content {
  margin: 12px 0;
  line-height: 1.8;
  color: #555;
}

.image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.image-list img {
  width: 92px;
  height: 92px;
  border-radius: 10px;
  object-fit: cover;
  background: #fff4eb;
}
</style>
