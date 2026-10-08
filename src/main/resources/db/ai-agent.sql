CREATE TABLE IF NOT EXISTS `tb_ai_conversation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户ID',
  `title` varchar(128) NOT NULL COMMENT '会话标题',
  `scene` varchar(32) NOT NULL COMMENT '最近一次识别出的场景',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：1-正常，0-关闭',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_ai_conversation_user_status_updated` (`user_id`, `status`, `updated_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话表';

CREATE TABLE IF NOT EXISTS `tb_ai_message` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `conversation_id` bigint(20) NOT NULL COMMENT '会话ID',
  `role` varchar(16) NOT NULL COMMENT '消息角色：user/assistant/tool',
  `content` text COMMENT '消息内容',
  `tool_name` varchar(64) DEFAULT NULL COMMENT '工具名',
  `tool_payload` text COMMENT '工具摘要或 JSON',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_ai_message_conversation_created` (`conversation_id`, `created_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 消息表';
