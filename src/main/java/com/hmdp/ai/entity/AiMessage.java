package com.hmdp.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_ai_message")
public class AiMessage implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private String role;

    private String content;

    private String toolName;

    private String toolPayload;

    @TableField("created_time")
    private LocalDateTime createTime;
}
