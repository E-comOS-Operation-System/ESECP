package com.esmcp.message;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微内核消息系统的核心消息类
 * 用于服务间的异步通信
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String messageId;
    private final String from;
    private final String to;
    private final String type;
    private final Object payload;
    private final long timestamp;
    private final Map<String, Object> headers;
    
    private Message(Builder builder) {
        this.messageId = builder.messageId;
        this.from = builder.from;
        this.to = builder.to;
        this.type = builder.type;
        this.payload = builder.payload;
        this.timestamp = builder.timestamp;
        this.headers = builder.headers;
    }
    
    public String getMessageId() { return messageId; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getType() { return type; }
    public Object getPayload() { return payload; }
    public long getTimestamp() { return timestamp; }
    public Map<String, Object> getHeaders() { return headers; }
    
    public Object getHeader(String key) {
        return headers.get(key);
    }
    
    @Override
    public String toString() {
        return String.format("Message[id=%s, from=%s, to=%s, type=%s]", 
            messageId, from, to, type);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String messageId = UUID.randomUUID().toString();
        private String from;
        private String to;
        private String type;
        private Object payload;
        private long timestamp = System.currentTimeMillis();
        private Map<String, Object> headers = new HashMap<>();
        
        public Builder from(String from) {
            this.from = from;
            return this;
        }
        
        public Builder to(String to) {
            this.to = to;
            return this;
        }
        
        public Builder type(String type) {
            this.type = type;
            return this;
        }
        
        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }
        
        public Builder header(String key, Object value) {
            this.headers.put(key, value);
            return this;
        }
        
        public Message build() {
            return new Message(this);
        }
    }
}
