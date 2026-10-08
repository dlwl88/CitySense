import request from '@/api/request';

export const paymentApi = {
  simulate(orderId) {
    return request({
      url: `/payment/simulate/${orderId}`,
      method: 'post'
    });
  },
  status(orderId) {
    return request({
      url: `/payment/status/${orderId}`,
      method: 'get'
    });
  },
  alipay(orderId) {
    return request({
      url: `/payment/alipay/${orderId}`,
      method: 'post'
    });
  },
  wechat(orderId) {
    return request({
      url: `/payment/wechat/${orderId}`,
      method: 'post'
    });
  }
};
