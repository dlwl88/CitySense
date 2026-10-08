package com.hmdp.ai.component;

import com.hmdp.ai.config.AiAgentProperties;
import com.hmdp.ai.rag.indexer.AiVectorIndexService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseInitializer {

    private final AiVectorIndexService aiVectorIndexService;
    private final AiAgentProperties aiAgentProperties;

    @PostConstruct
    public void init() {
        if (!aiAgentProperties.getBootstrap().isEnabled()) {
            log.info(">>>>>> [AI-AGENT] vector index bootstrap disabled by config");
            return;
        }
        log.info(">>>>>> [AI-AGENT] checking vector indexes...");
        try {
            aiVectorIndexService.ensureInitialized();
            log.info(">>>>>> [AI-AGENT] vector index bootstrap finished");
        } catch (Exception e) {
            if (aiAgentProperties.getBootstrap().isFailFast()) {
                throw e;
            }
            log.warn(">>>>>> [AI-AGENT] vector index bootstrap skipped: {}", e.getMessage(), e);
        }
    }
}
