package com.hmdp.ai.rag.indexer;

import com.hmdp.ai.dto.IndexRebuildResponse;
import com.hmdp.ai.rag.AiMetadataConstants;
import com.hmdp.ai.rag.retriever.AiRagRetriever;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Shop;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 向量索引构建器。
 * 负责把平台知识、店铺画像和探店笔记三类数据写入不同的向量集合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiVectorIndexServiceImpl implements AiVectorIndexService {

    @Qualifier("knowledgeVectorStore")
    private final VectorStore knowledgeVectorStore;

    @Qualifier("shopProfileVectorStore")
    private final VectorStore shopProfileVectorStore;

    @Qualifier("blogReviewVectorStore")
    private final VectorStore blogReviewVectorStore;

    private final IShopService shopService;

    private final IBlogService blogService;

    private final AiRagRetriever aiRagRetriever;

    private final TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();

    @Override
    public IndexRebuildResponse rebuildAll() {
        IndexRebuildResponse response = new IndexRebuildResponse();
        response.setScope("all");
        response.setKnowledgeCount(rebuildKnowledge());
        response.setShopProfileCount(rebuildAllShopProfiles());
        response.setBlogReviewCount(rebuildAllBlogReviews());
        response.setSuccess(true);
        response.setMessage("AI 全量索引重建完成");
        return response;
    }

    @Override
    public IndexRebuildResponse rebuildShop(Long shopId) {
        IndexRebuildResponse response = new IndexRebuildResponse();
        response.setScope("shop:" + shopId);
        response.setKnowledgeCount(0);
        response.setShopProfileCount(rebuildSingleShopProfile(shopId));
        response.setBlogReviewCount(rebuildShopBlogReviews(shopId));
        response.setSuccess(true);
        response.setMessage("店铺索引重建完成");
        return response;
    }

    @Override
    public int rebuildKnowledge() {
        try {
            knowledgeVectorStore.delete(new FilterExpressionBuilder().eq(AiMetadataConstants.DOC_TYPE, AiMetadataConstants.DOC_TYPE_KNOWLEDGE).build());
            List<Document> documents = loadKnowledgeDocuments();
            if (!documents.isEmpty()) {
                knowledgeVectorStore.add(documents);
            }
            return documents.size();
        }
        catch (Exception e) {
            log.error("Failed to rebuild knowledge vector store", e);
            throw new IllegalStateException("重建知识库索引失败", e);
        }
    }

    @Override
    public void ensureInitialized() {
        // 启动时只做“缺啥补啥”，避免每次启动都全量重建索引。
        if (!aiRagRetriever.hasKnowledgeDocuments()) {
            rebuildKnowledge();
        }
        if (!aiRagRetriever.hasShopProfileDocuments()) {
            rebuildAllShopProfiles();
        }
        if (!aiRagRetriever.hasBlogReviewDocuments()) {
            rebuildAllBlogReviews();
        }
    }

    private int rebuildAllShopProfiles() {
        shopProfileVectorStore.delete(new FilterExpressionBuilder().eq(AiMetadataConstants.DOC_TYPE, AiMetadataConstants.DOC_TYPE_SHOP_PROFILE).build());
        List<Shop> shops = shopService.list();
        List<Document> documents = shops.stream().map(this::toShopProfileDocument).toList();
        if (!documents.isEmpty()) {
            shopProfileVectorStore.add(documents);
        }
        return documents.size();
    }

    private int rebuildSingleShopProfile(Long shopId) {
        shopProfileVectorStore.delete(new FilterExpressionBuilder().eq(AiMetadataConstants.SHOP_ID, shopId).build());
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            return 0;
        }
        shopProfileVectorStore.add(List.of(toShopProfileDocument(shop)));
        return 1;
    }

    private int rebuildAllBlogReviews() {
        blogReviewVectorStore.delete(new FilterExpressionBuilder().eq(AiMetadataConstants.DOC_TYPE, AiMetadataConstants.DOC_TYPE_BLOG_REVIEW).build());
        List<Document> documents = buildBlogDocuments(blogService.list());
        if (!documents.isEmpty()) {
            blogReviewVectorStore.add(documents);
        }
        return documents.size();
    }

    private int rebuildShopBlogReviews(Long shopId) {
        blogReviewVectorStore.delete(new FilterExpressionBuilder().eq(AiMetadataConstants.SHOP_ID, shopId).build());
        List<Blog> blogs = blogService.lambdaQuery().eq(Blog::getShopId, shopId).list();
        List<Document> documents = buildBlogDocuments(blogs);
        if (!documents.isEmpty()) {
            blogReviewVectorStore.add(documents);
        }
        return documents.size();
    }

    private List<Document> loadKnowledgeDocuments() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:knowledge/*.txt");
        List<Document> indexedDocuments = new ArrayList<>();
        for (Resource resource : resources) {
            // 平台规则文档会先切块再入库，避免单条文档过长导致召回粒度太粗。
            TextReader reader = new TextReader(resource);
            List<Document> splitDocuments = tokenTextSplitter.apply(reader.get());
            for (int i = 0; i < splitDocuments.size(); i++) {
                Document document = splitDocuments.get(i);
                Map<String, Object> metadata = new HashMap<>(document.getMetadata());
                String sourceId = resource.getFilename() + "#" + i;
                metadata.put(AiMetadataConstants.DOC_TYPE, AiMetadataConstants.DOC_TYPE_KNOWLEDGE);
                metadata.put(AiMetadataConstants.SOURCE_ID, sourceId);
                metadata.put(AiMetadataConstants.SHOP_ID, 0L);
                metadata.put(AiMetadataConstants.TYPE_ID, 0L);
                metadata.put(AiMetadataConstants.AREA, "");
                metadata.put(AiMetadataConstants.USER_ID, 0L);
                indexedDocuments.add(new Document(sourceId, document.getText(), metadata));
            }
        }
        return indexedDocuments;
    }

    private Document toShopProfileDocument(Shop shop) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(AiMetadataConstants.DOC_TYPE, AiMetadataConstants.DOC_TYPE_SHOP_PROFILE);
        metadata.put(AiMetadataConstants.SOURCE_ID, shop.getId());
        metadata.put(AiMetadataConstants.SHOP_ID, shop.getId());
        metadata.put(AiMetadataConstants.TYPE_ID, shop.getTypeId());
        metadata.put(AiMetadataConstants.AREA, defaultString(shop.getArea()));
        metadata.put(AiMetadataConstants.USER_ID, 0L);
        metadata.put(AiMetadataConstants.SHOP_NAME, shop.getName());
        metadata.put(AiMetadataConstants.ADDRESS, shop.getAddress());
        metadata.put(AiMetadataConstants.AVG_PRICE, shop.getAvgPrice());
        metadata.put(AiMetadataConstants.RATING, shop.getScore() == null ? null : shop.getScore() / 10.0);
        metadata.put(AiMetadataConstants.COMMENTS, shop.getComments());
        metadata.put(AiMetadataConstants.SOLD, shop.getSold());
        metadata.put(AiMetadataConstants.OPEN_HOURS, defaultString(shop.getOpenHours()));
        metadata.put(AiMetadataConstants.X, shop.getX());
        metadata.put(AiMetadataConstants.Y, shop.getY());

        // 店铺画像文本尽量保留结构化字段，方便向量召回和结果解释共用一份数据。
        String text = """
                店铺名：%s
                分类ID：%s
                商圈：%s
                地址：%s
                均价：%s元
                评分：%s分
                评论数：%s
                销量：%s
                营业时间：%s
                """.formatted(
                shop.getName(),
                shop.getTypeId(),
                defaultString(shop.getArea()),
                shop.getAddress(),
                shop.getAvgPrice(),
                shop.getScore() == null ? "未知" : shop.getScore() / 10.0,
                shop.getComments(),
                shop.getSold(),
                defaultString(shop.getOpenHours())
        );
        return new Document(String.valueOf(shop.getId()), text, metadata);
    }

    private List<Document> buildBlogDocuments(List<Blog> blogs) {
        if (blogs == null || blogs.isEmpty()) {
            return List.of();
        }
        // 评论向量不只存正文，还补上标题和店铺名，降低纯正文短评的语义损失。
        Map<Long, Shop> shopMap = shopService.list().stream().collect(Collectors.toMap(Shop::getId, shop -> shop));
        List<Document> documents = new ArrayList<>(blogs.size());
        for (Blog blog : blogs) {
            Shop shop = shopMap.get(blog.getShopId());
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(AiMetadataConstants.DOC_TYPE, AiMetadataConstants.DOC_TYPE_BLOG_REVIEW);
            metadata.put(AiMetadataConstants.SOURCE_ID, blog.getId());
            metadata.put(AiMetadataConstants.SHOP_ID, blog.getShopId());
            metadata.put(AiMetadataConstants.TYPE_ID, shop == null ? 0L : shop.getTypeId());
            metadata.put(AiMetadataConstants.AREA, shop == null ? "" : defaultString(shop.getArea()));
            metadata.put(AiMetadataConstants.USER_ID, blog.getUserId());
            metadata.put(AiMetadataConstants.TITLE, blog.getTitle());

            String text = """
                    标题：%s
                    店铺：%s
                    正文：%s
                    """.formatted(
                    defaultString(blog.getTitle()),
                    shop == null ? "未知店铺" : shop.getName(),
                    defaultString(blog.getContent())
            );
            documents.add(new Document(String.valueOf(blog.getId()), text, metadata));
        }
        return documents;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}