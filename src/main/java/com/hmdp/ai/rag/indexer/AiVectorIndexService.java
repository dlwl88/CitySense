package com.hmdp.ai.rag.indexer;

import com.hmdp.ai.dto.IndexRebuildResponse;

public interface AiVectorIndexService {

    IndexRebuildResponse rebuildAll();

    IndexRebuildResponse rebuildShop(Long shopId);

    int rebuildKnowledge();

    void ensureInitialized();
}
