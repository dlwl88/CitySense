<template>
  <div class="category-grid card-panel">
    <RouterLink
      v-for="item in categories"
      :key="item.id"
      class="category-item hover-lift"
      :to="`/category/${item.id}`"
    >
      <div class="icon-circle">{{ item.icon }}</div>
      <strong>{{ item.name }}</strong>
      <span>{{ item.description }}</span>
    </RouterLink>
  </div>
</template>

<script setup>
defineProps({
  categories: {
    type: Array,
    default: () => []
  }
});
</script>

<style scoped lang="scss">
.category-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  padding: 18px;
}

.category-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 10px;
  text-align: center;
  border-radius: 12px;
  background: linear-gradient(180deg, #fffaf6 0%, #ffffff 100%);
}

.icon-circle {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  font-size: 24px;
  background: #fff0e6;
}

.category-item strong {
  font-size: 14px;
}

.category-item span {
  font-size: 12px;
  color: #888;
}

@media (max-width: 767px) {
  .category-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    padding: 14px;
  }
}
</style>
