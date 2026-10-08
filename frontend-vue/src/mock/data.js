import shopHotpot from '@/assets/images/shop-hotpot.svg';
import shopSpicy from '@/assets/images/shop-spicy.svg';
import shopBbq from '@/assets/images/shop-bbq.svg';
import shopDessert from '@/assets/images/shop-dessert.svg';
import dishLamb from '@/assets/images/dish-lamb.svg';
import dishShrimp from '@/assets/images/dish-shrimp.svg';
import dishGarlic from '@/assets/images/dish-garlic.svg';
import dishTripe from '@/assets/images/dish-tripe.svg';
import dishHuanghou from '@/assets/images/dish-huanghou.svg';
import dishBingfen from '@/assets/images/dish-bingfen.svg';
import dishBeef from '@/assets/images/dish-beef.svg';
import dishCorn from '@/assets/images/dish-corn.svg';
import dishIcecream from '@/assets/images/dish-icecream.svg';
import dishTart from '@/assets/images/dish-tart.svg';
import dishCoffee from '@/assets/images/dish-coffee.svg';
import dishCroissant from '@/assets/images/dish-croissant.svg';
import commentHotpot1 from '@/assets/images/comment-hotpot-1.svg';
import commentHotpot2 from '@/assets/images/comment-hotpot-2.svg';
import commentHotpot3 from '@/assets/images/comment-hotpot-3.svg';
import commentDessert1 from '@/assets/images/comment-dessert-1.svg';

export const categoryList = [
  { id: 'food', name: '美食', icon: '🍜', description: '火锅烤肉日料' },
  { id: 'relax', name: '休闲娱乐', icon: '🎮', description: 'KTV桌游影院' },
  { id: 'hotel', name: '酒店', icon: '🏨', description: '度假出差住宿' },
  { id: 'movie', name: '电影', icon: '🎬', description: '热映大片特惠' },
  { id: 'takeout', name: '外卖', icon: '🛵', description: '30分钟好味到家' },
  { id: 'dessert', name: '甜点饮品', icon: '🧁', description: '下午茶快乐加倍' },
  { id: 'fitness', name: '丽人健身', icon: '💪', description: '瑜伽美发美甲' },
  { id: 'travel', name: '周边游', icon: '🧳', description: '出游度假好去处' }
];

