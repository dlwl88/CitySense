import request from '@/api/request';
import { mergeShopDetail } from '@/utils/shop';

export const shopApi = {
  async getDetail(id) {
    const detail = await request({
      url: `/shop/${id}`,
      method: 'get'
    });
    return mergeShopDetail(detail);
  }
};
