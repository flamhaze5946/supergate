package com.bestv.supergate.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.bestv.flame.client.handler.AbstractClientHandler;
import com.bestv.flame.client.handler.softrouter.RouterServer;
import com.bestv.flame.client.handler.softrouter.strategy.RouterStrategy;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.AbstractNioChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求发送工具
 * Created by flamhaze on 16/8/5.
 */
public class RequestSenderHandler extends AbstractClientHandler<JSONObject, JSONObject, Channel>
{

    /** 连接器 */
    private Bootstrap bootstrap;

    /** rpc请求暂存器 */
    private ConcurrentHashMap<String, JSONObject> rpcRequestMap;

    /** rpc请求结果暂存器 */
    private ConcurrentHashMap<String, JSONObject> rpcResultMap;

    /** 用于访问封装的channel远程请求地址 */
    private Field requestedRemoteAddrField;

    /** request本体, 带占位符 */
    private String requestGen;

    /** 请求id键 */
    private static final String TRACE_ID_KEY = "traceId";


    /**
     * 构造函数
     *
     * @param appName          调用系统名
     * @param serviceInterface 服务接口
     * @param serviceHost      调用系统主机名
     * @param servicePort      系统服务端口号
     * @param keepAlive        是否长连接
     * @param softRouter       是否走软负载
     * @param routerServer     软负载处理器
     * @param routerStrategy   软负载策略
     */
    public RequestSenderHandler(String appName, String serviceInterface, String serviceHost, int servicePort,
                                boolean keepAlive,
                                boolean softRouter, RouterServer routerServer, RouterStrategy routerStrategy) {
        super(appName, serviceInterface, serviceHost, servicePort,
                keepAlive,
                softRouter, routerServer, routerStrategy);
        rpcRequestMap = new ConcurrentHashMap<String, JSONObject>();
        rpcResultMap = new ConcurrentHashMap<String, JSONObject>();

        try
        {
            requestedRemoteAddrField = AbstractNioChannel.class.getDeclaredField("requestedRemoteAddress");
            requestedRemoteAddrField.setAccessible(true);
        }
        catch (NoSuchFieldException e)
        {
            throw new RuntimeException("requestedRemoteAddress属性获取失败, 可能是netty版本不对", e);
        }
    }

    /**
     * @see AbstractClientHandler#buildTraceId()
     */
    protected String buildTraceId()
    {
        return null;
    }

    /**
     * @see AbstractClientHandler#init()
     */
    protected void init() {

        bootstrap = new Bootstrap();

        bootstrap.group(new NioEventLoopGroup());                  //group 组
        bootstrap.channel(NioSocketChannel.class);                 //channel 通道
        bootstrap.option(ChannelOption.TCP_NODELAY, true);         //option 选项
        bootstrap.handler(new ClientChildHandler(this));           //handler 处理
    }

    /**
     * @see AbstractClientHandler#afterResponse()
     */
    protected void afterResponse() {

    }

    /**
     * @see AbstractClientHandler#isConnectionAlive(Object)
     */
    protected boolean isConnectionAlive(Channel connection)
    {
        return connection.isActive();
    }

