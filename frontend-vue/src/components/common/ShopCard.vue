<template>
  <article class="shop-card card-panel hover-lift" @click="goDetail">
    <img class="cover" :src="shop.cover" :alt="shop.name" />

    <div class="content">
      <div class="top-row">
        <h3>{{ shop.name }}</h3>
        <span class="tag-chip">{{ shop.status }}</span>
      </div>

      <div class="meta-row">
        <el-rate :model-value="shop.score" disabled allow-half size="small" />
        <span>{{ shop.score }}</span>
        <span>人均 ¥{{ shop.price }}</span>
        <span>{{ shop.distance }}</span>
      </div>

      <div class="tag-row">
        <span v-for="tag in shop.tags" :key="tag" class="small-tag">{{ tag }}</span>
      </div>

      <p class="recommend">{{ shop.recommendText }}</p>

      <div class="bottom-row">
        <div class="actions">
          <el-button size="small" text bg @click.stop="goDetail">查看详情</el-button>
          <el-button size="small" text bg type="primary" @click.stop="$emit('ai', shop)">
            AI 推荐
          </el-button>
        </div>
      </div>
    </div>
  </article>
</template>

<script setup>
const props = defineProps({
  shop: {
    type: Object,
    required: true
  }
});

const router = useRouter();

function goDetail() {
  router.push(`/shop/${props.shop.id}`);
}
</script>

<style scoped lang="scss">
.shop-card {
  display: grid;
  grid-template-columns: 180px 1fr;
  gap: 16px;
  padding: 14px;
  cursor: pointer;
}

.cover {
  width: 100%;
  height: 136px;
  object-fit: cover;
  border-radius: 10px;
  background: #fff4eb;
}

.content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.top-row,
.meta-row,
.bottom-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.top-row h3 {
  margin: 0;
  font-size: 18px;
}

.meta-row {
  justify-content: flex-start;
  flex-wrap: wrap;
  color: #777;
  font-size: 13px;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.small-tag {
  padding: 4px 8px;
  font-size: 12px;
  color: #8a4a14;
  border-radius: 999px;
  background: #fff5ec;
}

.recommend {
  margin: 0;
  color: #666;
  line-height: 1.7;
}

.actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 767px) {
  .shop-card {
    grid-template-columns: 1fr;
  }

  .cover {
    height: 180px;
  }

  .top-row,
  .bottom-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
