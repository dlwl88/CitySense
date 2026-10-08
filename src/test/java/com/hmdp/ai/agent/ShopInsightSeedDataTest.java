package com.hmdp.ai.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.ShopMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Objects;

@SpringBootTest(properties = {
        "spring.main.lazy-initialization=true",
        "spring.ai.openai.api-key=test-key",
        "spring.ai.openai.chat.options.model=test-model",
        "spring.ai.openai.embedding.options.model=test-embedding-model"
})
class ShopInsightSeedDataTest {

    private static final long TARGET_SHOP_ID = 5L;
    private static final String TITLE_PREFIX = "[AI-SEED]";
    private static final String DEFAULT_IMAGE = "/imgs/blogs/blog1.jpg";

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private BlogCommentsMapper blogCommentsMapper;

    @Test
    void seedShopFiveBlogsForAiInsight() {
        Shop shop = shopMapper.selectById(TARGET_SHOP_ID);
        Assertions.assertNotNull(shop, "shopId=5 的店铺不存在，无法填充测试数据");

        cleanupExistingSeeds();

        List<BlogSeed> seeds = List.of(
                new BlogSeed(2L, TITLE_PREFIX + " 海底捞夜宵局，番茄锅和虾滑依旧稳", """
                        周五晚上和朋友来吃海底捞，虽然门口等位了二十多分钟，但翻台还算快。
                        我们点了番茄锅、牛油锅、招牌虾滑、捞派毛肚和酥肉，番茄锅很浓，虾滑弹牙，毛肚七上八下口感特别脆。
                        服务员加汤、换骨碟、递围裙都很及时，桌面也收拾得很干净。
                        缺点是周末高峰确实有点吵，人均比普通火锅店偏高一点，但整体还是聚餐首选。
                        """, 56, List.of(
                        "番茄锅真的很稳，适合不能吃辣的人。",
                        "高峰期有点吵，但服务没得说。"
                )),
                new BlogSeed(1L, TITLE_PREFIX + " 海底捞生日聚会体验，服务细节拉满", """
                        这次是给同事过生日选的海底捞，提前和门店说了需求，到店后安排得挺顺。
                        期间有帮忙布置小道具，服务员会主动提醒蛋糕时间，仪式感很足。
                        菜品里我最喜欢捞派豆花、精品肥牛和虾滑，食材新鲜，锅底也不咸。
                        唯一的问题是周末包间不好约，临时来基本只能坐大厅。
                        """, 48, List.of(
                        "适合朋友聚会和生日局。",
                        "大厅人多的时候会比较热闹。"
                )),
                new BlogSeed(4L, TITLE_PREFIX + " 深夜来这家海底捞，服务依然在线", """
                        晚上十点多来吃火锅，本来担心服务会打折，结果体验比预期好很多。
                        员工态度热情，点单之后上菜速度快，牛肉粒、鸭血和手打虾滑都挺新鲜。
                        饮品和小料台补得比较勤，适合下班后想吃一顿热乎的。
                        不足是停车位偏紧张，商场高峰时找车位有点麻烦。
                        """, 33, List.of(
                        "夜宵局可以冲，上菜挺快。",
                        "停车确实不太方便。"
                )),
                new BlogSeed(5L, TITLE_PREFIX + " 海底捞适合带家人，番茄锅友好", """
                        带爸妈来吃火锅，我特意选了番茄锅和菌菇锅，老人接受度很高。
                        番茄锅酸甜适中，嫩牛肉和捞面都不错，服务员会主动帮忙下菜、提醒火候。
                        店里整体卫生做得不错，桌椅和地面都比较干净。
                        如果能再多一些安静座位会更好，靠走道的位置来回走动的人比较多。
                        """, 41, List.of(
                        "带长辈来很合适，番茄锅不刺激。",
                        "靠走道的位置体验会差一点。"
                )),
                new BlogSeed(2L, TITLE_PREFIX + " 海底捞牛油锅够香，但排队时间偏长", """
                        冲着牛油锅来的，香味确实足，涮毛肚、黄喉和肥牛都很过瘾。
                        服务员会及时巡台，空盘收得快，还会提醒锅底越煮越咸可以加汤。
                        这家店问题主要是周末排队偏久，我们等了接近五十分钟，体感一般。
                        如果不是特别执着服务体验，其实更建议错峰来。
                        """, 37, List.of(
                        "牛油锅很香，毛肚必点。",
                        "周末等位时间太长了。"
                )),
                new BlogSeed(1L, TITLE_PREFIX + " 海底捞小料台丰富，聚餐省心", """
                        几个人聚餐最怕众口难调，海底捞的好处就是锅底和小料选择多。
                        这次点了清油麻辣锅和番茄锅，清油锅香而不呛，虾滑、午餐肉和巴沙鱼都在线。
                        服务依旧稳定，围裙、手机袋、眼镜布这些小细节很加分。
                        价格不算便宜，但胜在基本不会踩雷，适合请客或者团队聚餐。
                        """, 44, List.of(
                        "小料台种类多，自己能调出喜欢的口味。",
                        "价格不便宜，但比较稳。"
                )),
                new BlogSeed(4L, TITLE_PREFIX + " 一个人也能吃海底捞，体验比想象中好", """
                        工作日中午一个人来吃，门店安排座位还挺快，没有想象中那么尴尬。
                        点了半份菜组合，虾滑、土豆片、鸭血都不错，番茄锅特别适合单人慢慢吃。
                        服务员不会过度打扰，但需要时响应很快，整体节奏舒服。
                        缺点是商场中午人流量大，门口区域稍微有点拥挤。
                        """, 29, List.of(
                        "一个人吃也不会不自在。",
                        "中午门口有点挤。"
                )),
                new BlogSeed(5L, TITLE_PREFIX + " 服务很好，但甜品补给有点慢", """
                        这家海底捞的服务态度没什么可挑的，桌边照顾得很周到。
                        菜品表现也比较稳定，虾滑、捞派豆花和肥牛都值得点，锅底浓度也在线。
                        不过这次小料台附近的水果和甜品补给速度一般，有一段时间基本空着。
                        如果门店能把高峰期补货节奏提上来，体验会更完整。
                        """, 21, List.of(
                        "服务很好，菜品也稳。",
                        "水果补得慢，希望改进。"
                )),
                new BlogSeed(2L, TITLE_PREFIX + " 学生党偶尔来打牙祭，食材确实新鲜", """
                        虽然人均不低，但海底捞的食材和服务确实比普通火锅店更稳定。
                        这次吃到的精品肥牛、嫩牛肉、虾滑和豆花都很新鲜，番茄锅也很适合拍照。
                        店内灯光明亮，卫生感不错，适合第一次来杭州玩的朋友。
                        预算有限的话不建议放开点，控制菜量更合适。
                        """, 26, List.of(
                        "适合带外地朋友来吃一顿稳的。",
                        "人均偏高，学生党要控制预算。"
                )),
                new BlogSeed(1L, TITLE_PREFIX + " 海底捞环境干净，适合情侣约会", """
                        这家店在商场里位置不难找，整体装修明亮整洁，没有老火锅店那种油腻感。
                        我和对象点了鸳鸯锅、虾滑、酥肉和毛肚，出品稳定，服务员也比较有边界感。
                        适合想吃得舒服、又不想踩雷的情侣约会。
                        但如果追求非常安静的氛围，这里就一般，尤其是晚饭高峰会比较热闹。
                        """, 31, List.of(
                        "情侣约会没问题，环境挺干净。",
                        "想要特别安静就不太合适。"
                )),
                new BlogSeed(4L, TITLE_PREFIX + " 海底捞味道稳定，但价格感知偏高", """
                        味道方面没什么问题，牛油锅底香，番茄锅也浓，虾滑和肥牛依旧是安全牌。
                        服务和卫生都保持在高水平，基本不会出错。
                        但这次结账还是觉得价格偏高，尤其是人多时不注意点单很容易超预算。
                        如果你更看重性价比，这家未必是最优选；如果更看重服务，那还是值得来。
                        """, 24, List.of(
                        "味道和服务都稳。",
                        "价格会超预算，要注意点单。"
                )),
                new BlogSeed(5L, TITLE_PREFIX + " 团建聚餐选海底捞，容错率高", """
                        部门团建临时决定吃火锅，最后选了海底捞，原因就是容错率高。
                        店员对多人聚餐很熟练，换盘、添汤、分单都处理得比较顺。
                        菜品里大家一致觉得虾滑、毛肚、肥牛和捞面表现最好，几乎没有踩雷菜。
                        缺点是高峰期上菜会有一点波动，第一轮菜没有完全同时到齐。
                        """, 39, List.of(
                        "团建聚餐很省心，大家都能接受。",
                        "第一轮菜上的节奏可以再稳一点。"
                ))
        );

        int insertedBlogs = 0;
        int insertedComments = 0;
        for (BlogSeed seed : seeds) {
            Blog blog = new Blog();
            blog.setShopId(TARGET_SHOP_ID);
            blog.setUserId(seed.userId());
            blog.setTitle(seed.title());
            blog.setImages(DEFAULT_IMAGE);
            blog.setContent(seed.content());
            blog.setLiked(seed.liked());
            blog.setComments(seed.commentTexts().size());
            blogMapper.insert(blog);
            insertedBlogs++;

            for (String commentText : seed.commentTexts()) {
                BlogComments comment = new BlogComments();
                comment.setUserId(seed.userId());
                comment.setBlogId(blog.getId());
                comment.setParentId(0L);
                comment.setAnswerId(0L);
                comment.setContent(commentText);
                comment.setLiked(0);
                comment.setStatus(false);
                blogCommentsMapper.insert(comment);
                insertedComments++;
            }
        }

        long seededCount = blogMapper.selectCount(new LambdaQueryWrapper<Blog>()
                .eq(Blog::getShopId, TARGET_SHOP_ID)
                .like(Blog::getTitle, TITLE_PREFIX));

        Assertions.assertEquals(seeds.size(), seededCount, "测试探店笔记数量不匹配");
        Assertions.assertTrue(insertedComments > 0, "测试评论未成功插入");

        System.out.printf("AI 探店测试数据已写入: shopId=%d, blogs=%d, comments=%d%n",
                TARGET_SHOP_ID, insertedBlogs, insertedComments);
    }

    private void cleanupExistingSeeds() {
        List<Blog> existingBlogs = blogMapper.selectList(new LambdaQueryWrapper<Blog>()
                .eq(Blog::getShopId, TARGET_SHOP_ID)
                .like(Blog::getTitle, TITLE_PREFIX));

        List<Long> blogIds = existingBlogs.stream()
                .map(Blog::getId)
                .filter(Objects::nonNull)
                .toList();

        if (!blogIds.isEmpty()) {
            blogCommentsMapper.delete(new LambdaQueryWrapper<BlogComments>()
                    .in(BlogComments::getBlogId, blogIds));
            blogMapper.delete(new LambdaQueryWrapper<Blog>()
                    .in(Blog::getId, blogIds));
        }
    }

    private record BlogSeed(Long userId, String title, String content, Integer liked, List<String> commentTexts) {
    }
}
