package com.bestv.supergate;

import com.bestv.supergate.filter.AbstractFilter;
import com.bestv.supergate.util.RpcRequestContext;
import com.netflix.zuul.FilterFileManager;
import com.netflix.zuul.FilterLoader;
import com.netflix.zuul.context.ContextLifecycleFilter;
import com.netflix.zuul.groovy.GroovyCompiler;
import com.netflix.zuul.groovy.GroovyFileFilter;
import com.netflix.zuul.http.ZuulServlet;
import com.netflix.zuul.monitoring.MonitoringHelper;
import com.bestv.flame.client.handler.softrouter.RouterServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.boot.context.embedded.ServletRegistrationBean;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import org.springframework.stereotype.Component;

import java.net.URL;


/**
 * 超级网关
 * Created by flamhaze on 16/7/27.
 */
@SpringBootApplication
@EnableEurekaClient
@ComponentScan(basePackages = "com.bestv.supergate")
@ImportResource("classpath*:spring-*.xml")
public class SuperGate {

    public static void main(String[] args)
    {
        SpringApplication.run(SuperGate.class, args);
    }

    /**
     * 初始化网关环境
     * 定时扫描groovy文件目录并载入之
     */
    @Component
    public static class GrooviesScanRunner implements CommandLineRunner
    {
        /** 路由服务器 */
        @Autowired
        private RouterServer routerServer;

        /** groovy文件相对路径 */
        private static final String GROOVIES_DIC_PATH = "groovies";

        /** groovy文件刷新间隔 */
        private static final Integer GROOVIES_REFRESH_INTERVAL = 10;

        /** 过滤路径 */
        public static final String FILTER_PATH = "/*";

        /**
         * @see CommandLineRunner#run(String...)
         */
        public void run(String... args) throws Exception {

            synchronized (AbstractFilter.class)
            {
                RpcRequestContext.setRouterServer(routerServer);
                AbstractFilter.class.notifyAll();
            }

            URL scriptUrl = SuperGate.class.getClassLoader().getResource(GROOVIES_DIC_PATH);

            if (scriptUrl == null)
            {
                throw new RuntimeException("脚本路径载入错误, 请检查 [" + GROOVIES_DIC_PATH + " ]目录有效.");
            }

            String scriptRoot = scriptUrl.getPath();

            MonitoringHelper.initMocks();

            FilterLoader.getInstance().setCompiler(new GroovyCompiler());
            try {
                FilterFileManager.setFilenameFilter(new GroovyFileFilter());
                FilterFileManager.init(GROOVIES_REFRESH_INTERVAL, scriptRoot);
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * 注册servlet及其映射
     * @return servlet
     */
    @Bean
    public ServletRegistrationBean zuulServlet() {
        ServletRegistrationBean servlet = new ServletRegistrationBean(new ZuulServlet());
        servlet.addUrlMappings(GrooviesScanRunner.FILTER_PATH);
        return servlet;
    }

    /**
     * 注册过滤器
     * @return 过滤器执行者
     */
    @Bean
    public FilterRegistrationBean contextLifecycleFilter() {
        FilterRegistrationBean filter = new FilterRegistrationBean(new ContextLifecycleFilter());
        filter.addUrlPatterns(GrooviesScanRunner.FILTER_PATH);
        return filter;
    }
}