    /**
     * @see AbstractClientHandler#releaseConnection(Object)
     */
    protected boolean releaseConnection(Channel connection)
    {
        try
        {
            releaseChannel(connection);
            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * @see AbstractClientHandler#connect(String, int)
     */
    protected Channel connect(String serviceHost, int servicePort) {

        //发起连接
        ChannelFuture channelFuture = bootstrap.connect(serviceHost, servicePort);

        if (!channelFuture.awaitUninterruptibly().isSuccess())
        {
            throw new RuntimeException("与 " + serviceHost + ":" + servicePort + "连接失败!");
        }

        return channelFuture.channel();
    }


    /**
     * @see AbstractClientHandler#sendRequest(Object, Object)
     */
    protected void sendRequest(JSONObject rpcRequest, Channel channel) {

        rpcRequest.put(TRACE_ID_KEY, UUID.randomUUID().toString());

        channel.writeAndFlush(JSON.toJSONString(rpcRequest));

        if (rpcRequestMap.putIfAbsent((String) rpcRequest.get(TRACE_ID_KEY), rpcRequest) != null)
        {
            throw new RuntimeException("rpc请求暂存失败");
        }

        synchronized (rpcRequest)
        {
            try
            {
                rpcRequest.wait(3000);
                if (rpcResultMap.get(rpcRequest.get(TRACE_ID_KEY)) == null)
                {
                    throw new RuntimeException("没有收到回复, 快速失败");
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
                releaseConnection(channel);
            }
        }
    }

    /**
     * @see AbstractClientHandler#receiveResponse(Object)
     */
    protected JSONObject receiveResponse(JSONObject rpcRequest) throws Exception {

        JSONObject rpcResult = rpcResultMap.get(rpcRequest.get(TRACE_ID_KEY));
        rpcResultMap.remove(rpcRequest.get(TRACE_ID_KEY));
        rpcRequestMap.remove(rpcRequest.get(TRACE_ID_KEY));
        return rpcResult;
    }

    /**
     * 填充rpc请求结果
     * @param rpcResult rpc请求结果
     */
    public void fillRpcResult(JSONObject rpcResult)
    {
        if (rpcResultMap.putIfAbsent((String) rpcResult.get(TRACE_ID_KEY), rpcResult) != null)
        {
            throw new RuntimeException("rpc请求结果暂存失败");
        }

        JSONObject rpcRequest = rpcRequestMap.get(rpcResult.get(TRACE_ID_KEY));

        while (rpcRequest == null)
        {
            rpcRequest = rpcRequestMap.get(rpcResult.get(TRACE_ID_KEY));
        }

        synchronized (rpcRequest)
        {
            rpcRequest.notify();
        }
    }


    /**
     * @see AbstractClientHandler#buildRpcRequest(String, String, String, Object[])
     */
    @Override
    protected JSONObject buildRpcRequest(String traceId, String serviceInterface, String methodName, Object[] args) {

        return JSON.parseObject(MessageFormat.format(requestGen, args), Feature.DisableSpecialKeyDetect, Feature.OrderedField);
    }

    /**
     * method for get requestGen
     */
    public String getRequestGen() {
        return requestGen;
    }

    /**
     * method for set requestGen
     */
    public void setRequestGen(String requestGen) {
        this.requestGen = requestGen;
    }


    @Override
    public Object doAction(String methodName, Object[] args) throws Exception {

        Object response = super.doAction(methodName, args);
        return response;
    }

    /**
     * 释放失效通道
     * @param channel 失效通道
     */
    private void releaseChannel(Channel channel) throws IllegalAccessException
    {
        String remoteAddress = getRemoteAddr(channel);
        channel.close();
        withReleaseConnection(new ConnectionWrapper<Channel>(remoteAddress, channel));
    }

    /**
     * 通过channel获取远程地址
     * @param channel channel
     * @return 远程地址
     */
    private String getRemoteAddr(Channel channel) throws IllegalAccessException
    {
        return requestedRemoteAddrField.get(channel).toString();
    }
}


/**
 * 客户端执行总线
 * Created by flamhaze on 16/6/29.
 */
class ClientChildHandler extends ChannelInitializer<SocketChannel> {

    /** RPC动态代理执行器 */
    private RequestSenderHandler requestSenderHandler;

    /**
     * 构造函数
     * @param requestSenderHandler RPC动态代理执行器
     */
    public ClientChildHandler(RequestSenderHandler requestSenderHandler) {
        this.requestSenderHandler = requestSenderHandler;
    }

    /**
     * @see ChannelInitializer#initChannel(Channel)
     */
    @Override
    protected void initChannel(SocketChannel e) throws Exception {

        e.pipeline()

                // 半粘包解决方案
                .addLast(new LengthFieldPrepender(4, false))

                .addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4))

                .addLast(new StringEncoder())

                .addLast(new StringDecoder())

                // 客户端主要逻辑
                .addLast(new MyClientHandler(requestSenderHandler))
        ;
    }


}


/**
 * 客户端主要逻辑
 */
class MyClientHandler extends ChannelInboundHandlerAdapter {

    /** rpc动态代理执行器 */
    private RequestSenderHandler requestSenderHandler;

    /**
     * 构造函数
     * @param requestSenderHandler RPC动态代理执行器
     */
    public MyClientHandler(RequestSenderHandler requestSenderHandler) {
        this.requestSenderHandler = requestSenderHandler;
    }


    /**
     * @see ChannelInboundHandlerAdapter#channelRead(ChannelHandlerContext, Object)
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        requestSenderHandler.fillRpcResult(JSON.parseObject((String) msg, Feature.DisableSpecialKeyDetect));
    }

    /**
     * @see ChannelInboundHandlerAdapter#channelReadComplete(ChannelHandlerContext)
     */
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {

        ctx.flush();
    }

    /**
     * @see ChannelInboundHandlerAdapter#exceptionCaught(ChannelHandlerContext, Throwable)
     */
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        ctx.close();
        System.out.println("异常信息：\r\n" + cause.getMessage());
    }
}