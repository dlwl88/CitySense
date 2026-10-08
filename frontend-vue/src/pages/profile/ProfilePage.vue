<template>
  <div class="page-shell">
    <div class="page-container">
      <section v-if="!userStore.isLoggedIn" class="empty-login card-panel">
        <h2>登录后查看个人中心</h2>
        <p>你可以使用后端短信验证码登录，联调当前 Spring Boot 接口。</p>
        <el-button type="primary" @click="userStore.openLoginDialog()">立即登录</el-button>
      </section>

      <template v-else>
        <section class="profile-top card-panel">
          <div class="user-base">
            <el-avatar :src="profile.avatar" :size="88" />
            <div>
              <h1>{{ profile.nickname }}</h1>
              <p>欢迎回来，今天也去发现一家值得安利的宝藏店铺吧。</p>
            </div>
          </div>
          <div class="profile-actions">
            <el-button :type="signedToday ? 'success' : 'default'" :disabled="signedToday" @click="signToday">
              {{ signedToday ? `今日已签到（已连续 ${userStore.signCount} 天）` : '今日签到' }}
            </el-button>
            <el-button @click="openPanel('publish')">发布笔记</el-button>
            <el-button type="primary" @click="userStore.logout()">退出登录</el-button>
          </div>
        </section>

        <section class="stats-grid">
          <button v-for="item in stats" :key="item.key" class="stat-card card-panel hover-lift" @click="openPanel(item.key)">
            <strong>{{ item.value }}</strong>
            <span>{{ item.title }}</span>
          </button>
        </section>

        <section class="card-panel menu-panel">
          <div class="section-title">
            <h2>常用功能</h2>
            <p>个人中心基础能力入口，均已接通实际交互</p>
          </div>
          <div class="menu-list">
            <button
              v-for="entry in menuEntries"
              :key="entry.key"
              class="menu-item hover-lift"
              @click="openPanel(entry.key)"
            >
              <span>{{ entry.title }}</span>
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
        </section>

        <section class="card-panel blog-panel">
          <div class="section-title">
            <h2>我的笔记</h2>
            <p>真实联调 `/blog/of/me`</p>
          </div>
          <div class="my-blog-list">
            <article v-for="blog in myBlogs" :key="blog.id" class="my-blog-item hover-lift">
              <div class="blog-main">
                <strong>{{ blog.title || '无标题笔记' }}</strong>
                <p>{{ blog.content || '暂无内容' }}</p>
                <div class="blog-meta">
                  <span>点赞 {{ blog.liked || 0 }}</span>
                  <span>评论 {{ blog.comments || 0 }}</span>
                  <span>店铺ID {{ blog.shopId || '-' }}</span>
                </div>
              </div>
              <div class="blog-actions">
                <el-button size="small" @click="viewBlog(blog)">查看详情</el-button>
                <el-button v-if="blog.shopId" size="small" type="primary" @click="goShop(blog.shopId)">去店铺</el-button>
              </div>
            </article>
            <div v-if="!myBlogs.length" class="empty-block">当前还没有发布笔记</div>
          </div>
        </section>
      </template>
    </div>

    <BottomNav current-tab="profile" />

    <el-dialog v-model="dialogs.favorites" title="我的收藏" width="760px">
      <div class="dialog-list">
        <article v-for="shop in favoriteShops" :key="shop.id" class="panel-item hover-lift">
          <div>
            <strong>{{ shop.name }}</strong>
            <p>{{ shop.address }}</p>
            <span>评分 {{ shop.score }} ｜ 人均 ¥{{ shop.price }}</span>
          </div>
          <div class="panel-actions">
            <el-button size="small" @click="goShop(shop.id)">查看店铺</el-button>
            <el-button size="small" type="danger" @click="removeFavorite(shop.id)">取消收藏</el-button>
          </div>
        </article>
        <div v-if="!favoriteShops.length" class="empty-block">当前还没有收藏店铺</div>
      </div>
    </el-dialog>

    <el-dialog v-model="dialogs.orders" title="我的订单" width="760px">
      <div class="dialog-list">
        <article v-for="order in userStore.orderHistory" :key="order.orderId" class="panel-item hover-lift">
          <div>
            <strong>{{ order.shopName || '未知店铺' }} ｜ {{ order.voucherTitle || '代金券订单' }}</strong>
            <p>订单ID：{{ order.orderId }} ｜ 支付金额 ¥{{ order.payAmount || '-' }}</p>
            <span>{{ formatOrderStatus(order.status) }} ｜ {{ formatTime(order.updateTime || order.createdTime) }}</span>
          </div>
          <div class="panel-actions">
            <el-button size="small" @click="refreshOrderStatus(order)">刷新状态</el-button>
            <el-button v-if="order.shopId" size="small" @click="goShop(order.shopId)">查看店铺</el-button>
            <el-button size="small" type="danger" @click="removeOrder(order.orderId)">移除记录</el-button>
          </div>
        </article>
        <div v-if="!userStore.orderHistory.length" class="empty-block">当前还没有下单记录</div>
      </div>
    </el-dialog>

    <el-dialog v-model="dialogs.addresses" title="地址管理" width="640px">
      <div class="address-form">
        <el-input v-model="addressForm.contact" placeholder="联系人" />
        <el-input v-model="addressForm.phone" placeholder="手机号" />
        <el-input v-model="addressForm.detail" placeholder="详细地址，例如朝阳区国贸地铁站 A 口" />
        <el-button type="primary" @click="saveAddress">新增地址</el-button>
      </div>
      <div class="dialog-list compact-top">
        <article v-for="address in userStore.addressList" :key="address.id" class="panel-item hover-lift">
          <div>
            <strong>{{ address.contact }} {{ address.phone }}</strong>
            <p>{{ address.city }} {{ address.detail }}</p>
          </div>
          <div class="panel-actions">
            <el-button size="small" type="danger" @click="userStore.removeAddress(address.id)">删除</el-button>
          </div>
        </article>
        <div v-if="!userStore.addressList.length" class="empty-block">你还没有保存常用地址</div>
      </div>
    </el-dialog>

    <el-dialog v-model="dialogs.settings" title="设置" width="520px">
      <div class="setting-actions">
        <el-button @click="clearAiHistory">清空 AI 记录</el-button>
        <el-button @click="userStore.clearFavorites()">清空收藏</el-button>
        <el-button @click="userStore.clearOrders()">清空订单记录</el-button>
        <el-button type="danger" @click="userStore.logout()">退出登录</el-button>
      </div>
    </el-dialog>

    <el-dialog v-model="dialogs.support" title="联系客服" width="520px">
      <div class="support-box">
        <p>当前前端已联调用户、店铺、博客、优惠券、支付、AI 会话接口。</p>
        <p>如需排查问题，请提供：操作步骤、接口路径、浏览器控制台报错。</p>
        <p>建议联系信息：400-800-8083 / support@hmdp.local</p>
      </div>
    </el-dialog>

    <el-dialog v-model="dialogs.reviews" title="我的评论" width="760px">
      <div class="dialog-list">
        <article v-for="blog in myBlogs" :key="blog.id" class="panel-item hover-lift">
          <div>
            <strong>{{ blog.title || '无标题笔记' }}</strong>
            <p>{{ blog.content || '暂无内容' }}</p>
            <span>点赞 {{ blog.liked || 0 }} ｜ 评论 {{ blog.comments || 0 }} ｜ 店铺ID {{ blog.shopId || '-' }}</span>
          </div>
          <div class="panel-actions">
            <el-button size="small" @click="viewBlog(blog)">查看详情</el-button>
            <el-button v-if="blog.shopId" size="small" @click="goShop(blog.shopId)">去店铺</el-button>
          </div>
        </article>
        <div v-if="!myBlogs.length" class="empty-block">你还没有评论内容</div>
      </div>
    </el-dialog>

    <el-dialog v-model="dialogs.publish" title="发布笔记" width="640px" @close="resetBlogForm">
      <div class="publish-form">
        <el-input v-model="blogForm.shopId" placeholder="店铺ID（可选）" />
        <el-input v-model="blogForm.title" placeholder="标题" />
        <el-input v-model="blogForm.images" placeholder="图片地址，多个用英文逗号分隔" />
        <el-input
          v-model="blogForm.content"
          type="textarea"
          :rows="6"
          resize="none"
          placeholder="分享一下你的探店体验"
        />
        <div class="publish-actions">
          <el-button @click="resetBlogForm">重置</el-button>
          <el-button type="primary" :loading="publishing" @click="publishBlog">发布</el-button>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="blogDetailVisible" width="720px" class="blog-detail-dialog" destroy-on-close>
      <template #header>
        <div class="dialog-header" v-if="activeBlog">
          <div>
            <h3>{{ activeBlog.title || '无标题笔记' }}</h3>
            <p>点赞 {{ activeBlog.liked || 0 }} ｜ 评论 {{ activeBlog.comments || 0 }} ｜ 店铺ID {{ activeBlog.shopId || '-' }}</p>
          </div>
        </div>
      </template>
      <div v-if="activeBlog" class="detail-dialog-body">
        <div class="detail-images" v-if="activeBlog.imageList?.length">
          <img v-for="image in activeBlog.imageList" :key="image" :src="image" :alt="activeBlog.title" />
        </div>
        <p class="detail-content">{{ activeBlog.content || '暂无详细内容' }}</p>
        <div class="detail-footer-actions">
          <el-button v-if="activeBlog.shopId" @click="goShop(activeBlog.shopId)">去店铺</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus';
