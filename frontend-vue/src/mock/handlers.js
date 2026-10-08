import { categoryList, cityOptions, profileData, quickScenes, shops } from '@/mock/data';

const sleep = (duration = 300) => new Promise((resolve) => setTimeout(resolve, duration));

/**
 * mock 请求分发器：
 * 按 URL 和 method 返回示例数据，便于前端独立运行。
 */
export async function handleMockRequest(config) {
  await sleep(250);

  const method = (config.method || 'get').toLowerCase();
  const url = config.url || '';

  if (method === 'get' && url === '/home') {
    const page = Number(config.params?.page || 1);
    const keyword = (config.params?.keyword || '').trim();
    const city = config.params?.city || '北京';
    const pageSize = 3;
    const filtered = shops.filter((shop) => {
      if (!keyword) {
        return true;
      }
      return [shop.name, shop.tags.join(' '), shop.recommendText].join(' ').includes(keyword);
    });
    const list = filtered.slice(0, page * pageSize);
    return success({
      city,
      banners: shops.slice(0, 3),
      categories: categoryList,
      shops: list,
      hasMore: list.length < filtered.length,
      cityOptions
    });
  }

  if (method === 'get' && url === '/categories') {
    return success({ list: categoryList });
  }

  if (method === 'get' && url === '/shops') {
    const category = config.params?.category;
    const keyword = (config.params?.keyword || '').trim();
    const list = shops.filter((shop) => {
      const categoryMatch = !category || shop.category === category;
      const keywordMatch = !keyword || [shop.name, shop.tags.join(' '), shop.recommendText].join(' ').includes(keyword);
      return categoryMatch && keywordMatch;
    });
    return success({ list });
  }

  if (method === 'get' && /^\/shops\/\d+$/.test(url)) {
    const shopId = Number(url.split('/').pop());
    const shop = shops.find((item) => item.id === shopId);
    return success({ detail: shop || null });
  }

  if (method === 'post' && /^\/shops\/\d+\/favorite$/.test(url)) {
    return success({ message: '收藏状态已切换' });
  }

  if (method === 'get' && url === '/profile') {
    return success({
      profile: {
        avatar: 'https://dummyimage.com/120x120/ff6700/ffffff&text=AI',
        nickname: '点评小达人'
      },
      ...profileData
    });
  }

  if (method === 'post' && url === '/ai/chat') {
    const payload = typeof config.data === 'string' ? JSON.parse(config.data) : config.data || {};
    return success(buildAiResponse(payload.message, payload.currentShop));
  }

  return success({
    message: 'mock 请求已接收',
    quickScenes
  });
}

function success(data) {
  return {
    status: 200,
    data
  };
}

/**
 * 根据不同问题意图返回模拟 AI 结果。
 * 这里不调用真实模型，而是做规则匹配，方便本地演示。
 */
export function buildAiResponse(message = '', currentShop) {
  const text = message.trim();

  if (text.includes('北京朝阳区') && text.includes('火锅')) {
    return {
      content: '我帮你挑了 3 家北京朝阳区口碑不错的火锅店，可以优先看看下面这些：',
      cards: shops
        .filter((shop) => shop.tags.some((tag) => tag.includes('朝阳区')) && shop.name.includes('锅'))
        .slice(0, 3)
        .map((shop) => ({
          name: shop.name,
          score: shop.score,
          price: shop.price,
          reason: shop.recommendText
        }))
    };
  }

  if (text.includes('这家店什么菜好吃') || text.includes('推荐商家菜品')) {
    if (currentShop?.dishes?.length) {
      return {
        content: `结合当前商家「${currentShop.name}」的人气和搭配，我推荐你先点这 3 道：`,
        cards: currentShop.dishes.slice(0, 3).map((dish) => ({
          name: dish.name,
          score: '招牌推荐',
          price: dish.price,
          reason: dish.desc
        }))
      };
    }
    return {
      content: '你还没有打开具体商家页。先进入一家店，我就能按当前店铺给你推荐菜品。',
      cards: []
    };
  }

  if (text.includes('蘸料') || text.includes('火锅')) {
    return {
      content:
        '火锅蘸料可以试试这个万能版本：2 勺麻酱 + 1 勺腐乳汁 + 少量蒜末 + 香菜 + 葱花 + 一点点糖和香油。如果你吃辣，再补半勺辣椒油，整体会更香。',
      cards: []
    };
  }

  if (text.includes('预订建议')) {
    return {
      content:
        '如果是周五晚餐或周末热门商圈，建议提前 1 天预约；2-4 人聚餐优先选 17:30 前或 20:00 后到店，排队时间会明显短一些。',
      cards: []
    };
  }

  if (text.includes('附近美食')) {
    return {
      content: '附近热度较高的美食方向有火锅、甜品下午茶和日式烧肉，如果你告诉我预算或人数，我还能继续缩小范围。',
      cards: shops.slice(0, 3).map((shop) => ({
        name: shop.name,
        score: shop.score,
        price: shop.price,
        reason: shop.recommendText
      }))
    };
  }

  return {
    content:
      '我可以帮你推荐商家、分析菜品、解答美食问题，或者给你一份更适合约会/聚餐/家庭聚会的点单建议。你也可以直接问我“北京朝阳区火锅推荐”。',
    cards: []
  };
}

