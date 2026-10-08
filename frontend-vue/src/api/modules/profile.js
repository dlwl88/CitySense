import { userApi } from '@/api/modules/user';

export const profileApi = {
  async getProfile() {
    const [me, signCount] = await Promise.all([
      userApi.getMe(),
      userApi.getSignCount().catch(() => 0)
    ]);

    return {
      profile: {
        id: me?.id,
        avatar: me?.icon || 'https://dummyimage.com/120x120/ff6700/ffffff&text=U',
        nickname: me?.nickName || '未登录用户'
      },
      menus: [
        { title: '我的收藏', value: 0 },
        { title: '连续签到', value: signCount || 0 },
        { title: '我的评论', value: 0 }
      ],
      entries: ['地址管理', '设置', '联系客服']
    };
  }
};
