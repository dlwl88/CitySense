import { createRouter, createWebHashHistory } from 'vue-router';
import HomePage from '@/pages/home/HomePage.vue';
import CategoryPage from '@/pages/category/CategoryPage.vue';
import ShopDetailPage from '@/pages/shop/ShopDetailPage.vue';
import ProfilePage from '@/pages/profile/ProfilePage.vue';
import AiAssistantPage from '@/pages/ai/AiAssistantPage.vue';

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomePage,
      meta: { tab: 'home', title: '首页' }
    },
    {
      path: '/category/:id?',
      name: 'category',
      component: CategoryPage,
      meta: { tab: 'category', title: '分类' }
    },
    {
      path: '/shop/:id',
      name: 'shop-detail',
      component: ShopDetailPage,
      meta: { title: '商家详情' }
    },
    {
      path: '/profile',
      name: 'profile',
      component: ProfilePage,
      meta: { tab: 'profile', title: '我的' }
    },
    {
      path: '/ai-assistant',
      name: 'ai-assistant',
      component: AiAssistantPage,
      meta: { tab: 'ai', title: 'AI助手' }
    }
  ],
  scrollBehavior() {
    return { top: 0 };
  }
});

export default router;
