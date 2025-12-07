package com.esmcp.kernel;

import com.esmcp.message.Message;

/**
 * 微内核接口
 * 提供服务注册、消息路由等核心功能
 */
public interface Microkernel {
    /**
     * 注册服务
     */
    void registerService(String serviceName, Service service);
    
    /**
     * 注销服务
     */
    void unregisterService(String serviceName);
    
    /**
     * 发送消息（同步）
     */
    Message sendMessage(Message message) throws InterruptedException;
    
    /**
     * 广播消息
     */
    void broadcastMessage(Message message);
    
    /**
     * 获取服务
     */
    Service getService(String serviceName);
    
    /**
     * 启动微内核
     */
    void start();
    
    /**
     * 关闭微内核
     */
    void shutdown();
}
