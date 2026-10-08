package com.hmdp.ai.agent.loop;

import com.hmdp.component.AgentLoopObserver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLoopExecutorTest {

    @Test
    void shouldAlwaysUseMultiAgentLoop() {
        ManualToolAgentLoopExecutor manualToolAgentLoopExecutor = mock(ManualToolAgentLoopExecutor.class);
        AgentLoopExecution execution = new AgentLoopExecution();
        execution.setFinalAnswer("ok");
        when(manualToolAgentLoopExecutor.execute(any(AgentLoopRequest.class), anyList(), any(AgentLoopObserver.class)))
                .thenReturn(execution);

        AgentLoopExecutor executor = new AgentLoopExecutor(manualToolAgentLoopExecutor);
        AgentLoopRequest request = new AgentLoopRequest();
        request.setPrompt("超级会员有什么福利");
        request.setHistoryMessages(new ArrayList<>());

        AgentLoopResult result = executor.execute(request);

        assertEquals("MULTI_AGENT", result.getScene());
        assertNotNull(result.getAgentLoopExecution());
        verify(manualToolAgentLoopExecutor).execute(any(AgentLoopRequest.class), anyList(), any(AgentLoopObserver.class));
    }
}
