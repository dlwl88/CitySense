package com.hmdp.ai.tool;

import com.hmdp.ai.config.AiAgentProperties;
import com.hmdp.ai.dto.BlogVectorHitDTO;
import com.hmdp.ai.dto.KnowledgeHitDTO;
import com.hmdp.ai.dto.ShopReviewSummaryDTO;
import com.hmdp.ai.dto.ShopToolDTO;
import com.hmdp.ai.rag.retriever.AiRagRetriever;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Shop;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DianPingAgentTools {

    private static final int REVIEW_SAMPLE_THRESHOLD = 5;

    private final IShopService shopService;

    private final IBlogService blogService;

    private final AiRagRetriever aiRagRetriever;

    private final AiAgentProperties aiAgentProperties;

    @Tool(description = "获取单个店铺详情")
    public ShopToolDTO getShopDetail(@ToolParam(description = "店铺ID") Long shopId) {
        Result result = shopService.queryById(shopId);
        if (result == null || result.getData() == null) {
            return null;
        }
        return toShopToolDTO((Shop) result.getData());
    }

    @Tool(description = "按分类和坐标搜索附近店铺")
    public List<ShopToolDTO> searchNearbyShops(
            @ToolParam(description = "店铺分类ID") Integer typeId,
            @ToolParam(description = "经度") Double x,
            @ToolParam(description = "纬度") Double y,
            @ToolParam(description = "分页页码") Integer page) {
        int currentPage = page == null || page < 1 ? 1 : page;
        Result result = shopService.queryShopByType(typeId, currentPage, x, y);
        if (result == null || result.getData() == null) {
            return List.of();
        }
        List<Shop> shops = (List<Shop>) result.getData();
        return shops.stream().map(this::toShopToolDTO).toList();
    }

    @Tool(description = "按关键词搜索店铺名称、商圈和地址")
    public List<ShopToolDTO> searchShopsByKeyword(@ToolParam(description = "搜索关键词") String keyword) {
        return shopService.lambdaQuery()
                .like(Shop::getName, keyword)
                .or(wrapper -> wrapper.like(Shop::getArea, keyword).or().like(Shop::getAddress, keyword))
                .last("limit 10")
                .list()
                .stream()
                .map(this::toShopToolDTO)
                .toList();
    }

    @Tool(description = "搜索平台知识库，返回语义相关的业务知识片段")
    public List<KnowledgeHitDTO> searchKnowledgeBase(@ToolParam(description = "用户查询") String query) {
        return aiRagRetriever.searchKnowledge(query);
    }

    @Tool(description = "获取店铺探店笔记原始文本，优先返回点赞高的样本")
    public List<String> getShopReviewTexts(@ToolParam(description = "店铺ID") Long shopId) {
        List<Blog> blogs = blogService.lambdaQuery()
                .eq(Blog::getShopId, shopId)
                .orderByDesc(Blog::getLiked)
                .last("limit " + Math.max(aiAgentProperties.getInsight().getMaxReviewSamples(), 1))
                .list();
        if (CollectionUtils.isEmpty(blogs)) {
            return List.of();
        }
        return blogs.stream()
                .map(blog -> "【" + safe(blog.getTitle()) + "】" + truncate(safe(blog.getContent()), aiAgentProperties.getInsight().getMaxReviewCharsPerSample()))
                .toList();
    }

    @Tool(description = "获取店铺评论摘要和样本量说明")
    public ShopReviewSummaryDTO getShopReviewSummary(@ToolParam(description = "店铺ID") Long shopId) {
        List<String> reviewTexts = getShopReviewTexts(shopId);
        ShopReviewSummaryDTO dto = new ShopReviewSummaryDTO();
        dto.setShopId(shopId);
        dto.setSampleCount(reviewTexts.size());
        dto.setReviewTexts(reviewTexts);
        dto.setCombinedText(truncate(String.join("\n---\n", reviewTexts), aiAgentProperties.getInsight().getMaxCombinedChars()));
        if (reviewTexts.size() < REVIEW_SAMPLE_THRESHOLD) {
            dto.setSampleSizeNotice("当前仅找到 " + reviewTexts.size() + " 条探店笔记，样本量较少，结论仅供参考。");
        }
        else {
            dto.setSampleSizeNotice("当前基于 " + reviewTexts.size() + " 条探店笔记进行分析。");
        }
        return dto;
    }

    @Tool(description = "搜索店铺画像向量，返回语义相关的候选店铺")
    public List<ShopToolDTO> vectorSearchShopProfiles(
            @ToolParam(description = "用户查询") String query,
            @ToolParam(description = "召回条数") Integer topK) {
        return aiRagRetriever.searchShopProfiles(query, topK);
    }

    @Tool(description = "搜索探店笔记向量，返回语义相关的评论样本")
    public List<BlogVectorHitDTO> vectorSearchBlogs(
            @ToolParam(description = "用户查询") String query,
            @ToolParam(description = "召回条数") Integer topK) {
        return aiRagRetriever.searchBlogReviews(query, topK);
    }

    @Tool(description = "按店铺搜索探店笔记向量，返回语义相关的评论样本")
    public List<BlogVectorHitDTO> vectorSearchBlogsByShop(
            @ToolParam(description = "店铺ID") Long shopId,
            @ToolParam(description = "用户查询") String query,
            @ToolParam(description = "召回条数") Integer topK) {
        return aiRagRetriever.searchBlogReviewsByShop(shopId, query, topK);
    }

    private ShopToolDTO toShopToolDTO(Shop shop) {
        if (shop == null) {
            return null;
        }
        ShopToolDTO dto = new ShopToolDTO();
        dto.setShopId(shop.getId());
        dto.setName(shop.getName());
        dto.setTypeId(shop.getTypeId());
        dto.setArea(shop.getArea());
        dto.setAddress(shop.getAddress());
        dto.setAvgPrice(shop.getAvgPrice());
        dto.setRating(shop.getScore() == null ? null : shop.getScore() / 10.0);
        dto.setComments(shop.getComments());
        dto.setSold(shop.getSold());
        dto.setOpenHours(shop.getOpenHours());
        dto.setX(shop.getX());
        dto.setY(shop.getY());
        dto.setDistanceMeters(shop.getDistance());
        return dto;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength || maxLength <= 0) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}