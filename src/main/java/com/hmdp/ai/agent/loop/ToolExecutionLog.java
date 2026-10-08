package com.hmdp.ai.agent.loop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionLog {

    private String toolName;

    private Object payload;
}
