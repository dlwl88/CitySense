package com.hmdp.ai.agent.loop;

import com.hmdp.ai.tool.AgentLoopControlTools;
import com.hmdp.ai.tool.DianPingAgentTools;
import com.hmdp.ai.tool.McpLocalLifeTools;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地工具注册表。
 * 这里把项目内的 @Tool 方法转换成 Spring AI 的 ToolCallback，供 AgentLoop 手动调用。
 */
@Component
public class AgentLoopToolRegistry {

    private final List<ToolCallback> toolCallbacks;

    private final Map<String, ToolCallback> callbackMap;

    @org.springframework.beans.factory.annotation.Autowired
    public AgentLoopToolRegistry(DianPingAgentTools dianPingAgentTools,
                                 AgentLoopControlTools controlTools,
                                 McpLocalLifeTools mcpLocalLifeTools) {
        this(Arrays.asList(ToolCallbacks.from(dianPingAgentTools, controlTools, mcpLocalLifeTools)));
    }

    // 这个构造器只给测试使用，便于注入假的 ToolCallback 集合。
    AgentLoopToolRegistry(List<ToolCallback> toolCallbacks) {
        this.toolCallbacks = List.copyOf(toolCallbacks);
        Map<String, ToolCallback> map = new LinkedHashMap<>();
        for (ToolCallback toolCallback : toolCallbacks) {
            map.put(toolCallback.getToolDefinition().name(), toolCallback);
        }
        this.callbackMap = Map.copyOf(map);
    }

    public List<ToolCallback> getToolCallbacks() {
        return toolCallbacks;
    }

    public ToolCallback getToolCallback(String toolName) {
        return callbackMap.get(toolName);
    }
}