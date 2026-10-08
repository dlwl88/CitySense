import { defineStore } from 'pinia';
import { userApi } from '@/api/modules/user';

const DEFAULT_AVATAR = 'https://dummyimage.com/120x120/ff6700/ffffff&text=U';
const TOKEN_KEY = 'hm-dianping-token';
const FAVORITES_KEY = 'hm-dianping-favorites';
const ORDERS_KEY = 'hm-dianping-orders';
const ADDRESSES_KEY = 'hm-dianping-addresses';

function readJson(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function writeJson(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

function sortOrders(list) {
  return [...list].sort((left, right) => new Date(right.updateTime || right.createdTime || 0) - new Date(left.updateTime || left.createdTime || 0));
}

function resolveScopedKey(baseKey, userId) {
  return `${baseKey}:${userId || 'guest'}`;
}

/**
 * 用户状态：
 * 1. 保存 token / 当前用户信息
 * 2. 维护登录弹窗开关
 * 3. 保存本地收藏、订单、地址等界面状态
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    city: '北京',
    token: localStorage.getItem(TOKEN_KEY) || '',
    profile: null,
    signCount: 0,
    loginDialogVisible: false,
    favoriteShopIds: [],
    orderHistory: [],
    addressList: []
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token && state.profile?.id),
    displayAvatar: (state) => state.profile?.avatar || DEFAULT_AVATAR,
    displayName: (state) => state.profile?.nickname || '点击登录',
    isFavorite: (state) => (shopId) => state.favoriteShopIds.includes(Number(shopId))
  },
  actions: {
    async initAuth() {
      if (!this.token) {
        this.resetLocalUserState();
        return;
      }
      try {
        await this.fetchMe();
        this.loadUserScopedState();
        await this.fetchSignCount();
      } catch {
        this.clearAuth();
      }
    },
    setCity(city) {
      this.city = city;
    },
    openLoginDialog() {
      this.loginDialogVisible = true;
    },
    closeLoginDialog() {
      this.loginDialogVisible = false;
    },
    async sendCode(phone) {
      await userApi.sendCode(phone);
    },
    async login(form) {
      const token = await userApi.login(form);
      this.token = token;
      localStorage.setItem(TOKEN_KEY, token);
      this.resetLocalUserState();
      await this.fetchMe();
      this.loadUserScopedState();
      await this.fetchSignCount();
      this.loginDialogVisible = false;
    },
    async fetchMe() {
      const me = await userApi.getMe();
      this.profile = me
        ? {
            id: me.id,
            nickname: me.nickName,
            avatar: me.icon || DEFAULT_AVATAR
          }
        : null;
      if (!this.profile) {
        this.resetLocalUserState();
      }
      return this.profile;
    },
    async fetchSignCount() {
      this.signCount = (await userApi.getSignCount().catch(() => 0)) || 0;
      return this.signCount;
    },
    async signToday() {
      await userApi.sign();
      await this.fetchSignCount();
    },
    getScopedStorageKey(baseKey) {
      return resolveScopedKey(baseKey, this.profile?.id);
    },
    loadUserScopedState() {
      this.favoriteShopIds = readJson(this.getScopedStorageKey(FAVORITES_KEY), []);
      this.orderHistory = sortOrders(readJson(this.getScopedStorageKey(ORDERS_KEY), []));
      this.addressList = readJson(this.getScopedStorageKey(ADDRESSES_KEY), []);
    },
    resetLocalUserState() {
      this.favoriteShopIds = [];
      this.orderHistory = [];
      this.addressList = [];
    },
    persistFavorites() {
      writeJson(this.getScopedStorageKey(FAVORITES_KEY), this.favoriteShopIds);
    },
    persistOrders() {
      this.orderHistory = sortOrders(this.orderHistory);
      writeJson(this.getScopedStorageKey(ORDERS_KEY), this.orderHistory);
    },
    persistAddresses() {
      writeJson(this.getScopedStorageKey(ADDRESSES_KEY), this.addressList);
    },
    clearAuth() {
      this.token = '';
      this.profile = null;
      this.signCount = 0;
      this.resetLocalUserState();
      localStorage.removeItem(TOKEN_KEY);
    },
    logout() {
      this.clearAuth();
    },
    toggleFavorite(shopId) {
      const targetId = Number(shopId);
      if (this.favoriteShopIds.includes(targetId)) {
        this.favoriteShopIds = this.favoriteShopIds.filter((id) => id !== targetId);
        this.persistFavorites();
        return false;
      }
      this.favoriteShopIds.push(targetId);
      this.persistFavorites();
      return true;
    },
    clearFavorites() {
      this.favoriteShopIds = [];
      this.persistFavorites();
    },
    recordOrder(order) {
      const orderId = String(order.orderId);
      const nextOrder = {
        createdTime: new Date().toISOString(),
        updateTime: new Date().toISOString(),
        ...order,
        orderId,
        status: Number(order.status ?? 1)
      };
      const currentIndex = this.orderHistory.findIndex((item) => String(item.orderId) === orderId);
      if (currentIndex >= 0) {
        this.orderHistory.splice(currentIndex, 1, {
          ...this.orderHistory[currentIndex],
          ...nextOrder
        });
      } else {
        this.orderHistory.unshift(nextOrder);
      }
      this.persistOrders();
    },
    updateOrder(orderId, patch = {}) {
      const targetId = String(orderId);
      this.orderHistory = this.orderHistory.map((item) => {
        if (String(item.orderId) !== targetId) {
          return item;
        }
        return {
          ...item,
          ...patch,
          updateTime: new Date().toISOString()
        };
      });
      this.persistOrders();
    },
    clearOrders() {
      this.orderHistory = [];
      this.persistOrders();
    },
    addAddress(address) {
      this.addressList.unshift({
        id: Date.now(),
        city: address.city || this.city,
        contact: address.contact || '默认联系人',
        phone: address.phone || '',
        detail: address.detail || '',
        createdTime: new Date().toISOString()
      });
      this.persistAddresses();
    },
    removeAddress(addressId) {
      this.addressList = this.addressList.filter((item) => Number(item.id) !== Number(addressId));
      this.persistAddresses();
    }
  }
});
