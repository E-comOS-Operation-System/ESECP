package com.esmcp;

import com.esmcp.kernel.MessageBus;
import com.esmcp.kernel.Microkernel;
import com.esmcp.kernel.Service;
import com.esmcp.message.Message;
import com.esmcp.message.MessageType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * 微内核单元测试
 */
public class MicrokernelTest {
    private Microkernel kernel;
    
    @Before
    public void setUp() {
        kernel = new MessageBus();
        kernel.start();
    }
    
    @After
    public void tearDown() {
        kernel.shutdown();
    }
    
    @Test
    public void testServiceRegistration() {
        TestService service = new TestService("TestService");
        kernel.registerService("TestService", service);
        
        Service retrieved = kernel.getService("TestService");
        assertNotNull(retrieved);
        assertEquals("TestService", retrieved.getServiceName());
        assertTrue(service.isRunning());
    }
    
    @Test
    public void testMessagePassing() throws InterruptedException {
        EchoService echoService = new EchoService();
        kernel.registerService("EchoService", echoService);
        
        // 等待服务启动
        Thread.sleep(100);
        
        Message request = Message.builder()
            .from("TestClient")
            .to("EchoService")
            .type("ECHO")
            .payload("Hello World")
            .build();
        
        Message response = kernel.sendMessage(request);
        
        assertNotNull(response);
        assertEquals("EchoService", response.getFrom());
        assertEquals("Hello World", response.getPayload());
    }
    
    @Test
    public void testServiceCommunication() throws InterruptedException {
        kernel.registerService("ServiceA", new ServiceA());
        kernel.registerService("ServiceB", new ServiceB());
        
        Thread.sleep(100);
        
        // 直接测试ServiceB
        Message request = Message.builder()
            .from("TestClient")
            .to("ServiceB")
            .type("PROCESS")
            .payload("Test Message")
            .build();
        
        Message response = kernel.sendMessage(request);
        
        assertNotNull(response);
        assertEquals("ServiceB", response.getFrom());
        assertEquals("Processed: Test Message", response.getPayload());
    }
    
    // 测试服务
    private static class TestService extends Service {
        TestService(String name) {
            super(name);
        }
        
        @Override
        public void initialize() {}
        
        @Override
        public Message handleMessage(Message message) {
            return null;
        }
        
        @Override
        public void shutdown() {}
    }
    
    // Echo服务
    private static class EchoService extends Service {
        EchoService() {
            super("EchoService");
        }
        
        @Override
        public void initialize() {}
        
        @Override
        public Message handleMessage(Message message) {
            return Message.builder()
                .from(serviceName)
                .to(message.getFrom())
                .type(message.getType())
                .payload(message.getPayload())
                .header("replyTo", message.getMessageId())
                .build();
        }
        
        @Override
        public void shutdown() {}
    }
    
    // 服务A - 转发消息到服务B
    private static class ServiceA extends Service {
        ServiceA() {
            super("ServiceA");
        }
        
        @Override
        public void initialize() {}
        
        @Override
        public Message handleMessage(Message message) {
            try {
                Message forward = Message.builder()
                    .from(serviceName)
                    .to("ServiceB")
                    .type("PROCESS")
                    .payload(message.getPayload())
                    .build();
                
                return sendMessage(forward);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        
        @Override
        public void shutdown() {}
    }
    
    // 服务B - 处理消息
    private static class ServiceB extends Service {
        ServiceB() {
            super("ServiceB");
        }
        
        @Override
        public void initialize() {}
        
        @Override
        public Message handleMessage(Message message) {
            String payload = "Processed: " + message.getPayload();
            return Message.builder()
                .from(serviceName)
                .to(message.getFrom())
                .type("RESULT")
                .payload(payload)
                .header("replyTo", message.getMessageId())
                .build();
        }
        
        @Override
        public void shutdown() {}
    }
}
