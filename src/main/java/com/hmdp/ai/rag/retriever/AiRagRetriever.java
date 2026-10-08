package com.hmdp.ai.rag.retriever;

import com.hmdp.ai.config.AiAgentProperties;
import com.hmdp.ai.dto.BlogVectorHitDTO;
import com.hmdp.ai.dto.KnowledgeHitDTO;
import com.hmdp.ai.dto.ShopToolDTO;
import com.hmdp.ai.rag.AiMetadataConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG 检索入口。
 * 当前同时负责知识库、店铺画像、探店评论三类向量检索，以及知识库的文本兜底召回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRagRetriever {

    private final AiAgentProperties aiAgentProperties;

    @Qualifier("knowledgeVectorStore")
    private final VectorStore knowledgeVectorStore;

    @Qualifier("shopProfileVectorStore")
    private final VectorStore shopProfileVectorStore;

    @Qualifier("blogReviewVectorStore")
    private final VectorStore blogReviewVectorStore;

    public List<KnowledgeHitDTO> searchKnowledge(String query) {
        return searchKnowledge(query, null);
    }

    public List<KnowledgeHitDTO> searchKnowledge(String query, Filter.Expression filterExpression) {
        Set<String> keywords = extractKeywords(query);
        // 先走向量召回，再用关键词兜底补强平台规则这类高确定性知识。
        List<KnowledgeHitDTO> vectorHits = knowledgeVectorStore.similaritySearch(
                        buildSearchRequest(query, aiAgentProperties.getRag().getTopK(), aiAgentProperties.getRag().getKnowledgeSimilarityThreshold(), filterExpression))
                .stream()
                .map(this::toKnowledgeHit)
                .toList();
        List<KnowledgeHitDTO> fallbackHits = keywordFallbackKnowledgeSearch(query, keywords);
        if (!fallbackHits.isEmpty()) {
            List<KnowledgeHitDTO> relevantVectorHits = vectorHits.stream()
                    .filter(hit -> keywordScore(hit.getContent(), keywords) > 0)
                    .toList();
            List<KnowledgeHitDTO> mergedHits = mergeKnowledgeHits(relevantVectorHits, fallbackHits, keywords);
            log.info("Knowledge fallback merged for query='{}', vectorSize={}, fallbackSize={}, mergedSize={}",
                    query, vectorHits.size(), fallbackHits.size(), mergedHits.size());
            return mergedHits;
        }
        if (!vectorHits.isEmpty()) {
            log.debug("Knowledge vector hits found for query='{}', size={}", query, vectorHits.size());
            return vectorHits;
        }
        return List.of();
    }

    public List<ShopToolDTO> searchShopProfiles(String query, Integer topK) {
        int finalTopK = topK == null || topK <= 0 ? aiAgentProperties.getRag().getTopK() : topK;
        return shopProfileVectorStore.similaritySearch(buildSearchRequest(query, finalTopK, null))
                .stream()
                .map(this::toShopHit)
                .toList();
    }

    public List<BlogVectorHitDTO> searchBlogReviews(String query, Integer topK) {
        int finalTopK = topK == null || topK <= 0 ? aiAgentProperties.getRag().getTopK() : topK;
        return blogReviewVectorStore.similaritySearch(buildSearchRequest(query, finalTopK, null))
                .stream()
                .map(this::toBlogHit)
                .toList();
    }

    public List<BlogVectorHitDTO> searchBlogReviewsByShop(Long shopId, String query, Integer topK) {
        Filter.Expression filterExpression = new FilterExpressionBuilder().eq(AiMetadataConstants.SHOP_ID, shopId).build();
        int finalTopK = topK == null || topK <= 0 ? aiAgentProperties.getRag().getTopK() : topK;
        return blogReviewVectorStore.similaritySearch(buildSearchRequest(query, finalTopK, null, filterExpression))
                .stream()
                .map(this::toBlogHit)
                .toList();
    }

    public boolean hasKnowledgeDocuments() {
        return !knowledgeVectorStore.similaritySearch(buildSearchRequest("基于 Spring Boot 与 Spring AI 的同城生活服务平台", 1, aiAgentProperties.getRag().getKnowledgeSimilarityThreshold(), null)).isEmpty();
    }

    public boolean hasShopProfileDocuments() {
        return !shopProfileVectorStore.similaritySearch(buildSearchRequest("推荐店铺", 1, null)).isEmpty();
    }

    public boolean hasBlogReviewDocuments() {
        return !blogReviewVectorStore.similaritySearch(buildSearchRequest("探店评价", 1, null)).isEmpty();
    }

    private SearchRequest buildSearchRequest(String query, int topK, Filter.Expression filterExpression) {
        return buildSearchRequest(query, topK, aiAgentProperties.getRag().getSimilarityThreshold(), filterExpression);
    }

    private SearchRequest buildSearchRequest(String query, int topK, Double similarityThreshold, Filter.Expression filterExpression) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(StringUtils.hasText(query) ? query : "基于 Spring Boot 与 Spring AI 的同城生活服务平台")
                .topK(topK);
        if (similarityThreshold != null) {
            builder.similarityThreshold(similarityThreshold);
        }
        if (filterExpression != null) {
            builder.filterExpression(filterExpression);
        }
        return builder.build();
    }

    private List<KnowledgeHitDTO> keywordFallbackKnowledgeSearch(String query, Set<String> keywords) {
        if (!StringUtils.hasText(query) || keywords.isEmpty()) {
            return List.of();
        }

        // 兜底方案直接扫 classpath 下的知识文本，避免向量库弱召回时“明明有文档却答不上来”。
        List<KnowledgeHitDTO> results = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath:knowledge/*.txt");
            for (Resource resource : resources) {
                String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String[] passages = text.split("(\\r?\\n){2,}");
                for (int i = 0; i < passages.length; i++) {
                    String passage = passages[i].trim();
                    if (!StringUtils.hasText(passage)) {
                        continue;
                    }
                    int score = keywordScore(passage, keywords);
                    if (score <= 0) {
                        continue;
                    }
                    KnowledgeHitDTO dto = new KnowledgeHitDTO();
                    dto.setId(resource.getFilename() + "#fallback-" + i);
                    dto.setSourceId(resource.getFilename() + "#fallback-" + i);
                    dto.setContent(passage);
                    dto.setScore((double) score);
                    results.add(dto);
                }
            }
        }
        catch (Exception e) {
            log.warn("Knowledge keyword fallback search failed for query='{}': {}", query, e.getMessage());
            return List.of();
        }

        return results.stream()
                .sorted(Comparator.comparing(KnowledgeHitDTO::getScore, Comparator.nullsLast(Double::compareTo)).reversed())
                .limit(aiAgentProperties.getRag().getTopK())
                .toList();
    }

    private List<KnowledgeHitDTO> mergeKnowledgeHits(List<KnowledgeHitDTO> vectorHits,
                                                     List<KnowledgeHitDTO> fallbackHits,
                                                     Set<String> keywords) {
        List<KnowledgeHitDTO> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (KnowledgeHitDTO hit : fallbackHits) {
            if (seen.add(dedupKey(hit))) {
                merged.add(hit);
            }
        }
        for (KnowledgeHitDTO hit : vectorHits) {
            if (seen.add(dedupKey(hit))) {
                merged.add(hit);
            }
        }
        return merged.stream()
                .sorted(Comparator
                        .<KnowledgeHitDTO>comparingInt(hit -> keywordScore(hit.getContent(), keywords))
                        .reversed()
                        .thenComparing(KnowledgeHitDTO::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(aiAgentProperties.getRag().getTopK())
                .toList();
    }

    private String dedupKey(KnowledgeHitDTO hit) {
        String content = hit == null || hit.getContent() == null ? "" : hit.getContent().replaceAll("\\s+", " ").trim();
        String sourceId = hit == null ? "" : String.valueOf(hit.getSourceId());
        return sourceId + "|" + content;
    }

    private List<KnowledgeHitDTO> keywordFallbackKnowledgeSearch(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        Set<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            return List.of();
        }
        return keywordFallbackKnowledgeSearch(query, keywords);
    }

    private Set<String> extractKeywords(String query) {
        String normalized = query.replaceAll("[^\\p{IsHan}A-Za-z0-9]", "");
        if (!StringUtils.hasText(normalized)) {
            return Set.of();
        }
        // 这里不是通用分词器，而是非常轻量的业务关键词抽取，目标只是给 fallback 检索加召回。
        List<String> stopWords = Arrays.asList("请问", "一下", "什么", "哪些", "有啥", "有什么", "怎么", "可以", "告诉我", "我想", "呢", "吗");
        for (String stopWord : stopWords) {
            normalized = normalized.replace(stopWord, "");
        }
        if (!StringUtils.hasText(normalized)) {
            normalized = query.replaceAll("\\s+", "");
        }

        Set<String> keywords = new LinkedHashSet<>();
        if (normalized.length() <= 4) {
            keywords.add(normalized);
        }
        for (int i = 0; i < normalized.length() - 1; i++) {
            keywords.add(normalized.substring(i, i + 2));
        }
        keywords.addAll(extractDomainKeywords(query));
        return keywords.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> extractDomainKeywords(String query) {
        Set<String> keywords = new LinkedHashSet<>();
        if (query.contains("会员") || query.contains("SVIP")) {
            keywords.add("会员");
            keywords.add("SVIP");
        }
        if (query.contains("福利")) {
            keywords.add("福利");
        }
        if (query.contains("积分")) {
            keywords.add("积分");
        }
        if (query.contains("秒杀")) {
            keywords.add("秒杀");
        }
        if (query.contains("退款")) {
            keywords.add("退款");
        }
        if (query.contains("优惠券")) {
            keywords.add("优惠券");
        }
        return keywords;
    }

    private int keywordScore(String passage, Set<String> keywords) {
        return keywords.stream()
                .filter(Objects::nonNull)
                .filter(keyword -> keyword.length() >= 2)
                .mapToInt(keyword -> passage.contains(keyword) ? 1 : 0)
                .sum();
    }

    private KnowledgeHitDTO toKnowledgeHit(Document document) {
        KnowledgeHitDTO dto = new KnowledgeHitDTO();
        dto.setId(document.getId());
        dto.setContent(document.getText());
        dto.setScore(document.getScore());
        dto.setSourceId(asString(document.getMetadata(), AiMetadataConstants.SOURCE_ID));
        return dto;
    }

    private ShopToolDTO toShopHit(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        ShopToolDTO dto = new ShopToolDTO();
        dto.setShopId(asLong(metadata, AiMetadataConstants.SHOP_ID));
        dto.setName(asString(metadata, AiMetadataConstants.SHOP_NAME));
        dto.setTypeId(asLong(metadata, AiMetadataConstants.TYPE_ID));
        dto.setArea(asString(metadata, AiMetadataConstants.AREA));
        dto.setAddress(asString(metadata, AiMetadataConstants.ADDRESS));
        dto.setAvgPrice(asLong(metadata, AiMetadataConstants.AVG_PRICE));
        dto.setRating(asDouble(metadata, AiMetadataConstants.RATING));
        dto.setComments(asInteger(metadata, AiMetadataConstants.COMMENTS));
        dto.setSold(asInteger(metadata, AiMetadataConstants.SOLD));
        dto.setOpenHours(asString(metadata, AiMetadataConstants.OPEN_HOURS));
        dto.setX(asDouble(metadata, AiMetadataConstants.X));
        dto.setY(asDouble(metadata, AiMetadataConstants.Y));
        dto.setSemanticScore(document.getScore());
        return dto;
    }

    private BlogVectorHitDTO toBlogHit(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        BlogVectorHitDTO dto = new BlogVectorHitDTO();
        dto.setBlogId(asLong(metadata, AiMetadataConstants.SOURCE_ID));
        dto.setShopId(asLong(metadata, AiMetadataConstants.SHOP_ID));
        dto.setUserId(asLong(metadata, AiMetadataConstants.USER_ID));
        dto.setTitle(asString(metadata, AiMetadataConstants.TITLE));
        dto.setContent(document.getText());
        dto.setScore(document.getScore());
        return dto;
    }

    private String asString(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer asInteger(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private Double asDouble(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.valueOf(String.valueOf(value));
    }
}