package com.hmdp.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "xxl.job.enabled", havingValue = "true", matchIfMissing = true)
public class XxlJobConfig {
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(
            @Value("${xxl.job.admin.addresses}") String addresses,
            @Value("${xxl.job.accessToken}") String token,
            @Value("${xxl.job.executor.appname}") String appname,
            @Value("${xxl.job.executor.address}") String address,
            @Value("${xxl.job.executor.ip}") String ip,
            @Value("${xxl.job.executor.port}") int port,
            @Value("${xxl.job.executor.logpath}") String logpath,
            @Value("${xxl.job.executor.logretentiondays}") int retentionDays) {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(addresses);
        executor.setAccessToken(token);
        executor.setAppname(appname);
        executor.setAddress(address);
        executor.setIp(ip);
        executor.setPort(port);
        executor.setLogPath(logpath);
        executor.setLogRetentionDays(retentionDays);
        return executor;
    }
}
