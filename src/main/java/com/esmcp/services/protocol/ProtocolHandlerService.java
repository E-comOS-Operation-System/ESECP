package com.esmcp.services.protocol;

import com.esmcp.kernel.Service;
import com.esmcp.message.Message;
import com.esmcp.message.MessageType;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 协议处理服务
 * 实现ESMCP协议，处理客户端连接和命令
 */
public class ProtocolHandlerService extends Service {
    private static final int DEFAULT_PORT = 2525;
    private ServerSocket serverSocket;
    private ExecutorService clientExecutor;
    private volatile boolean accepting = true;
    
    public ProtocolHandlerService() {
        super("ProtocolHandlerService");
    }
    
    @Override
    public void initialize() {
        try {
            serverSocket = new ServerSocket(DEFAULT_PORT);
            clientExecutor = Executors.newCachedThreadPool();
            
            // 启动接受连接的线程
            new Thread(this::acceptConnections, "ProtocolAcceptor").start();
            
            logger.info("ProtocolHandlerService listening on port {}", DEFAULT_PORT);
        } catch (IOException e) {
            logger.error("Failed to start protocol handler", e);
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public Message handleMessage(Message message) {
        // 此服务主要处理TCP连接，不处理内部消息
        return null;
    }
    
    private void acceptConnections() {
        while (accepting) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("Client connected: {}", clientSocket.getRemoteSocketAddress());
                clientExecutor.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (accepting) {
                    logger.error("Error accepting connection", e);
                }
            }
        }
    }
    
    private void handleClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            ClientSession session = new ClientSession();
            out.println("220 ESMCP Server Ready");
            
