package com.bestv.supergate.config;

import com.bestv.flame.client.handler.softrouter.EurekaRouterServer;
import com.bestv.flame.client.handler.softrouter.RouterServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 服务设置
 * Created by flamhaze on 16/8/5.
 */
@Configuration
public class ServiceConfig {

    @Bean
    public RouterServer routerServer()
    {
        return new EurekaRouterServer();
    }
}
