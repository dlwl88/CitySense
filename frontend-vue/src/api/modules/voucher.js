import request from '@/api/request';

export const voucherApi = {
  listByShop(shopId) {
    return request({
      url: `/voucher/list/${shopId}`,
      method: 'get'
    });
  },
  buy(voucherId) {
    return request({
      url: `/voucher-order/buy/${voucherId}`,
      method: 'post'
    });
  },
  seckill(voucherId) {
    return request({
      url: `/voucher-order/seckill/${voucherId}`,
      method: 'post'
    });
  }
};