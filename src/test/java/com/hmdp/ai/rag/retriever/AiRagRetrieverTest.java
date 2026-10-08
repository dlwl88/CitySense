package com.hmdp.ai.rag.retriever;

import com.hmdp.ai.config.AiAgentProperties;
import com.hmdp.ai.dto.KnowledgeHitDTO;
import com.hmdp.ai.rag.AiMetadataConstants;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiRagRetrieverTest {

    @Test
    void shouldPreferKnowledgeFallbackWhenVectorHitIsIrrelevant() {
        VectorStore knowledgeVectorStore = mock(VectorStore.class);
        when(knowledgeVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("tech-1", "技术方案：缓存穿透解决方法是缓存空对象或布隆过滤器。", Map.of(
                        AiMetadataConstants.SOURCE_ID, "tech-1"
                ))
        ));

        AiRagRetriever retriever = newRetriever(knowledgeVectorStore);

        List<KnowledgeHitDTO> hits = retriever.searchKnowledge("超级会员有什么福利吗");

        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).getContent().contains("超级会员"));
        assertTrue(hits.get(0).getContent().contains("4 张 5 元无门槛优惠券"));
        assertFalse(hits.stream().anyMatch(hit -> "tech-1".equals(hit.getSourceId())));
    }

    @Test
    void shouldFallbackToKnowledgeFileWhenVectorStoreReturnsNothing() {
        VectorStore knowledgeVectorStore = mock(VectorStore.class);
        when(knowledgeVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        AiRagRetriever retriever = newRetriever(knowledgeVectorStore);

        List<KnowledgeHitDTO> hits = retriever.searchKnowledge("超级会员有什么福利吗");

        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).getContent().contains("积分倍增"));
    }

    @Test
    void shouldKeepVectorHitsWhenFallbackHasNoMatch() {
        VectorStore knowledgeVectorStore = mock(VectorStore.class);
        when(knowledgeVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("intro-1", "基于 Spring Boot 与 Spring AI 的同城生活服务平台是一个类似于大众点评的消费评价平台。", Map.of(
                        AiMetadataConstants.SOURCE_ID, "intro-1"
                ))
        ));

        AiRagRetriever retriever = newRetriever(knowledgeVectorStore);

        List<KnowledgeHitDTO> hits = retriever.searchKnowledge("这个应用主要做什么");

        assertEquals(1, hits.size());
        assertEquals("intro-1", hits.get(0).getSourceId());
        assertTrue(hits.get(0).getContent().contains("消费评价平台"));
    }

    private AiRagRetriever newRetriever(VectorStore knowledgeVectorStore) {
        AiAgentProperties properties = new AiAgentProperties();
        VectorStore shopProfileVectorStore = mock(VectorStore.class);
        VectorStore blogReviewVectorStore = mock(VectorStore.class);
        return new AiRagRetriever(properties, knowledgeVectorStore, shopProfileVectorStore, blogReviewVectorStore);
    }
}
