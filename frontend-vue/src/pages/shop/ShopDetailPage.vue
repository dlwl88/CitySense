<template>
  <div class="page-shell">
    <div class="page-container detail-page" v-if="detail">
      <div class="detail-top-bar">
        <el-button circle @click="router.back()">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <div class="right-actions">
          <el-button round @click="toggleFavorite">
            {{ userStore.isFavorite(detail.id) ? '已收藏' : '收藏' }}
          </el-button>
          <el-button type="primary" round @click="askAiForShop">AI推荐菜品</el-button>
        </div>
      </div>

      <section class="hero card-panel">
        <el-carousel height="300px">
          <el-carousel-item v-for="image in carouselImages" :key="image">
            <img class="hero-image" :src="image" :alt="detail.name" />
          </el-carousel-item>
        </el-carousel>

        <div class="hero-info">
          <div>
            <h1>{{ detail.name }}</h1>
            <div class="score-row">
              <el-rate :model-value="detail.score" disabled allow-half />
              <span>{{ detail.score }}</span>
              <span>人均 ¥{{ detail.price }}</span>
              <span>{{ detail.distance }}</span>
              <span class="tag-chip">{{ detail.status }}</span>
            </div>
          </div>

          <div class="meta-list">
            <div><strong>地址：</strong>{{ detail.address }}</div>
            <div><strong>电话：</strong>{{ detail.phone }}</div>
            <div><strong>营业时间：</strong>{{ detail.openHours }}</div>
            <div><strong>推荐：</strong>{{ detail.recommendText }}</div>
          </div>

          <div class="action-row">
            <el-button @click="toggleFavorite">收藏</el-button>
            <el-button @click="openNavigation">导航</el-button>
            <el-button type="primary" @click="askAiForShop">AI助手推荐</el-button>
          </div>
        </div>
      </section>

      <section class="content-grid">
        <article class="card-panel dishes-panel">
          <div class="section-title">
            <h2>招牌菜品</h2>
            <p>{{ dishTip }}</p>
          </div>
          <div class="dish-list">
            <div v-for="dish in detail.dishes" :key="dish.name" class="dish-item hover-lift">
              <img :src="dish.image" :alt="dish.name" />
              <div>
                <strong>{{ dish.name }}</strong>
                <p>{{ dish.desc }}</p>
                <span>¥{{ dish.price }}</span>
              </div>
            </div>
          </div>
        </article>

        <article class="card-panel offer-panel">
          <div class="section-title">
            <h2>优惠活动</h2>
            <p>普通券直购，秒杀券走异步秒杀与支付接口</p>
          </div>
          <div class="offer-list">
            <div v-for="voucher in vouchers" :key="voucher.id" class="offer-item hover-lift" :class="{ disabled: !canPurchaseVoucher(voucher) }">
              <div class="offer-main">
                <div class="offer-head">
                  <strong>{{ voucher.title }}</strong>
                  <div class="offer-badges">
                    <span class="voucher-badge" :class="isSeckillVoucher(voucher) ? 'is-seckill' : 'is-normal'">
                      {{ isSeckillVoucher(voucher) ? '秒杀券' : '普通券' }}
                    </span>
                    <span class="voucher-badge is-light" :class="voucherStatusClass(voucher)">
                      {{ getVoucherStatusText(voucher) }}
                    </span>
                  </div>
                </div>
                <span>支付 ¥{{ formatMoney(voucher.payValue) }} / 抵扣 ¥{{ formatMoney(voucher.actualValue) }}</span>
                <span>{{ formatVoucherMeta(voucher) }}</span>
              </div>
              <div class="offer-actions">
                <el-button
                  size="small"
                  type="primary"
                  :loading="ordering && activeVoucherId === voucher.id"
                  :disabled="ordering || paying || checkingOrder || !canPurchaseVoucher(voucher)"
                  @click="submitVoucherOrder(voucher)"
                >
                  {{ getVoucherActionText(voucher) }}
                </el-button>
              </div>
            </div>
            <div v-if="!vouchers.length" class="empty-block">暂无可领取优惠券</div>
          </div>

          <div v-if="latestOrderId" class="payment-box">
            <strong>当前订单：{{ latestOrderId }}</strong>
            <span class="order-status-text">{{ orderStatusText }}</span>
            <div class="payment-actions">
              <el-button size="small" :disabled="checkingOrder || paying || ordering" :loading="checkingOrder" @click="checkPaymentStatus">查状态</el-button>
              <el-button size="small" :disabled="!canPayOrder" :loading="paying" @click="simulatePayment">模拟支付</el-button>
              <el-button size="small" :disabled="!canPayOrder" :loading="paying" @click="openPayUrl('alipay')">支付宝</el-button>
              <el-button size="small" :disabled="!canPayOrder" :loading="paying" @click="openPayUrl('wechat')">微信</el-button>
            </div>
          </div>
        </article>
      </section>

      <section class="card-panel comments-panel">
        <div class="section-title">
          <h2>用户评论</h2>
          <p>{{ commentTip }}</p>
        </div>
        <CommentItem v-for="comment in detail.comments" :key="comment.id" :comment="comment" />
      </section>
    </div>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus';
