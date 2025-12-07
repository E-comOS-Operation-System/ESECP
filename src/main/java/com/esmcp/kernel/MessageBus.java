package com.esmcp.kernel;

import com.esmcp.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 消息总线实现
 * 核心的微内核实现，负责消息路由和服务管理
 */
public class MessageBus implements Microkernel {
    private static final Logger logger = LoggerFactory.getLogger(MessageBus.class);
    
    private final Map<String, Service> services = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Message>> pendingRequests = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running = false;
    
    @Override
    public void registerService(String serviceName, Service service) {
        service.setKernel(this);
        services.put(serviceName, service);
        service.start();
        logger.info("Service registered: {}", serviceName);
    }
    
    @Override
    public void unregisterService(String serviceName) {
        Service service = services.remove(serviceName);
        if (service != null) {
            service.stop();
            logger.info("Service unregistered: {}", serviceName);
        }
    }
    
    @Override
    public Message sendMessage(Message message) throws InterruptedException {
        if (!running) {
            throw new IllegalStateException("MessageBus is not running");
        }
        
        String to = message.getTo();
        Service targetService = services.get(to);
        
        if (targetService == null) {
            logger.warn("Target service not found: {}", to);
            return createErrorResponse(message, "Service not found: " + to);
        }
        
        // 异步发送消息
        CompletableFuture<Message> future = new CompletableFuture<>();
        pendingRequests.put(message.getMessageId(), future);
        
        executor.submit(() -> {
            try {
                targetService.receive(message);
            } catch (Exception e) {
                logger.error("Error delivering message to {}", to, e);
                future.completeExceptionally(e);
            }
        });
        
        try {
            // 等待响应（超时10秒）
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingRequests.remove(message.getMessageId());
            logger.warn("Message timeout: {}", message.getMessageId());
            return createErrorResponse(message, "Request timeout");
        } catch (ExecutionException e) {
            pendingRequests.remove(message.getMessageId());
            logger.error("Message execution error", e);
            return createErrorResponse(message, "Execution error: " + e.getMessage());
        } finally {
            pendingRequests.remove(message.getMessageId());
        }
    }
    
    @Override
    public void broadcastMessage(Message message) {
        for (Service service : services.values()) {
            executor.submit(() -> service.receive(message));
        }
    }
    
    @Override
    public Service getService(String serviceName) {
        return services.get(serviceName);
    }
    
    @Override
    public void start() {
        running = true;
        logger.info("MessageBus started");
    }
    
    @Override
    public void shutdown() {
        running = false;
        
        // 停止所有服务
        for (Service service : services.values()) {
            service.stop();
        }
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("MessageBus shutdown");
    }
    
    /**
     * 完成待处理的请求（由服务调用）
     */
    public void completeRequest(String messageId, Message response) {
        CompletableFuture<Message> future = pendingRequests.remove(messageId);
        if (future != null) {
            future.complete(response);
        }
    }
    
    private Message createErrorResponse(Message request, String error) {
        return Message.builder()
            .from("MessageBus")
            .to(request.getFrom())
            .type("ERROR")
            .payload(error)
            .header("originalMessageId", request.getMessageId())
            .build();
    }
}
