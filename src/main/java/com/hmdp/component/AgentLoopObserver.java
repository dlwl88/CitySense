package com.hmdp.component;

import com.hmdp.ai.agent.loop.AgentLoopTrace;
import com.hmdp.ai.agent.loop.ToolExecutionLog;

public interface AgentLoopObserver {

    AgentLoopObserver NOOP = new AgentLoopObserver() {
    };

    default void onTrace(AgentLoopTrace trace) {
    }

    default void onToolExecuted(ToolExecutionLog log) {
    }

    static AgentLoopObserver noop() {
        return NOOP;
    }
}