import { ArrowLeft } from '@element-plus/icons-vue';
import CommentItem from '@/components/common/CommentItem.vue';
import { shopApi } from '@/api/modules/shop';
import { voucherApi } from '@/api/modules/voucher';
import { paymentApi } from '@/api/modules/payment';
import { blogApi } from '@/api/modules/blog';
import { aiApi } from '@/api/modules/ai';
import { useAiStore } from '@/store/ai';
import { useUserStore } from '@/store/user';

const route = useRoute();
const router = useRouter();
const aiStore = useAiStore();
const userStore = useUserStore();
const detail = ref(null);
const vouchers = ref([]);
const latestOrderId = ref(null);
const latestOrderMeta = ref(null);
const usedRealComments = ref(false);
const usedAiInsight = ref(false);
const orderReady = ref(false);
const orderStatusUnknown = ref(false);
const ordering = ref(false);
const paying = ref(false);
const checkingOrder = ref(false);
const activeVoucherId = ref(null);
const canPayOrder = computed(() => orderReady.value && !orderStatusUnknown.value
  && Number(latestOrderMeta.value?.status) === 1 && !paying.value && !checkingOrder.value && !ordering.value);
const orderStatusText = computed(() => {
  if (orderStatusUnknown.value) return '订单状态暂未确认，请点击查状态';
  if (!orderReady.value) return '订单创建中，请稍候后再查询';
  const status = Number(latestOrderMeta.value?.status);
  return status === 1 ? '待支付，可继续支付' : `当前状态：${formatOrderStatus(status)}`;
});

const carouselImages = computed(() => {
  if (!detail.value) return [];
  return [detail.value.cover, ...detail.value.comments.flatMap((comment) => comment.images || [])]
    .filter(Boolean)
    .slice(0, 3);
});

const dishTip = computed(() => (usedAiInsight.value ? '优先展示 AI 店铺分析得到的招牌菜结果' : '当前后端暂无菜品接口，这里展示本地演示数据'));
const commentTip = computed(() => (usedRealComments.value ? '优先展示真实探店笔记内容作为门店评论' : '当前后端评论接口未实现，这里展示 mock 评论'));

onMounted(loadDetail);

async function loadDetail() {
  try {
    const shopDetail = await shopApi.getDetail(route.params.id);
    const [voucherList, insight, shopBlogs] = await Promise.all([
      voucherApi.listByShop(route.params.id).catch(() => []),
      aiApi.analyzeShop(route.params.id).catch(() => null),
      loadShopBlogs(route.params.id)
    ]);

    detail.value = mergeDetail(shopDetail, insight, shopBlogs);
    vouchers.value = voucherList;
    aiStore.setCurrentShop(detail.value);
    const latestOrder = findLatestOrder(detail.value.id);
    latestOrderId.value = latestOrder?.orderId || null;
    latestOrderMeta.value = latestOrder || null;
    if (latestOrderId.value) {
      orderReady.value = false;
      await ensureOrderReady(latestOrderId.value, false).catch(() => {
        ElMessage.warning('订单状态暂未确认，请点击查状态');
      });
    }
  } catch (error) {
    ElMessage.error(error?.message || '加载商家详情失败');
  }
}

