import request from '@/api/request';

export const blogApi = {
  getHot(current = 1) {
    return request({
      url: '/blog/hot',
      method: 'get',
      params: { current }
    });
  },
  getMy(current = 1) {
    return request({
      url: '/blog/of/me',
      method: 'get',
      params: { current }
    });
  },
  create(data) {
    return request({
      url: '/blog',
      method: 'post',
      data
    });
  },
  toggleLike(id) {
    return request({
      url: `/blog/like/${id}`,
      method: 'put'
    });
  },
  getLikes(id) {
    return request({
      url: `/blog/likes/${id}`,
      method: 'get'
    });
  },
  getDetail(id) {
    return request({
      url: `/blog/${id}`,
      method: 'get'
    });
  }
};