import { ArrowRight } from '@element-plus/icons-vue';
import BottomNav from '@/components/layout/BottomNav.vue';
import { profileApi } from '@/api/modules/profile';
import { blogApi } from '@/api/modules/blog';
import { paymentApi } from '@/api/modules/payment';
import { shopApi } from '@/api/modules/shop';
import { useAiStore } from '@/store/ai';
import { useUserStore } from '@/store/user';

const router = useRouter();
const userStore = useUserStore();
const aiStore = useAiStore();

const profile = ref({ avatar: '', nickname: '' });
const myBlogs = ref([]);
const favoriteShops = ref([]);
const publishing = ref(false);
const blogDetailVisible = ref(false);
const activeBlog = ref(null);

const dialogs = reactive({
  favorites: false,
  orders: false,
  addresses: false,
  settings: false,
  support: false,
  publish: false,
  reviews: false
});

const addressForm = reactive({ contact: '', phone: '', detail: '' });
const blogForm = reactive({ shopId: '', title: '', images: '', content: '' });

const signedToday = computed(() => Number(userStore.signCount) > 0);
const stats = computed(() => [
  { key: 'favorites', title: '我的收藏', value: userStore.favoriteShopIds.length },
  { key: 'orders', title: '我的订单', value: userStore.orderHistory.length },
  { key: 'reviews', title: '我的评论', value: myBlogs.value.length }
]);