async function loadShopBlogs(shopId) {
  const results = [];
  for (let page = 1; page <= 3 && results.length < 3; page += 1) {
    const list = await blogApi.getHot(page).catch(() => []);
    const matched = (list || []).filter((item) => Number(item.shopId) === Number(shopId));
    results.push(...matched);
  }
  const uniqueMap = new Map();
  results.forEach((item) => uniqueMap.set(item.id, item));
  return [...uniqueMap.values()].slice(0, 3);
}

function mergeDetail(baseDetail, insight, shopBlogs) {
  const nextDetail = {
    ...baseDetail,
    dishes: baseDetail.dishes,
    comments: baseDetail.comments,
    recommendText: baseDetail.recommendText
  };

  if (Array.isArray(shopBlogs) && shopBlogs.length) {
    nextDetail.comments = shopBlogs.map((blog) => ({
      id: blog.id,
      user: blog.name || '匿名用户',
      avatar: blog.icon || 'https://dummyimage.com/80x80/ffb36d/ffffff&text=U',
      score: calculateBlogScore(blog),
      content: blog.content || blog.title || '这位用户没有留下更多文字评价。',
      images: parseImages(blog.images)
    }));
    usedRealComments.value = true;
  } else {
    usedRealComments.value = false;
  }

  if (insight?.signatureDishes?.length) {
    nextDetail.dishes = insight.signatureDishes.map((name, index) => ({
      name,
      price: baseDetail.price,
      desc: index === 0 ? insight.summary || 'AI 认为这是当前门店最值得尝试的菜品。' : `AI 推荐指数 Top ${index + 1}`,
      image: baseDetail.cover
    }));
    nextDetail.recommendText = insight.summary || baseDetail.recommendText;
    usedAiInsight.value = true;
  } else {
    usedAiInsight.value = false;
  }

  return nextDetail;
}

function parseImages(images) {
  return String(images || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 3);
}

function calculateBlogScore(blog) {
  const liked = Number(blog?.liked || 0);
  if (liked >= 100) return 5;
  if (liked >= 50) return 4.8;
  if (liked >= 20) return 4.6;
  if (liked >= 5) return 4.3;
  return 4;
}

function findLatestOrder(shopId) {
  const orders = userStore.orderHistory.filter((item) => Number(item.shopId) === Number(shopId));
  return orders[0] || null;
}

function toggleFavorite() {
  const isFavorite = userStore.toggleFavorite(detail.value.id);
  ElMessage.success(isFavorite ? '已加入收藏' : '已取消收藏');
}

function openNavigation() {
  const keyword = encodeURIComponent(`${detail.value.name} ${detail.value.address}`);
  window.open(`https://uri.amap.com/search?keyword=${keyword}`, '_blank');
}

async function askAiForShop() {
  aiStore.setCurrentShop(detail.value);
  aiStore.openDrawer();
  await aiStore.sendShopInsight(detail.value);
}

function isSeckillVoucher(voucher) {
  return Number(voucher?.type) === 1;
}

function toTime(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.getTime();
}

function getVoucherStatus(voucher) {
  if (voucher?.status != null && Number(voucher.status) !== 1) {
    return 'offline';
  }
  if (isSeckillVoucher(voucher)) {
    const now = Date.now();
    const beginTime = toTime(voucher.beginTime);
    const endTime = toTime(voucher.endTime);
    if (beginTime && now < beginTime) {
      return 'upcoming';
    }
    if (endTime && now > endTime) {
      return 'expired';
    }
    if (Number(voucher.stock ?? 0) <= 0) {
      return 'soldout';
    }
  }
  return 'available';
}

