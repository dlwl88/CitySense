import { categoryList, shops as mockShops } from '@/mock/data';

const mockShopMap = new Map(mockShops.map((shop) => [Number(shop.id), shop]));

function fallbackCategoryMeta(name = '', index = 0) {
  const preset = categoryList.find((item) => name.includes(item.name) || item.name.includes(name));
  return preset || categoryList[index % categoryList.length];
}

export function normalizeCategory(type, index = 0) {
  const meta = fallbackCategoryMeta(type?.name || '', index);
  return {
    id: Number(type?.id),
    name: type?.name || meta.name,
    icon: meta.icon,
    description: meta.description,
    rawIcon: type?.icon || ''
  };
}

export function normalizeCategories(list = []) {
  return list.map((item, index) => normalizeCategory(item, index));
}

export function normalizeShop(raw) {
  const shopId = Number(raw?.id);
  const mock = mockShopMap.get(shopId) || {};
  const firstImage = raw?.images?.split(',')?.map((item) => item.trim())?.find(Boolean);
  const normalizedScore = raw?.score == null ? mock.score || 4.5 : Number(raw.score) > 5 ? Number(raw.score) / 10 : Number(raw.score);
  const distanceValue = raw?.distance == null ? mock.distance || '附近' : formatDistance(raw.distance);

  return {
    id: shopId,
    typeId: Number(raw?.typeId || mock.typeId || 0),
    category: mock.category || '',
    name: raw?.name || mock.name || '未命名商铺',
    score: Number(Number(normalizedScore).toFixed(1)),
    price: Number(raw?.avgPrice ?? mock.price ?? 0),
    distance: distanceValue,
    tags: mock.tags || [raw?.area, raw?.openHours].filter(Boolean),
    cover: normalizeImage(firstImage) || mock.cover,
    status: mock.status || '营业中',
    phone: mock.phone || '暂未提供',
    address: raw?.address || mock.address || '暂无地址',
    recommendText: mock.recommendText || buildRecommendText(raw),
    dishes: mock.dishes || [],
    offers: mock.offers || [],
    comments: mock.comments || [],
    sold: Number(raw?.sold || 0),
    commentCount: Number(raw?.comments || mock.comments?.length || 0),
    openHours: raw?.openHours || mock.openHours || '营业时间以门店为准',
    raw
  };
}

export function mergeShopDetail(raw) {
  return normalizeShop(raw);
}

function normalizeImage(image) {
  if (!image) {
    return '';
  }
  if (/^https?:\/\//i.test(image)) {
    return image;
  }
  return image;
}

function formatDistance(distance) {
  const value = Number(distance);
  if (Number.isNaN(value)) {
    return String(distance);
  }
  if (value < 1000) {
    return `${Math.round(value)}m`;
  }
  return `${(value / 1000).toFixed(1)}km`;
}

function buildRecommendText(raw) {
  const sold = raw?.sold ? `销量 ${raw.sold}` : '口碑不错';
  const comments = raw?.comments ? `评论 ${raw.comments}` : '适合打卡';
  return `${sold}，${comments}，值得一试。`;
}
