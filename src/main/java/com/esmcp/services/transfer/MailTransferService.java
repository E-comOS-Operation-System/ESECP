package com.esmcp.services.transfer;

import com.esmcp.kernel.Service;
import com.esmcp.message.Message;
import com.esmcp.message.MessageType;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 邮件传输服务
 * 负责邮件的发送、队列管理和重试机制
 */
public class MailTransferService extends Service {
    private final Queue<TransferTask> sendQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, TransferStatus> statusMap = new ConcurrentHashMap<>();
    private final AtomicLong taskIdGenerator = new AtomicLong(1);
    private Thread workerThread;
    private volatile boolean processing = true;
    
    public MailTransferService() {
        super("MailTransferService");
    }
    
    @Override
    public void initialize() {
        // 启动发送工作线程
        workerThread = new Thread(this::processQueue, "MailTransferWorker");
        workerThread.start();
        logger.info("MailTransferService initialized");
    }
    
    @Override
    public Message handleMessage(Message message) {
        String type = message.getType();
        
        switch (type) {
            case MessageType.MAIL_SEND:
                return handleMailSend(message);
            case MessageType.MAIL_STATUS:
                return handleMailStatus(message);
            default:
                return createErrorResponse(message, "Unknown message type: " + type);
        }
    }
    
    private Message handleMailSend(Message message) {
        @SuppressWarnings("unchecked")
        Map<String, Object> mailData = (Map<String, Object>) message.getPayload();
        
        String taskId = String.valueOf(taskIdGenerator.getAndIncrement());
        TransferTask task = new TransferTask(
            taskId,
            (String) mailData.get("from"),
            (String) mailData.get("to"),
            (String) mailData.get("subject"),
            (String) mailData.get("body")
        );
        
        sendQueue.offer(task);
        statusMap.put(taskId, new TransferStatus(taskId, "QUEUED"));
        
        logger.info("Mail queued for transfer: {}", taskId);
        
        return Message.builder()
            .from(serviceName)
            .to(message.getFrom())
            .type(MessageType.MAIL_SEND)
            .payload(Map.of("success", true, "taskId", taskId, "status", "QUEUED"))
            .header("replyTo", message.getMessageId())
            .build();
    }
    
    private Message handleMailStatus(Message message) {
        String taskId = (String) message.getPayload();
        TransferStatus status = statusMap.get(taskId);
        
        if (status == null) {
            return createErrorResponse(message, "Task not found: " + taskId);
        }
        
        return Message.builder()
            .from(serviceName)
            .to(message.getFrom())
            .type(MessageType.MAIL_STATUS)
            .payload(Map.of(
                "taskId", status.taskId,
                "status", status.status,
                "attempts", status.attempts,
                "lastAttempt", status.lastAttempt
            ))
            .header("replyTo", message.getMessageId())
            .build();
    }
    
    private void processQueue() {
        while (processing) {
            try {
                TransferTask task = sendQueue.poll();
                if (task == null) {
                    Thread.sleep(100);
                    continue;
                }
                
                TransferStatus status = statusMap.get(task.taskId);
                if (status != null) {
                    status.status = "SENDING";
                    status.attempts++;
                    status.lastAttempt = System.currentTimeMillis();
                }
                
                // 模拟邮件发送
                boolean success = sendMail(task);
                
                if (status != null) {
                    status.status = success ? "SENT" : "FAILED";
                }
                
                logger.info("Mail transfer {}: {}", task.taskId, success ? "SUCCESS" : "FAILED");
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error processing transfer queue", e);
            }
        }
    }
    
    private boolean sendMail(TransferTask task) {
        try {
            // 模拟SMTP发送（实际应该连接SMTP服务器）
            Thread.sleep(100);
            logger.debug("Sending mail from {} to {}: {}", task.from, task.to, task.subject);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    @Override
    public void shutdown() {
        processing = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
        logger.info("MailTransferService shutdown, {} tasks in queue", sendQueue.size());
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
    
    private static class TransferTask {
        final String taskId;
        final String from;
        final String to;
        final String subject;
        final String body;
        
        TransferTask(String taskId, String from, String to, String subject, String body) {
            this.taskId = taskId;
            this.from = from;
            this.to = to;
            this.subject = subject;
            this.body = body;
        }
    }
    
    private static class TransferStatus {
        final String taskId;
        String status;
        int attempts;
        long lastAttempt;
        
        TransferStatus(String taskId, String status) {
            this.taskId = taskId;
            this.status = status;
            this.attempts = 0;
            this.lastAttempt = 0;
        }
    }
}
