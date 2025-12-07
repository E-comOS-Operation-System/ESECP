package com.esmcp.main;

import com.esmcp.kernel.MessageBus;
import com.esmcp.kernel.Microkernel;
import com.esmcp.services.auth.AuthenticationService;
import com.esmcp.services.protocol.ProtocolHandlerService;
import com.esmcp.services.storage.MailStorageService;
import com.esmcp.services.transfer.MailTransferService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ESMCP服务器主启动类
 * 演示微内核架构的启动和服务注册
 */
public class ServerMain {
    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);
    
    public static void main(String[] args) {
        logger.info("Starting ESMCP Server...");
        
        // 1. 创建微内核
        Microkernel kernel = new MessageBus();
        kernel.start();
        logger.info("Microkernel started");
        
        // 2. 注册基础服务
        kernel.registerService("AuthenticationService", new AuthenticationService());
        kernel.registerService("MailStorageService", new MailStorageService());
        kernel.registerService("MailTransferService", new MailTransferService());
        kernel.registerService("ProtocolHandlerService", new ProtocolHandlerService());
        
        logger.info("All services registered and started");
        logger.info("ESMCP Server is ready on port 2525");
        logger.info("Test users: admin/admin123, user1/pass123, user2/pass456");
        
        // 3. 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down ESMCP Server...");
            kernel.shutdown();
            logger.info("ESMCP Server stopped");
        }));
        
        // 保持主线程运行
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