function getVoucherStatusText(voucher) {
  switch (getVoucherStatus(voucher)) {
    case 'offline': return '已下架';
    case 'upcoming': return '未开始';
    case 'expired': return '已结束';
    case 'soldout': return '已抢光';
    default: return '可购买';
  }
}

function voucherStatusClass(voucher) {
  return `is-${getVoucherStatus(voucher)}`;
}

function canPurchaseVoucher(voucher) {
  return getVoucherStatus(voucher) === 'available';
}

function getVoucherActionText(voucher) {
  if (!canPurchaseVoucher(voucher)) {
    return getVoucherStatusText(voucher);
  }
  return isSeckillVoucher(voucher) ? '抢券' : '立即购买';
}

function formatVoucherMeta(voucher) {
  if (isSeckillVoucher(voucher)) {
    return `秒杀券 ｜ 库存 ${voucher.stock ?? '不限'} ｜ ${voucher.beginTime || '即时生效'}`;
  }
  return '普通券 ｜ 即时购买 ｜ 下单后可直接支付';
}

async function submitVoucherOrder(voucher) {
  if (ordering.value || paying.value || checkingOrder.value) return;
  if (!canPurchaseVoucher(voucher)) {
    ElMessage.warning(`${getVoucherStatusText(voucher)}，暂不可下单`);
    return;
  }
  activeVoucherId.value = voucher.id;
  try {
    if (isSeckillVoucher(voucher)) {
      await seckillVoucher(voucher);
      return;
    }
    await buyVoucher(voucher);
  } finally {
    activeVoucherId.value = null;
  }
}

async function buyVoucher(voucher) {
  if (!userStore.isLoggedIn) {
    userStore.openLoginDialog();
    return;
  }
  ordering.value = true;
  try {
    latestOrderId.value = String(await voucherApi.buy(voucher.id));
    latestOrderMeta.value = {
      orderId: latestOrderId.value,
      shopId: detail.value.id,
      voucherId: voucher.id,
      voucherType: voucher.type,
      asyncCreated: false,
      status: 1
    };
    orderReady.value = true;
    orderStatusUnknown.value = false;
    userStore.recordOrder({
      orderId: latestOrderId.value,
      shopId: detail.value.id,
      shopName: detail.value.name,
      voucherId: voucher.id,
      voucherTitle: voucher.title,
      voucherType: voucher.type,
      asyncCreated: false,
      payAmount: formatMoney(voucher.payValue),
      actualAmount: formatMoney(voucher.actualValue),
      status: 1
    });
    ElMessage.success(`普通券下单成功，订单ID：${latestOrderId.value}，可以直接支付`);
  } catch (error) {
    ElMessage.error(error?.message || '普通券购买失败');
  } finally {
    ordering.value = false;
  }
}

async function seckillVoucher(voucher) {
  if (!userStore.isLoggedIn) {
    userStore.openLoginDialog();
    return;
  }
  ordering.value = true;
  try {
    latestOrderId.value = String(await voucherApi.seckill(voucher.id));
    latestOrderMeta.value = {
      orderId: latestOrderId.value,
      shopId: detail.value.id,
      voucherId: voucher.id,
      voucherType: voucher.type,
      asyncCreated: true,
      status: 0
    };
    orderReady.value = false;
    orderStatusUnknown.value = false;
    userStore.recordOrder({
      orderId: latestOrderId.value,
      shopId: detail.value.id,
      shopName: detail.value.name,
      voucherId: voucher.id,
      voucherTitle: voucher.title,
      voucherType: voucher.type,
      asyncCreated: true,
      payAmount: formatMoney(voucher.payValue),
      actualAmount: formatMoney(voucher.actualValue),
      status: 0
    });
    ElMessage.success(`抢券成功，订单ID：${latestOrderId.value}，正在等待订单落库`);
    await ensureOrderReady(latestOrderId.value, true);
  } catch (error) {
    ElMessage.error(error?.message || '抢券失败');
  } finally {
    ordering.value = false;
  }
}

