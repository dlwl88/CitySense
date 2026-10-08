import request from '@/api/request';
import { cityOptions } from '@/mock/data';
import { normalizeCategories, normalizeShop } from '@/utils/shop';

const PAGE_SIZE = 10;

export const homeApi = {
  async getCategories() {
    const list = await request({
      url: '/shop-type/list',
      method: 'get'
    });
    return normalizeCategories(list || []);
  },

  async getHomeData(params = {}) {
    const categories = await this.getCategories();
    const currentPage = Number(params.page || 1);
    const keyword = (params.keyword || '').trim();
    const currentTypeId = params.typeId || categories[0]?.id;

    let shopList = [];
    if (keyword) {
      shopList = await request({
        url: '/shop/of/name',
        method: 'get',
        params: {
          name: keyword,
          current: currentPage
        }
      });
    } else if (currentTypeId) {
      shopList = await request({
        url: '/shop/of/type',
        method: 'get',
        params: {
          typeId: currentTypeId,
          current: currentPage
        }
      });
    }

    const shops = (shopList || []).map((item) => normalizeShop(item));
    return {
      city: params.city || '北京',
      cityOptions,
      categories,
      currentTypeId,
      banners: shops.slice(0, 3),
      shops,
      hasMore: shops.length >= PAGE_SIZE
    };
  },

  async searchShops(params = {}) {
    const currentPage = Number(params.page || 1);
    let list = [];

    if (params.category) {
      list = await request({
        url: '/shop/of/type',
        method: 'get',
        params: {
          typeId: params.category,
          current: currentPage
        }
      });
    } else {
      list = await request({
        url: '/shop/of/name',
        method: 'get',
        params: {
          name: params.keyword || '',
          current: currentPage
        }
      });
    }

    return {
      list: (list || []).map((item) => normalizeShop(item)),
      hasMore: (list || []).length >= PAGE_SIZE
    };
  }
};
