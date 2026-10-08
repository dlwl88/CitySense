import request from '@/api/request';

export const userApi = {
  sendCode(phone) {
    return request({
      url: '/user/code',
      method: 'post',
      params: { phone }
    });
  },
  login(data) {
    return request({
      url: '/user/login',
      method: 'post',
      data
    });
  },
  getMe() {
    return request({
      url: '/user/me',
      method: 'get'
    });
  },
  sign() {
    return request({
      url: '/user/sign',
      method: 'post'
    });
  },
  getSignCount() {
    return request({
      url: '/user/sign/count',
      method: 'get'
    });
  }
};
