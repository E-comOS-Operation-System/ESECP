package com.esmcp.services.storage;

import com.esmcp.kernel.Service;
import com.esmcp.message.Message;
import com.esmcp.message.MessageType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 邮件存储服务
 * 负责邮件的存储、检索和管理
 */
public class MailStorageService extends Service {
    private final Map<String, Mail> mailStore = new ConcurrentHashMap<>();
    private final Map<String, List<String>> userMailIndex = new ConcurrentHashMap<>();
    private final AtomicLong mailIdGenerator = new AtomicLong(1);
    
    public MailStorageService() {
        super("MailStorageService");
    }
    
    @Override
    public void initialize() {
        logger.info("MailStorageService initialized");
    }
    
    @Override
    public Message handleMessage(Message message) {
        String type = message.getType();
        
        switch (type) {
            case MessageType.MAIL_STORE:
                return handleMailStore(message);
            case MessageType.MAIL_RETRIEVE:
                return handleMailRetrieve(message);
            case MessageType.MAIL_LIST:
                return handleMailList(message);
            case MessageType.MAIL_DELETE:
                return handleMailDelete(message);
            case MessageType.MAIL_MARK:
                return handleMailMark(message);
            default:
                return createErrorResponse(message, "Unknown message type: " + type);
        }
    }
    
    private Message handleMailStore(Message message) {
        @SuppressWarnings("unchecked")
        Map<String, Object> mailData = (Map<String, Object>) message.getPayload();
        
        String mailId = String.valueOf(mailIdGenerator.getAndIncrement());
        Mail mail = new Mail(
            mailId,
            (String) mailData.get("from"),
            (String) mailData.get("to"),
            (String) mailData.get("subject"),
            (String) mailData.get("body"),
            System.currentTimeMillis()
        );
        
        mailStore.put(mailId, mail);
        
        // 索引到收件人
        userMailIndex.computeIfAbsent(mail.to, k -> new ArrayList<>()).add(mailId);
        
        logger.info("Mail stored: {} from {} to {}", mailId, mail.from, mail.to);
        
        return Message.builder()
            .from(serviceName)
            .to(message.getFrom())
            .type(MessageType.MAIL_STORE)
            .payload(Map.of("success", true, "mailId", mailId))
            .header("replyTo", message.getMessageId())
            .build();
    }
    
    private Message handleMailRetrieve(Message message) {
        String mailId = (String) message.getPayload();
        Mail mail = mailStore.get(mailId);
        
        if (mail == null) {
            return createErrorResponse(message, "Mail not found: " + mailId);
        }
        
        mail.read = true;
        
        Map<String, Object> mailData = Map.of(
            "mailId", mail.mailId,
            "from", mail.from,
            "to", mail.to,
            "subject", mail.subject,
            "body", mail.body,
            "timestamp", mail.timestamp,
            "read", mail.read
        );
        
        return Message.builder()
            .from(serviceName)
            .to(message.getFrom())
            .type(MessageType.MAIL_RETRIEVE)
            .payload(mailData)
            .header("replyTo", message.getMessageId())
            .build();
    }
    
    private Message handleMailList(Message message) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) message.getPayload();
        String username = (String) params.get("username");
        Integer limit = (Integer) params.getOrDefault("limit", 10);
        
        List<String> userMails = userMailIndex.getOrDefault(username, Collections.emptyList());
        
        List<Map<String, Object>> mailList = userMails.stream()
            .limit(limit)
            .map(mailStore::get)
            .filter(Objects::nonNull)
            .map(mail -> {
                Map<String, Object> mailInfo = new HashMap<>();
                mailInfo.put("mailId", mail.mailId);
                mailInfo.put("from", mail.from);
                mailInfo.put("subject", mail.subject);
                mailInfo.put("timestamp", mail.timestamp);
                mailInfo.put("read", mail.read);
                return mailInfo;
            })
            .collect(Collectors.toList());
        
        return Message.builder()
            .from(serviceName)
            .to(message.getFrom())
            .type(MessageType.MAIL_LIST)
            .payload(Map.of("mails", mailList, "total", userMails.size()))
            .header("replyTo", message.getMessageId())
            .build();
    }
    
    private Message handleMailDelete(Message message) {
        String mailId = (String) message.getPayload();
        Mail mail = mailStore.remove(mailId);
        
        if (mail != null) {
            List<String> userMails = userMailIndex.get(mail.to);
            if (userMails != null) {
                userMails.remove(mailId);
            }
            logger.info("Mail deleted: {}", mailId);
        }
        
        return Message.builder()
            .from(serviceName)
            .to(message.getFrom())
            .type(MessageType.MAIL_DELETE)
            .payload(Map.of("success", mail != null))
            .header("replyTo", message.getMessageId())
            .build();
    }
    
    private Message handleMailMark(Message message) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) message.getPayload();
        String mailId = (String) params.get("mailId");
        Boolean read = (Boolean) params.get("read");
        
        Mail mail = mailStore.get(mailId);
        if (mail != null) {
            mail.read = read;
        }
        
        return Message.builder()
            .from(serviceName)
            .to(message.getFrom())
            .type(MessageType.MAIL_MARK)
            .payload(Map.of("success", mail != null))
            .header("replyTo", message.getMessageId())
            .build();
    }
    
    @Override
    public void shutdown() {
        logger.info("MailStorageService shutdown, {} mails in store", mailStore.size());
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
    
    private static class Mail {
        final String mailId;
        final String from;
        final String to;
        final String subject;
        final String body;
        final long timestamp;
        boolean read;
        
        Mail(String mailId, String from, String to, String subject, String body, long timestamp) {
            this.mailId = mailId;
            this.from = from;
            this.to = to;
            this.subject = subject;
            this.body = body;
            this.timestamp = timestamp;
            this.read = false;
        }
    }
}
