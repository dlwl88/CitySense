import axios from 'axios';

/**
 * Axios 实例：
 * - 开发时通过 Vite 代理把 /api 转发到 Spring Boot 8083
 * - 生产构建后由 Spring Boot 同源托管，直接请求真实后端接口
 * - 统一解包后端 Result 返回结构
 */
const service = axios.create({
  baseURL: import.meta.env.DEV ? '/api' : '',
  timeout: 8000
});

service.interceptors.request.use((config) => {
  const token = localStorage.getItem('hm-dianping-token');
  config.headers = config.headers || {};
  if (token) {
    config.headers.authorization = token;
  }
  return config;
});

service.interceptors.response.use(
  (response) => {
    const payload = response.data;
    if (payload && typeof payload.success === 'boolean') {
      if (!payload.success) {
        return Promise.reject(new Error(payload.errorMsg || '请求失败'));
      }
      return payload.data;
    }
    return payload;
  },
  (error) => {
    if (error?.response?.status === 401) {
      localStorage.removeItem('hm-dianping-token');
      window.dispatchEvent(new CustomEvent('hm-auth-expired'));
    }
    return Promise.reject(error);
  }
);

export default service;
