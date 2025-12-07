package com.esmcp.services.auth;

import com.esmcp.kernel.Service;
import com.esmcp.message.Message;
import com.esmcp.message.MessageType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务
 * 负责用户认证、会话管理和权限验证
 */
public class AuthenticationService extends Service {
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    
    public AuthenticationService() {
        super("AuthenticationService");
    }
    
    @Override
    public void initialize() {
        // 初始化测试用户
        users.put("admin", new User("admin", "admin123", "ADMIN"));
        users.put("user1", new User("user1", "pass123", "USER"));
        users.put("user2", new User("user2", "pass456", "USER"));
        logger.info("AuthenticationService initialized with {} users", users.size());
    }
    
    @Override
    public Message handleMessage(Message message) {
        String type = message.getType();
        
        switch (type) {
            case MessageType.AUTH_REQUEST:
                return handleAuthRequest(message);
            case MessageType.SESSION_VALIDATE:
                return handleSessionValidate(message);
            default:
                return createErrorResponse(message, "Unknown message type: " + type);
        }
    }
    
    private Message handleAuthRequest(Message message) {
        @SuppressWarnings("unchecked")
        Map<String, String> credentials = (Map<String, String>) message.getPayload();
        String username = credentials.get("username");
        String password = credentials.get("password");
        
        User user = users.get(username);
        boolean authenticated = user != null && user.password.equals(password);
        
        if (authenticated) {
            String sessionId = UUID.randomUUID().toString();
            Session session = new Session(sessionId, username, user.role);
            sessions.put(sessionId, session);
            
            logger.info("User {} authenticated successfully", username);
            
            Map<String, Object> response = Map.of(
                "success", true,
                "sessionId", sessionId,
                "username", username,
                "role", user.role
            );
            
            return Message.builder()
                .from(serviceName)
                .to(message.getFrom())
                .type(MessageType.AUTH_RESPONSE)
                .payload(response)
                .header("replyTo", message.getMessageId())
                .build();
        } else {
            logger.warn("Authentication failed for user: {}", username);
            
            return Message.builder()
                .from(serviceName)
                .to(message.getFrom())
                .type(MessageType.AUTH_RESPONSE)
                .payload(Map.of("success", false, "error", "Invalid credentials"))
                .header("replyTo", message.getMessageId())
                .build();
        }
    }
    
    private Message handleSessionValidate(Message message) {
        String sessionId = (String) message.getPayload();
        Session session = sessions.get(sessionId);
        
        boolean valid = session != null && !session.isExpired();
        
        Map<String, Object> response = valid 
            ? Map.of("valid", true, "username", session.username, "role", session.role)
            : Map.of("valid", false);
        
        return Message.builder()
            .from(serviceName)
            .to(message.getFrom())
            .type(MessageType.AUTH_RESPONSE)
            .payload(response)
            .header("replyTo", message.getMessageId())
            .build();
    }
    
    @Override
    public void shutdown() {
        sessions.clear();
        logger.info("AuthenticationService shutdown");
    }
    
    private Message createErrorResponse(Message request, String error) {
        return Message.builder()
            .from(serviceName)
            .to(request.getFrom())
            .type(MessageType.ERROR)
            .payload(error)
            .header("replyTo", request.getMessageId())
            .build();
    }
    
    private static class User {
        final String username;
        final String password;
        final String role;
        
        User(String username, String password, String role) {
            this.username = username;
            this.password = password;
            this.role = role;
        }
    }
    
    private static class Session {
        final String sessionId;
        final String username;
        final String role;
        final long createdAt;
        final long expiresAt;
        
        Session(String sessionId, String username, String role) {
            this.sessionId = sessionId;
            this.username = username;
            this.role = role;
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = createdAt + 3600000; // 1小时
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
