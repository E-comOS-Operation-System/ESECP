package com.esmcp.main;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * ESMCP测试客户端
 * 用于测试服务器功能
 */
public class TestClient {
    private static final String HOST = "localhost";
    private static final int PORT = 2525;
    
    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            
            System.out.println("Connected to ESMCP Server");
            
            // 读取欢迎消息
            String welcome = in.readLine();
            System.out.println("Server: " + welcome);
            
            // 演示完整流程
            demonstrateWorkflow(in, out);
            
            // 交互模式
            System.out.println("\nEntering interactive mode. Type 'QUIT' to exit.");
            String command;
            while ((command = console.readLine()) != null) {
                out.println(command);
                String response = readResponse(in);
                System.out.println("Server: " + response);
                
                if (command.trim().equalsIgnoreCase("QUIT")) {
                    break;
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void demonstrateWorkflow(BufferedReader in, PrintWriter out) throws Exception {
        System.out.println("\n=== Demonstrating ESMCP Workflow ===\n");
        
        // 1. 认证
        System.out.println("1. Authenticating as user1...");
        out.println("AUTH user1 pass123");
        System.out.println("Server: " + readResponse(in));
        
        // 2. 发送邮件
        System.out.println("\n2. Sending mail to user2...");
        out.println("SEND user2 Hello This_is_a_test_message");
        System.out.println("Server: " + readResponse(in));
        
        // 3. 列出邮件
        System.out.println("\n3. Listing mails...");
        out.println("LIST 5");
        System.out.println("Server: " + readResponse(in));
        
        System.out.println("\n=== Workflow Complete ===\n");
    }
    
    private static String readResponse(BufferedReader in) throws Exception {
        StringBuilder response = new StringBuilder();
        String line = in.readLine();
        
        if (line == null) {
            return "";
        }
        
        response.append(line);
        
        // 处理多行响应
        if (line.startsWith("250-")) {
            while ((line = in.readLine()) != null) {
                response.append("\n").append(line);
                if (line.startsWith("250 ")) {
                    break;
                }
            }
        }
        
        return response.toString();
    }
}
