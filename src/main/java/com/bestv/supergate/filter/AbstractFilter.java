package com.bestv.supergate.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.bestv.flame.common.dto.RpcRequest;
import com.bestv.supergate.util.RequestSenderHandler;
import com.bestv.supergate.util.RpcRequestContext;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.bestv.common.dto.ErrorContext;
import com.bestv.common.dto.ErrorInfo;
import com.bestv.common.dto.Node;
import com.bestv.common.dto.NodeTree;
import com.bestv.common.util.CommonUtil;
import com.bestv.common.util.StringUtil;
import com.bestv.flame.client.handler.softrouter.RouterServer;
import com.bestv.flame.client.handler.softrouter.strategy.WholeConnectStrategy;

import java.io.IOException;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 过滤器基类
 * Created by flamhaze on 16/8/4.
 */
public abstract class AbstractFilter extends ZuulFilter {

    /** 路由服务 */
    private RouterServer routerServer;

    /** 服务码 */
    private String serviceCode;

    /** 应用名 */
    private String appName;

    /** 接口名 */
    private String serviceInterface;

    /** 方法名 */
    private String methodName;

    /** 参数组字符串缓冲器 */
    private StringBuilder paramStringBuffer;

    /** 类信息字符串缓冲器 */
    private StringBuilder classInfoStringBuffer;

    /** 参数结构 */
    private NodeTree argumentTree;

    /** 参数映射 */
    private Map<String, Object> argumentMap;

    /** 参数路径前缀 */
    private static final String ARGS_PATH_PREFIX = "args";

    /** 方法名路径 */
    private static final String METHOD_NAME_PATH = "methodName";

    /** 接口名路径 */
    private static final String SERVICE_INTERFACE_PATH = "serviceInterface";

    /** RPC请求类名 */
    private static final String RPC_REQUEST_CLASS_NAME = RpcRequest.class.getName();

    /** 特殊字符串A */
    private static final String SPECIAL_STR_A = "★☆";

    /** 特殊字符串B */
    private static final String SPECIAL_STR_B= "☆★";

    /** 路径分隔符 */
    private static final String SEPARATOR = ".";

    /** 全局的映射关系, 缓存各种配置 */
    private static final Map<String, String> GEN_MAP = new ConcurrentHashMap<String, String>();

    /** 空对象 */
    private static final JSONObject NULL_OBJECT = JSONObject.parseObject("{\"value\":null}");

    /** 业务执行器 */
    private RequestSenderHandler handler;

    /** 路由服务器等待超时时间, 毫秒 */
    private static final Long ROUTER_SERVER_WAIT_TIMEOUT = 60000L;

    /**
     * 构造函数, 一些初始设定
     */
    public AbstractFilter() throws InterruptedException
    {
        classInfoStringBuffer = new StringBuilder();
        paramStringBuffer = new StringBuilder();
        loadEnvironment();

        loadHandlerByAppName(appName);
    }

    /**
     * 载入基于应用名指定的处理器
     * @param appNameForHandler 应用名
     */
    private void loadHandlerByAppName(String appNameForHandler) throws InterruptedException
    {
        waitRouterServer();

        handler = RpcRequestContext.getHandler(appNameForHandler);

        if (handler == null)
        {
            synchronized (this)
            {
                if (handler == null)
                {
                    handler = new RequestSenderHandler(appName, serviceInterface, null, 1, true, true, routerServer, new WholeConnectStrategy());
                    RpcRequestContext.putHandler(appNameForHandler, handler);
                }
            }
        }
    }

    /**
     * 等待路由服务就绪
     * @throws InterruptedException
     */
    private void waitRouterServer() throws InterruptedException
    {
        synchronized (AbstractFilter.class)
        {
            if (RpcRequestContext.getRouterServer() == null)
            {
                AbstractFilter.class.wait(ROUTER_SERVER_WAIT_TIMEOUT);
            }

            if (RpcRequestContext.getRouterServer() == null)
            {
                throw new RuntimeException("路由服务器无法载入, 无法启动处理器.");
            }

            routerServer = RpcRequestContext.getRouterServer();
        }
    }

