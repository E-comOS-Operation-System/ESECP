package com.esmcp.kernel;

import com.esmcp.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务基类
 * 所有微内核服务都继承此类
 */
public abstract class Service implements Runnable {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final String serviceName;
    protected Microkernel kernel;
    
    private final BlockingQueue<Message> inbox = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread serviceThread;
    
    protected Service(String serviceName) {
        this.serviceName = serviceName;
    }
    
    /**
     * 服务初始化
     */
    public abstract void initialize();
    
    /**
     * 处理接收到的消息
     */
    public abstract Message handleMessage(Message message);
    
    /**
     * 服务关闭
     */
    public abstract void shutdown();
    
    /**
     * 设置微内核引用
     */
    public void setKernel(Microkernel kernel) {
        this.kernel = kernel;
    }
    
    /**
     * 接收消息（由微内核调用）
     */
    public void receive(Message message) {
        inbox.offer(message);
    }
    
    /**
     * 启动服务
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            initialize();
            serviceThread = new Thread(this, serviceName);
            serviceThread.start();
            logger.info("Service {} started", serviceName);
        }
    }
    
    /**
     * 停止服务
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            shutdown();
            if (serviceThread != null) {
                serviceThread.interrupt();
            }
            logger.info("Service {} stopped", serviceName);
        }
    }
    
    /**
     * 服务运行循环
     */
    @Override
    public void run() {
        while (running.get()) {
            try {
                Message message = inbox.take();
                Message response = handleMessage(message);
                
                if (response != null && kernel instanceof MessageBus) {
                    // 直接完成请求，避免递归
                    String replyTo = (String) response.getHeader("replyTo");
                    if (replyTo != null) {
                        ((MessageBus) kernel).completeRequest(replyTo, response);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error processing message in service {}", serviceName, e);
            }
        }
    }
    
    /**
     * 发送消息到其他服务
     */
    protected Message sendMessage(Message message) throws InterruptedException {
        if (kernel != null) {
            return kernel.sendMessage(message);
        }
        return null;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public boolean isRunning() {
        return running.get();
    }
}
