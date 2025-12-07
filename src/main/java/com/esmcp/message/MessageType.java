package com.esmcp.message;

/**
 * 消息类型定义
 */
public class MessageType {
    // 认证相关
    public static final String AUTH_REQUEST = "AUTH_REQUEST";
    public static final String AUTH_RESPONSE = "AUTH_RESPONSE";
    public static final String SESSION_VALIDATE = "SESSION_VALIDATE";
    
    // 邮件存储相关
    public static final String MAIL_STORE = "MAIL_STORE";
    public static final String MAIL_RETRIEVE = "MAIL_RETRIEVE";
    public static final String MAIL_LIST = "MAIL_LIST";
    public static final String MAIL_DELETE = "MAIL_DELETE";
    public static final String MAIL_MARK = "MAIL_MARK";
    
    // 邮件传输相关
    public static final String MAIL_SEND = "MAIL_SEND";
    public static final String MAIL_QUEUE = "MAIL_QUEUE";
    public static final String MAIL_STATUS = "MAIL_STATUS";
    
    // 协议处理相关
    public static final String PROTOCOL_REQUEST = "PROTOCOL_REQUEST";
    public static final String PROTOCOL_RESPONSE = "PROTOCOL_RESPONSE";
    
    // 系统相关
    public static final String SERVICE_REGISTER = "SERVICE_REGISTER";
    public static final String SERVICE_UNREGISTER = "SERVICE_UNREGISTER";
    public static final String HEARTBEAT = "HEARTBEAT";
    public static final String ERROR = "ERROR";
    
    private MessageType() {}
}