async function ensureOrderReady(orderId, notifyWhenReady = false) {
  checkingOrder.value = true;
  try {
    const targetOrderId = String(orderId);
    for (let attempt = 0; attempt < 10; attempt += 1) {
      try {
        const status = await refreshOrderStatus(targetOrderId);
        if (Number(status) === 0) {
          await wait(800);
          continue;
        }
        if (notifyWhenReady) {
          ElMessage.info(`当前订单状态：${formatOrderStatus(status)}`);
        }
        return true;
      } catch (error) {
        if (!String(error?.message || '').includes('订单不存在') && !String(error?.message || '').includes('订单创建中')) {
          throw error;
        }
        await wait(800);
      }
    }
    ElMessage.warning('订单正在排队创建，请稍后再点击支付');
    return false;
  } finally {
    checkingOrder.value = false;
  }
}

async function checkPaymentStatus() {
  if (!latestOrderId.value || checkingOrder.value || paying.value || ordering.value) return;
  try {
    if (!await ensureOrderReady(latestOrderId.value, false)) return;
    ElMessage.info(`当前订单状态：${formatOrderStatus(latestOrderMeta.value?.status)}`);
  } catch (error) {
    ElMessage.error(error?.message || '查询状态失败');
  }
}

async function simulatePayment() {
  if (!latestOrderId.value || paying.value || checkingOrder.value || ordering.value) return;
  const orderId = String(latestOrderId.value);
  paying.value = true;
  try {
    if (!await ensurePayableOrder(orderId)) return;
    const result = await paymentApi.simulate(orderId);
    try {
      await refreshOrderStatus(orderId, { payType: 1 });
    } catch {
      ElMessage.warning('支付请求已成功，订单状态暂未确认，请点击查状态');
      return;
    }
    if (Number(latestOrderMeta.value?.status) === 2) {
      ElMessage.success(typeof result === 'string' ? result : '模拟支付成功');
    } else {
      ElMessage.info(`当前订单状态：${formatOrderStatus(latestOrderMeta.value?.status)}`);
    }
  } catch (error) {
    await handlePaymentFailure(error, orderId);
  } finally {
    paying.value = false;
  }
}

async function openPayUrl(type) {
  if (!latestOrderId.value || paying.value || checkingOrder.value || ordering.value) return;
  const orderId = String(latestOrderId.value);
  paying.value = true;
  try {
    if (!await ensurePayableOrder(orderId)) return;
    const result = type === 'alipay'
      ? await paymentApi.alipay(orderId)
      : await paymentApi.wechat(orderId);
    try {
      const status = await refreshOrderStatus(orderId, { payType: type === 'alipay' ? 2 : 3 });
      if (status !== 1) {
        ElMessage.warning(`订单${formatOrderStatus(status)}，不能继续支付`);
        return;
      }
    } catch {
      ElMessage.warning('支付单已创建，订单状态暂未确认，请点击查状态');
      return;
    }
    ElMessage.success(result?.payUrl ? `支付链接：${result.payUrl}` : '支付链接已生成');
  } catch (error) {
    await handlePaymentFailure(error, orderId);
  } finally {
    paying.value = false;
  }
}

async function refreshOrderStatus(orderId, patch = {}) {
  try {
    const response = await paymentApi.status(orderId);
    const status = Number(response);
    if (response == null || !Number.isInteger(status) || status < 0 || status > 6) {
      throw new Error('订单状态返回异常，请稍后查询');
    }
    userStore.updateOrder(orderId, { ...patch, status });
    if (String(latestOrderId.value) === String(orderId)) {
      latestOrderMeta.value = { ...latestOrderMeta.value, ...patch, status };
      orderReady.value = status !== 0;
      orderStatusUnknown.value = false;
    }
    return status;
  } catch (error) {
    if (String(latestOrderId.value) === String(orderId)) orderStatusUnknown.value = true;
    throw error;
  }
}

async function ensurePayableOrder(orderId) {
  if (!await ensureOrderReady(orderId, false)) return false;
  const status = Number(latestOrderMeta.value?.status);
  if (status !== 1) {
    ElMessage.warning(`订单${formatOrderStatus(status)}，不能继续支付`);
    return false;
  }
  return true;
}

