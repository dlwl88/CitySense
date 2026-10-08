package com.hmdp.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.ai.entity.AiConversation;
import com.hmdp.ai.mapper.AiConversationMapper;
import com.hmdp.ai.service.AiConversationService;
import org.springframework.stereotype.Service;

@Service
public class AiConversationServiceImpl extends ServiceImpl<AiConversationMapper, AiConversation> implements AiConversationService {
}