export const shops = [
  {
    id: 1,
    category: 'food',
    name: '京味铜锅涮肉',
    score: 4.8,
    price: 108,
    distance: '1.2km',
    tags: ['朝阳区', '老北京火锅', '排队王'],
    cover: shopHotpot,
    status: '营业中',
    phone: '010-88886666',
    address: '北京市朝阳区三里屯太古里北街18号',
    recommendText: '手切鲜羊肉和麻酱小料很稳，适合朋友聚餐。',
    dishes: [
      { name: '手切鲜羊肉', price: 58, desc: '肉质细嫩，锅开即涮', image: dishLamb },
      { name: '手工虾滑', price: 36, desc: '弹嫩爽滑，推荐必点', image: dishShrimp },
      { name: '老北京糖蒜', price: 12, desc: '解腻小菜，铜锅灵魂搭子', image: dishGarlic }
    ],
    offers: ['新客团购 88 元双人餐', '夜宵档 21:00 后啤酒买一送一'],
    comments: [
      {
        id: 101,
        user: '爱吃火锅的Mina',
        avatar: 'https://dummyimage.com/80x80/f4c27a/ffffff&text=M',
        score: 5,
        content: '羊肉新鲜，麻酱香而不腻，服务员会主动帮忙调料。',
        images: [commentHotpot1, commentHotpot2]
      },
      {
        id: 102,
        user: '周末探店局',
        avatar: 'https://dummyimage.com/80x80/9bc1ff/ffffff&text=Z',
        score: 4,
        content: '环境有点吵但味道不错，排队时间较长，建议早点到。',
        images: [commentHotpot3]
      }
    ]
  },
  {
    id: 2,
    category: 'food',
    name: '川渝九宫格火锅',
    score: 4.7,
    price: 122,
    distance: '850m',
    tags: ['朝阳区', '重庆火锅', '辣度在线'],
    cover: shopSpicy,
    status: '营业中',
    phone: '010-87654321',
    address: '北京市朝阳区百子湾路22号院',
    recommendText: '锅底够香，毛肚黄喉很脆，辣口爱好者会很上头。',
    dishes: [
      { name: '鲜毛肚', price: 42, desc: '七上八下口感最佳', image: dishTripe },
      { name: '黄喉', price: 28, desc: '脆爽弹牙，越煮越香', image: dishHuanghou },
      { name: '冰粉', price: 15, desc: '餐后解辣，清爽收尾', image: dishBingfen }
    ],
    offers: ['学生认证送酸梅汤', '工作日午市 7.8 折'],
    comments: [
      {
        id: 103,
        user: '朝阳吃辣冠军',
        avatar: 'https://dummyimage.com/80x80/f09090/ffffff&text=C',
        score: 5,
        content: '牛油锅底非常香，服务速度快，黄喉太绝了。',
        images: [commentHotpot2]
      }
    ]
  },
  {
    id: 3,
    category: 'food',
    name: '炙烤日式烧肉屋',
    score: 4.6,
    price: 146,
    distance: '2.4km',
    tags: ['望京', '日式烧肉', '情侣约会'],
    cover: shopBbq,
    status: '营业中',
    phone: '010-66778899',
    address: '北京市朝阳区望京SOHO塔2裙楼',
    recommendText: '和牛品质稳定，适合约会，包间氛围不错。',
    dishes: [
      { name: '雪花牛小排', price: 88, desc: '肉香浓郁，烤到微焦最好吃', image: dishBeef },
      { name: '芝士玉米', price: 22, desc: '小朋友也爱吃', image: dishCorn },
      { name: '海盐冰淇淋', price: 18, desc: '餐后解腻很加分', image: dishIcecream }
    ],
    offers: ['双人和牛套餐 268 元', '生日当月赠甜品拼盘'],
    comments: [
      {
        id: 104,
        user: '东京胃口',
        avatar: 'https://dummyimage.com/80x80/6ab0ff/ffffff&text=T',
        score: 4,
        content: '牛小排很香，环境有氛围，服务员会帮忙代烤。',
        images: []
      }
    ]
  },
  {
    id: 4,
    category: 'dessert',
    name: '橙意烘焙研究所',
    score: 4.9,
    price: 45,
    distance: '650m',
    tags: ['国贸', '甜品', '打卡拍照'],
    cover: shopDessert,
    status: '营业中',
    phone: '010-99887766',
    address: '北京市朝阳区国贸商城南区3层',
    recommendText: '法式甜点颜值高，下午茶拍照很出片。',
    dishes: [
      { name: '流心芝士塔', price: 26, desc: '奶香浓郁，入口绵密', image: dishTart },
      { name: '生椰拿铁', price: 28, desc: '椰香和咖啡平衡感好', image: dishCoffee },
      { name: '开心果可颂', price: 18, desc: '酥脆层次感很足', image: dishCroissant }
    ],
    offers: ['第二杯饮品半价', '会员积分可换切片蛋糕'],
    comments: [
      {
        id: 105,
        user: '甜口星人',
        avatar: 'https://dummyimage.com/80x80/ffc1e3/ffffff&text=S',
        score: 5,
        content: '生椰拿铁很好喝，店内装修很适合拍照。',
        images: [commentDessert1]
      }
    ]
  }
];

export const cityOptions = ['北京', '上海', '广州', '深圳', '杭州'];

export const quickScenes = [
  '推荐附近美食',
  '推荐商家菜品',
  '解答美食问题',
  '预订建议'
];

export const profileData = {
  menus: [
    { title: '我的收藏', value: 18 },
    { title: '我的订单', value: 6 },
    { title: '我的评论', value: 24 }
  ],
  entries: ['地址管理', '设置', '联系客服']
};
