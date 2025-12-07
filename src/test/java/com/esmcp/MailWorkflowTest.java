package com.esmcp;

import com.esmcp.kernel.MessageBus;
import com.esmcp.kernel.Microkernel;
import com.esmcp.message.Message;
import com.esmcp.message.MessageType;
import com.esmcp.services.auth.AuthenticationService;
import com.esmcp.services.storage.MailStorageService;
import com.esmcp.services.transfer.MailTransferService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 邮件工作流集成测试
 */
public class MailWorkflowTest {
    private Microkernel kernel;
    
    @Before
    public void setUp() {
        kernel = new MessageBus();
        kernel.start();
        
        kernel.registerService("AuthenticationService", new AuthenticationService());
        kernel.registerService("MailStorageService", new MailStorageService());
        kernel.registerService("MailTransferService", new MailTransferService());
        
        // 等待服务启动
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @After
    public void tearDown() {
        kernel.shutdown();
    }
    
    @Test
    public void testAuthentication() throws InterruptedException {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "user1");
        credentials.put("password", "pass123");
        
        Message request = Message.builder()
            .from("TestClient")
            .to("AuthenticationService")
            .type(MessageType.AUTH_REQUEST)
            .payload(credentials)
            .build();
        
        Message response = kernel.sendMessage(request);
        
        assertNotNull(response);
        assertEquals(MessageType.AUTH_RESPONSE, response.getType());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getPayload();
        assertTrue((Boolean) result.get("success"));
        assertNotNull(result.get("sessionId"));
    }
    
    @Test
    public void testMailSendAndRetrieve() throws InterruptedException {
        // 发送邮件
        Map<String, Object> mailData = new HashMap<>();
        mailData.put("from", "user1");
        mailData.put("to", "user2");
        mailData.put("subject", "Test Subject");
        mailData.put("body", "Test Body");
        
        Message storeRequest = Message.builder()
            .from("TestClient")
            .to("MailStorageService")
            .type(MessageType.MAIL_STORE)
            .payload(mailData)
            .build();
        
        Message storeResponse = kernel.sendMessage(storeRequest);
        
        assertNotNull(storeResponse);
        @SuppressWarnings("unchecked")
        Map<String, Object> storeResult = (Map<String, Object>) storeResponse.getPayload();
        assertTrue((Boolean) storeResult.get("success"));
        String mailId = (String) storeResult.get("mailId");
        assertNotNull(mailId);
        
        // 检索邮件
        Message retrieveRequest = Message.builder()
            .from("TestClient")
            .to("MailStorageService")
            .type(MessageType.MAIL_RETRIEVE)
            .payload(mailId)
            .build();
        
        Message retrieveResponse = kernel.sendMessage(retrieveRequest);
        
        assertNotNull(retrieveResponse);
        @SuppressWarnings("unchecked")
        Map<String, Object> mail = (Map<String, Object>) retrieveResponse.getPayload();
        assertEquals("user1", mail.get("from"));
        assertEquals("user2", mail.get("to"));
        assertEquals("Test Subject", mail.get("subject"));
        assertEquals("Test Body", mail.get("body"));
    }
    
    @Test
    public void testMailList() throws InterruptedException {
        // 发送多封邮件
        for (int i = 0; i < 3; i++) {
            Map<String, Object> mailData = new HashMap<>();
            mailData.put("from", "user1");
            mailData.put("to", "user2");
            mailData.put("subject", "Subject " + i);
            mailData.put("body", "Body " + i);
            
            Message request = Message.builder()
                .from("TestClient")
                .to("MailStorageService")
                .type(MessageType.MAIL_STORE)
                .payload(mailData)
                .build();
            
            kernel.sendMessage(request);
        }
        
        // 列出邮件
        Map<String, Object> params = new HashMap<>();
        params.put("username", "user2");
        params.put("limit", 10);
        
        Message listRequest = Message.builder()
            .from("TestClient")
            .to("MailStorageService")
            .type(MessageType.MAIL_LIST)
            .payload(params)
            .build();
        
        Message listResponse = kernel.sendMessage(listRequest);
        
        assertNotNull(listResponse);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) listResponse.getPayload();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mails = (List<Map<String, Object>>) result.get("mails");
        
        assertEquals(3, mails.size());
    }
    
    @Test
    public void testMailTransfer() throws InterruptedException {
        Map<String, Object> mailData = new HashMap<>();
        mailData.put("from", "user1");
        mailData.put("to", "external@example.com");
        mailData.put("subject", "External Mail");
        mailData.put("body", "Test external delivery");
        
        Message request = Message.builder()
            .from("TestClient")
            .to("MailTransferService")
            .type(MessageType.MAIL_SEND)
            .payload(mailData)
            .build();
        
        Message response = kernel.sendMessage(request);
        
        assertNotNull(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getPayload();
        assertTrue((Boolean) result.get("success"));
        assertNotNull(result.get("taskId"));
        assertEquals("QUEUED", result.get("status"));
    }
}
