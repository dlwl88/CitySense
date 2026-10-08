package com.hmdp.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.ai.entity.AiMessage;
import com.hmdp.ai.mapper.AiMessageMapper;
import com.hmdp.ai.service.AiMessageService;
import org.springframework.stereotype.Service;

@Service
public class AiMessageServiceImpl extends ServiceImpl<AiMessageMapper, AiMessage> implements AiMessageService {
}