const menuEntries = [
  { key: 'addresses', title: '地址管理' },
  { key: 'settings', title: '设置' },
  { key: 'support', title: '联系客服' }
];

watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      loadProfile();
    }
  },
  { immediate: true }
);

async function loadProfile() {
  if (!userStore.isLoggedIn) return;
  const data = await profileApi.getProfile();
  profile.value = data.profile;
  myBlogs.value = (await blogApi.getMy(1).catch(() => [])).map(normalizeBlog);
}

function normalizeBlog(blog) {
  return {
    ...blog,
    imageList: String(blog?.images || '')
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
  };
}

async function signToday() {
  if (signedToday.value) {
    ElMessage.info('今日已经签到过了');
    return;
  }
  await userStore.signToday();
  ElMessage.success('签到成功');
}

async function openPanel(key) {
  if (key === 'favorites') {
    dialogs.favorites = true;
    await loadFavoriteShops();
    return;
  }
  if (key === 'orders') {
    dialogs.orders = true;
    return;
  }
  if (key === 'reviews') {
    dialogs.reviews = true;
    return;
  }
  if (key === 'publish') {
    dialogs.publish = true;
    return;
  }
  dialogs[key] = true;
}

async function loadFavoriteShops() {
  const results = await Promise.allSettled(userStore.favoriteShopIds.map((shopId) => shopApi.getDetail(shopId)));
  favoriteShops.value = results.filter((item) => item.status === 'fulfilled').map((item) => item.value);
}

function removeFavorite(shopId) {
  userStore.toggleFavorite(shopId);
  favoriteShops.value = favoriteShops.value.filter((item) => Number(item.id) !== Number(shopId));
  ElMessage.success('已取消收藏');
}

function removeOrder(orderId) {
  const targetId = String(orderId);
  userStore.orderHistory = userStore.orderHistory.filter((item) => String(item.orderId) !== targetId);
  userStore.persistOrders();
}

async function refreshOrderStatus(order) {
  try {
    const status = await paymentApi.status(order.orderId);
    userStore.updateOrder(order.orderId, { status: Number(status) });
    ElMessage.success(`订单状态：${formatOrderStatus(status)}`);
  } catch (error) {
    ElMessage.error(error?.message || '刷新订单状态失败');
  }
}

