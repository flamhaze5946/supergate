package com.bestv.supergate.util;

import com.bestv.flame.client.handler.softrouter.RouterServer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC请求上下文
 * Created by flamhaze on 16/8/5.
 */
@Component
public class RpcRequestContext {

    /** 处理器缓存 */
    private static final Map<String, RequestSenderHandler> SENDER_HANDLER_MAP = new ConcurrentHashMap<String, RequestSenderHandler>();

    /** 路由服务 */
    private static RouterServer routerServer;

    /**
     * 获取处理器
     * @param appName 应用名
     * @return 处理器
     */
    public static RequestSenderHandler getHandler(String appName)
    {
        return SENDER_HANDLER_MAP.get(appName);
    }

    /**
     * put处理器
     * @param appName     应用名
     * @param handler     处理器
     */
    public static void putHandler(String appName, RequestSenderHandler handler)
    {
        SENDER_HANDLER_MAP.put(appName, handler);
    }

    /**
     * method for set routerServer
     */
    public static void setRouterServer(RouterServer routerServer) {
        RpcRequestContext.routerServer = routerServer;
    }

    /**
     * method for get routerServer
     */
    public static RouterServer getRouterServer() {
        return routerServer;
    }
}