            String line;
            while ((line = in.readLine()) != null) {
                String response = processCommand(line.trim(), session);
                out.println(response);
                
                if (session.quit) {
                    break;
                }
            }
            
        } catch (Exception e) {
            logger.error("Error handling client", e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                logger.error("Error closing socket", e);
            }
        }
    }
    
    private String processCommand(String command, ClientSession session) {
        if (command.isEmpty()) {
            return "500 Empty command";
        }
        
        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].toUpperCase();
        String args = parts.length > 1 ? parts[1] : "";
        
        try {
            switch (cmd) {
                case "AUTH":
                    return handleAuth(args, session);
                case "SEND":
                    return handleSend(args, session);
                case "LIST":
                    return handleList(args, session);
                case "RETR":
                    return handleRetr(args, session);
                case "DELE":
                    return handleDele(args, session);
                case "QUIT":
                    session.quit = true;
                    return "221 Goodbye";
                default:
                    return "500 Unknown command: " + cmd;
            }
        } catch (Exception e) {
            logger.error("Error processing command: {}", command, e);
            return "550 Command failed: " + e.getMessage();
        }
    }
    
    private String handleAuth(String args, ClientSession session) throws InterruptedException {
        String[] parts = args.split("\\s+");
        if (parts.length < 2) {
            return "501 Syntax: AUTH <username> <password>";
        }
        
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", parts[0]);
        credentials.put("password", parts[1]);
        
        Message request = Message.builder()
            .from(serviceName)
            .to("AuthenticationService")
            .type(MessageType.AUTH_REQUEST)
            .payload(credentials)
            .build();
        
        Message response = sendMessage(request);
        
        if (response != null && response.getType().equals(MessageType.AUTH_RESPONSE)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            Boolean success = (Boolean) result.get("success");
            
            if (Boolean.TRUE.equals(success)) {
                session.authenticated = true;
                session.username = parts[0];
                session.sessionId = (String) result.get("sessionId");
                return "250 Authentication successful";
            } else {
                return "535 Authentication failed";
            }
        }
        
        return "451 Authentication service unavailable";
    }
    
    private String handleSend(String args, ClientSession session) throws InterruptedException {
        if (!session.authenticated) {
            return "530 Authentication required";
        }
        
        // 格式: SEND <to> <subject> <body>
        String[] parts = args.split("\\s+", 3);
        if (parts.length < 3) {
            return "501 Syntax: SEND <to> <subject> <body>";
        }
        
        Map<String, Object> mailData = new HashMap<>();
        mailData.put("from", session.username);
        mailData.put("to", parts[0]);
        mailData.put("subject", parts[1]);
        mailData.put("body", parts[2]);
        
        // 存储邮件
        Message storeRequest = Message.builder()
            .from(serviceName)
            .to("MailStorageService")
            .type(MessageType.MAIL_STORE)
            .payload(mailData)
            .build();
        
        Message storeResponse = sendMessage(storeRequest);
        
        if (storeResponse != null && storeResponse.getType().equals(MessageType.MAIL_STORE)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) storeResponse.getPayload();
            String mailId = (String) result.get("mailId");
            
            // 发送邮件（异步）
            Message sendRequest = Message.builder()
                .from(serviceName)
                .to("MailTransferService")
                .type(MessageType.MAIL_SEND)
                .payload(mailData)
                .build();
            
            sendMessage(sendRequest);
            
            return "250 Mail accepted, ID: " + mailId;
        }
        
        return "451 Mail storage failed";
    }
    
    private String handleList(String args, ClientSession session) throws InterruptedException {
        if (!session.authenticated) {
            return "530 Authentication required";
        }
        
        int limit = 10;
        if (!args.isEmpty()) {
            try {
                limit = Integer.parseInt(args);
            } catch (NumberFormatException e) {
                return "501 Invalid limit";
            }
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put("username", session.username);
        params.put("limit", limit);
        
        Message request = Message.builder()
            .from(serviceName)
            .to("MailStorageService")
            .type(MessageType.MAIL_LIST)
            .payload(params)
            .build();
        
        Message response = sendMessage(request);
        
        if (response != null && response.getType().equals(MessageType.MAIL_LIST)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> mails = (List<Map<String, Object>>) result.get("mails");
            
            StringBuilder sb = new StringBuilder();
            sb.append("250-").append(mails.size()).append(" messages\r\n");
            
            for (Map<String, Object> mail : mails) {
                sb.append("250-ID:").append(mail.get("mailId"))
                  .append(" FROM:").append(mail.get("from"))
                  .append(" SUBJ:").append(mail.get("subject"))
                  .append("\r\n");
            }
            
            sb.append("250 End of list");
            return sb.toString();
        }
        
        return "451 List failed";
    }
    
    private String handleRetr(String args, ClientSession session) throws InterruptedException {
        if (!session.authenticated) {
            return "530 Authentication required";
        }
        
        if (args.isEmpty()) {
            return "501 Syntax: RETR <mail_id>";
        }
        
        Message request = Message.builder()
            .from(serviceName)
            .to("MailStorageService")
            .type(MessageType.MAIL_RETRIEVE)
            .payload(args)
            .build();
        
        Message response = sendMessage(request);
        
        if (response != null && response.getType().equals(MessageType.MAIL_RETRIEVE)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mail = (Map<String, Object>) response.getPayload();
            
            return String.format("250 FROM:%s TO:%s SUBJ:%s BODY:%s",
                mail.get("from"), mail.get("to"), mail.get("subject"), mail.get("body"));
        }
        
        return "550 Mail not found";
    }
    
    private String handleDele(String args, ClientSession session) throws InterruptedException {
        if (!session.authenticated) {
            return "530 Authentication required";
        }
        
        if (args.isEmpty()) {
            return "501 Syntax: DELE <mail_id>";
        }
        
        Message request = Message.builder()
            .from(serviceName)
            .to("MailStorageService")
            .type(MessageType.MAIL_DELETE)
            .payload(args)
            .build();
        
        Message response = sendMessage(request);
        
        if (response != null && response.getType().equals(MessageType.MAIL_DELETE)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            Boolean success = (Boolean) result.get("success");
            
            if (Boolean.TRUE.equals(success)) {
                return "250 Mail deleted";
            }
        }
        
        return "550 Delete failed";
    }
    
    @Override
    public void shutdown() {
        accepting = false;
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing server socket", e);
        }
        
        if (clientExecutor != null) {
            clientExecutor.shutdown();
        }
        
        logger.info("ProtocolHandlerService shutdown");
    }
    
    private static class ClientSession {
        boolean authenticated = false;
        String username;
        String sessionId;
        boolean quit = false;
    }
}