function saveAddress() {
  if (!addressForm.detail.trim()) {
    ElMessage.warning('请先输入详细地址');
    return;
  }
  userStore.addAddress({ city: userStore.city, contact: addressForm.contact, phone: addressForm.phone, detail: addressForm.detail });
  addressForm.contact = '';
  addressForm.phone = '';
  addressForm.detail = '';
  ElMessage.success('地址已保存');
}

function clearAiHistory() {
  aiStore.clearHistory();
  ElMessage.success('AI 记录已清空');
}

async function publishBlog() {
  if (!blogForm.title.trim() || !blogForm.content.trim()) {
    ElMessage.warning('请先填写标题和内容');
    return;
  }
  publishing.value = true;
  try {
    await blogApi.create({
      shopId: blogForm.shopId ? Number(blogForm.shopId) : null,
      title: blogForm.title,
      images: blogForm.images,
      content: blogForm.content
    });
    ElMessage.success('笔记发布成功');
    dialogs.publish = false;
    resetBlogForm();
    await loadProfile();
  } catch (error) {
    ElMessage.error(error?.message || '发布笔记失败');
  } finally {
    publishing.value = false;
  }
}

function resetBlogForm() {
  blogForm.shopId = '';
  blogForm.title = '';
  blogForm.images = '';
  blogForm.content = '';
}

function viewBlog(blog) {
  activeBlog.value = normalizeBlog(blog);
  blogDetailVisible.value = true;
}

function goShop(shopId) {
  dialogs.favorites = false;
  dialogs.orders = false;
  dialogs.reviews = false;
  blogDetailVisible.value = false;
  router.push(`/shop/${shopId}`);
}

function formatOrderStatus(status) {
  switch (Number(status)) {
    case 0: return '创建中';
    case 1: return '待支付';
    case 2: return '已支付';
    case 3: return '已核销';
    case 4: return '已取消';
    case 5: return '退款中';
    case 6: return '已退款';
    default: return '未知状态';
  }
}

function formatTime(value) {
  if (!value) return '刚刚';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}
</script>

<style scoped lang="scss">
.empty-login,
.profile-top { padding: 28px; margin: 18px 0 16px; }
.empty-login { display: grid; justify-items: start; gap: 12px; }
.profile-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: linear-gradient(135deg, #ff7d1a 0%, #ff6700 100%);
  color: #fff;
}
.user-base { display: flex; align-items: center; gap: 16px; }
.user-base h1 { margin: 0 0 8px; }
.user-base p { margin: 0; opacity: 0.9; }
.profile-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.stat-card {
  display: grid;
  gap: 8px;
  justify-items: center;
  padding: 22px 14px;
  border: none;
  background: #fff;
  cursor: pointer;
}
.stat-card strong { font-size: 28px; color: var(--dp-primary); }
.stat-card span { color: #666; }
.menu-panel,
.blog-panel { margin-top: 18px; padding: 18px; }
.menu-list { display: grid; }
.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 0;
  border: none;
  border-bottom: 1px solid #f0f0f0;
  background: transparent;
  cursor: pointer;
}
.my-blog-list,
.dialog-list { display: grid; gap: 12px; }
.my-blog-item,
.panel-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  padding: 14px;
  border-radius: 12px;
  background: #fffaf7;
}
.my-blog-item p,
.panel-item p { margin: 0; color: #666; line-height: 1.7; }
.blog-meta { display: flex; flex-wrap: wrap; gap: 12px; color: #999; font-size: 13px; }
.blog-actions,
.panel-actions,
.setting-actions,
.publish-actions,
.detail-footer-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.address-form,
.publish-form { display: grid; gap: 12px; }
.compact-top { margin-top: 16px; }
.support-box { display: grid; gap: 10px; color: #555; line-height: 1.7; }
.dialog-header h3 { margin: 0 0 8px; font-size: 22px; }
.dialog-header p { margin: 0; color: #999; }
.detail-dialog-body { display: grid; gap: 18px; }
.detail-images {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}
.detail-images img {
  width: 100%;
  height: 160px;
  object-fit: cover;
  border-radius: 12px;
  background: #f5f5f5;
}
.detail-content {
  margin: 0;
  font-size: 15px;
  line-height: 1.9;
  color: #444;
  white-space: pre-wrap;
}
.empty-block { color: #999; }
@media (max-width: 767px) {
  .stats-grid { grid-template-columns: 1fr; }
  .profile-top,
  .my-blog-item,
  .panel-item { flex-direction: column; grid-template-columns: 1fr; align-items: flex-start; }
  .profile-actions { width: 100%; }
}
</style>