    /**
     * @see ZuulFilter#run()
     */
    public Object run() {

        if (isServiceCodeMatch())
        {
            RequestContext.getCurrentContext().getResponse().addHeader("Access-Control-Allow-Origin", "*");

            try {
                JSONObject response = getResponse();

                removeJsonType(response);

                if (response == null)
                {
                    response = NULL_OBJECT;
                }

                boolean success = response.getBoolean("success");

                if (success)
                {
                    JSONObject baseResult = response.getJSONObject("targetResult");

                    if (baseResult == null)
                    {
                        throw new RuntimeException("结果为空");
                    }

                    if(!baseResult.getBoolean("success"))
                    {
                        ErrorContext errorContext = baseResult.getObject("errorContext", ErrorContext.class);
                        for (ErrorInfo errorInfo : errorContext.getErrorInfos())
                        {
                            errorInfo.setStackTraceElements(null);
                        }
                        baseResult.put("errorContext", errorContext);
                    }

                    response = JSON.parseObject(JSON.toJSONString(baseResult), Feature.DisableSpecialKeyDetect);
                }
                else
                {
                    outputResponse("RPC调用失败".getBytes("UTF-8"));
                    RequestContext.getCurrentContext().setSendZuulResponse(false);
                    return null;
                }



                RequestContext.getCurrentContext().getResponse().setContentType("text/html;charset=utf-8");
                outputResponse(response.toJSONString().getBytes("UTF-8"));
                RequestContext.getCurrentContext().setSendZuulResponse(false);

            } catch (Exception e) {

                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 初始化环境, 需要加载以下属性
     * <ul>
     *     <li>服务码</li>
     *     <li>应用名</li>
     *     <li>接口名</li>
     *     <li>请求JSON样式, 带占位符</li>
     *     <li>请求参数key组, 有顺序, 和requestGen匹配</li>
     * </ul>
     */
    public abstract void loadEnvironment();

    /**
     * 判断请求的服务码是否对应
     * @return 服务码是否对应
     */
    private boolean isServiceCodeMatch()
    {
        return StringUtil.equals(serviceCode, getRequestServiceCode());
    }

    public JSONObject getResponse() throws Exception {

        String requestGen = buildGen();

        // 经常变的部分时常更新
        handler.setRequestGen(requestGen);

        return (JSONObject) handler.doAction(null, getParameterByKeys(getParameterKeys(argumentMap)));
    }

    /**
     * 去除JSON中的type信息
     * @param jsonObject JSON对象
     */
    private void removeJsonType(JSONObject jsonObject)
    {
        List<String> needDelList = new ArrayList<String>();
        for (String key : jsonObject.keySet())
        {
            if (jsonObject.get(key) instanceof JSONObject)
            {
                JSONObject childJsonObject = (JSONObject) jsonObject.get(key);

                if (childJsonObject.size() == 1 && childJsonObject.containsKey("@type"))
                {
                    needDelList.add(key);
                    continue;
                }

                removeJsonType(childJsonObject);
            }
        }

        for (String needDelKey : needDelList)
        {
            jsonObject.remove(needDelKey);
        }

        jsonObject.remove("@type");
    }

    /**
     * 根据参数key组获取参数组
     * @param keys 参数key组
     * @return 参数组
     */
    private String[] getParameterByKeys(String[] keys)
    {
        String[] parameters = new String[keys.length];
        for (int i = 0; i < keys.length; i++)
        {
            parameters[i] = getParameter(keys[i]);

            if (parameters[i] != null)
            {
                parameters[i] = "\"" + parameters[i] + "\"";
            }

            else
            {
                parameters[i] = "null";
            }
        }
        return parameters;
    }

    /**
     * 获取参数键值组
     * @param keyMap 参数键值映射
     * @return 参数键值组
     */
    private String[] getParameterKeys(Map<String, Object> keyMap)
    {
        int keySize = keyMap.size();

        String[] keys = new String[keySize];
        List<String> keyList = new ArrayList<String>(keyMap.keySet());
        Collections.sort(keyList);

        for (int i = 0; i < keySize; i++)
        {
            keys[i] = (String) keyMap.get(keyList.get(i));
        }

        return keys;
    }

    /**
     * @see ZuulFilter#filterType()
     */
    public String filterType() {
        return "route";
    }

    /**
     * @see ZuulFilter#filterOrder()
     */
    public int filterOrder() {
        return 0;
    }

    /**
     * @see ZuulFilter#shouldFilter()
     */
    public boolean shouldFilter() {

        return true;
    }

    /**
     * 获取当前请求服务码
     * @return 服务码
     */
    protected String getRequestServiceCode()
    {
        return RequestContext.getCurrentContext().getRequest().getRequestURI().substring(1);
    }

    /**
     * 填充返回结果
     * @param response      返回结果
     * @throws IOException  没有取到输出流
     */
    protected void outputResponse(byte[] response) throws IOException {

        OutputStream outputStream = RequestContext.getCurrentContext().getResponse().getOutputStream();
        outputStream.write(response);
        outputStream.flush();
        outputStream.close();
    }

    /**
     * 获取参数
     * @param paramKey 参数key
     * @return 参数
     */
    protected String getParameter(String paramKey)
    {
        return RequestContext.getCurrentContext().getRequest().getParameter(paramKey);
    }


    /**
     * 将节点设置为数组类型
     * @param path 节点路径
     */
    protected void setArray(String path)
    {
        Node node = argumentTree.getNode(path);
        if (node == null)
        {
            node = new Node();
            node.setNodeName(path);
            argumentTree.addNode(node);
        }
        node.setNodeArray(true);
    }

    /**
     * 设置类信息
     * @param path          参数路径
     * @param className     类名, 需要包含包名
     */
    protected void setClassInfo(String path, String className)
    {
        classInfoStringBuffer
                .append(SPECIAL_STR_A)
                .append(path)
                .append(SPECIAL_STR_B)
                .append(className)
                .append(SPECIAL_STR_A);
    }

    /**
     * 设置方法名
     * @param methodName 方法名
     */
    protected void setMethodName(String methodName)
    {
        this.methodName = methodName;
    }

    /**
     * 设置接口信息
     * @param serviceInterface 接口名
     */
    protected void setServiceInterface(String serviceInterface)
    {
        this.serviceInterface = serviceInterface;
    }

    /**
     * 链接参数
     * @param path          代码参数路径
     * @param parameterKey  请求参数key
     */
    protected void linkParameter(String path, String parameterKey)
    {
        paramStringBuffer
                .append(SPECIAL_STR_A)
                .append(path)
                .append(SPECIAL_STR_B)
                .append(parameterKey)
                .append(SPECIAL_STR_A);
    }

    /**
     * 构建请求字符串生成模板
     * @return 请求字符串生成模板
     */
    private String buildGen()
    {
        if (StringUtil.isBlank(serviceInterface))
        {
            throw new RuntimeException("没有设置接口名!");
        }

        if (StringUtil.isBlank(methodName))
        {
            throw new RuntimeException("没有设置方法名!");
        }

        String genKey = buildGenKey();
        String requestGen = GEN_MAP.get(genKey);

        // 没有缓存到请求字符串生成模板
        if (StringUtil.isBlank(requestGen))
        {
            synchronized (this)
            {
                if (StringUtil.isBlank(requestGen))
                {
                    // 构建新树
                    argumentTree = new NodeTree();
                    argumentMap = new HashMap<String, Object>();
                    argumentTree.getHeadNode().setValue(RPC_REQUEST_CLASS_NAME);
                    setArray(ARGS_PATH_PREFIX);

                    // 载入接口名信息
                    addFixedNode(SERVICE_INTERFACE_PATH, serviceInterface);

                    // 载入方法名信息
                    addFixedNode(METHOD_NAME_PATH, methodName);

                    // 载入类信息
                    String classMapInfo = classInfoStringBuffer.toString();
                    String[] classInfoStrings = classMapInfo.split(SPECIAL_STR_A);

                    for (String classInfoString : classInfoStrings)
                    {
                        if (StringUtil.isBlank(classInfoString))
                        {
                            continue;
                        }

                        String[] classInfo = classInfoString.split(SPECIAL_STR_B);
                        String path = classInfo[0];
                        String className = classInfo[1];
                        addFixedNode(ARGS_PATH_PREFIX + SEPARATOR + path, className);
                    }


                    // 载入参数映射信息
                    String paramMapInfo = paramStringBuffer.toString();
                    String[] paramInfoStrings = paramMapInfo.split(SPECIAL_STR_A);

                    for (String paramInfoString : paramInfoStrings)
                    {
                        if (StringUtil.isBlank(paramInfoString))
                        {
                            continue;
                        }

                        String[] paramInfo = paramInfoString.split(SPECIAL_STR_B);
                        String path = paramInfo[0];
                        String paramKey = paramInfo[1];

                        Node paramNode = new Node();
                        paramNode.setNodeName(ARGS_PATH_PREFIX + SEPARATOR + path);
                        paramNode.setValue(paramKey);
                        argumentTree.addNode(paramNode);
                    }

                    requestGen = structToJsonString();
                    GEN_MAP.put(genKey, requestGen);
                }
            }
        }

        return requestGen;
    }

    /**
     * 构造gen的键值
     * @return gen的键值
     */
    private String buildGenKey()
    {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder
                .append(SPECIAL_STR_A)
                .append(serviceInterface)
                .append(SPECIAL_STR_B)
                .append(methodName)
                .append(SPECIAL_STR_A);

        if (classInfoStringBuffer != null)
        {
            stringBuilder
                    .append(SPECIAL_STR_A)
                    .append(classInfoStringBuffer.toString())
                    .append(SPECIAL_STR_A);
        }

        if (paramStringBuffer != null)
        {
            stringBuilder
                    .append(SPECIAL_STR_A)
                    .append(paramStringBuffer.toString())
                    .append(SPECIAL_STR_A);
        }

        return stringBuilder.toString();
    }

    public String structToJsonString()
    {
        return CommonUtil.nodeToJsonString(argumentTree.getHeadNode(), argumentMap, true);
    }


    /**
     * 增加非格式化值节点
     * @param nodePath 节点路径
     * @param value    节点值
     */
    private void addFixedNode(String nodePath, String value)
    {
        Node node = new Node();
        node.setNodeName(nodePath);
        node.setValue(value);
        node.setNeedFormat(false);
        argumentTree.addNode(node);
    }

    /**
     * method for set serviceCode
     */
    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
    }

    /**
     * method for set appName
     */
    public void setAppName(String appName) {
        this.appName = appName;
    }

}