async function handlePaymentFailure(error, orderId) {
  const status = await refreshOrderStatus(orderId).catch(() => null);
  if (status === 2 || status === 3) {
    ElMessage.info(`订单${formatOrderStatus(status)}，无需重复支付`);
  } else if (status === 4 || status === 5 || status === 6) {
    ElMessage.warning(`订单${formatOrderStatus(status)}，不能继续支付`);
  } else if (status == null) {
    ElMessage.warning(`${error?.message || '支付处理失败'}；订单状态暂未确认，请点击查状态`);
  } else {
    ElMessage.warning(error?.message || '支付处理失败，请查询订单状态后重试');
  }
}

function formatMoney(value) {
  const amount = Number(value || 0);
  return (amount / 100).toFixed(2);
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

function wait(duration) {
  return new Promise((resolve) => setTimeout(resolve, duration));
}
</script>

<style scoped lang="scss">
.detail-page { padding-top: 18px; }
.detail-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.right-actions { display: flex; gap: 10px; }
.hero {
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 18px;
  padding: 18px;
}
.hero-image {
  width: 100%;
  height: 300px;
  object-fit: cover;
  border-radius: 14px;
  background: #fff4eb;
}
.hero-info {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 14px;
}
.hero-info h1 { margin: 0; font-size: 30px; }
.score-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
  color: #666;
}
.meta-list {
  display: grid;
  gap: 12px;
  line-height: 1.8;
  color: #555;
}
.action-row { display: flex; flex-wrap: wrap; gap: 10px; }
.content-grid {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 16px;
  margin-top: 18px;
}
.dishes-panel,
.offer-panel,
.comments-panel { padding: 18px; }
.dish-list { display: grid; gap: 14px; }
.dish-item {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 14px;
  padding: 12px;
  border-radius: 12px;
  background: #fffaf7;
}
.dish-item img {
  width: 100%;
  height: 88px;
  object-fit: cover;
  border-radius: 10px;
}
.dish-item p { margin: 8px 0; color: #666; }
.dish-item span { color: var(--dp-primary); font-weight: 700; }
.offer-list { display: grid; gap: 12px; }
.offer-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  padding: 14px;
  border-radius: 12px;
  color: #8a4a14;
  background: #fff5ec;
}
.offer-main { display: grid; gap: 6px; }
.offer-main span { font-size: 13px; color: #8a4a14; }
.offer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}
.offer-badges {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.voucher-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.voucher-badge.is-seckill {
  color: #fff;
  background: linear-gradient(135deg, #ff6700, #ff8a3d);
}
.voucher-badge.is-normal {
  color: #ff6700;
  background: #fff1e7;
}
.voucher-badge.is-light {
  color: #8a4a14;
  background: #fde6d5;
}
.voucher-badge.is-available {
  color: #1f8f55;
  background: #e9f8ef;
}
.voucher-badge.is-upcoming {
  color: #9a6b00;
  background: #fff4d9;
}
.voucher-badge.is-expired,
.voucher-badge.is-offline,
.voucher-badge.is-soldout {
  color: #999;
  background: #f1f1f1;
}
.offer-item.disabled {
  opacity: 0.82;
  background: #faf7f4;
}
.offer-actions,
.payment-actions { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.payment-box {
  display: grid;
  gap: 12px;
  margin-top: 14px;
  padding: 14px;
  border-radius: 12px;
  background: #f8f8f8;
}
.order-status-text { color: #999; font-size: 13px; }
.empty-block { color: #999; }
.comments-panel { margin-top: 18px; }
@media (max-width: 767px) {
  .detail-top-bar,
  .hero,
  .content-grid,
  .offer-item { grid-template-columns: 1fr; flex-direction: column; }
  .detail-top-bar { align-items: flex-start; gap: 10px; }
  .right-actions { width: 100%; flex-wrap: wrap; }
  .hero-info h1 { font-size: 24px; }
  .dish-item { grid-template-columns: 1fr; }
}
</style>
