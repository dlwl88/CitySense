package com.hmdp.ai.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.hmdp.ai.mapper")
public class AiMapperConfiguration {
}
