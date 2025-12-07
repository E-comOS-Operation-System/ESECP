# ESMCP 快速入门指南

## 5分钟快速体验

### 步骤1：编译项目
```bash
cd /Users/ddd/ESECP
mvn clean compile
```

### 步骤2：运行测试（可选）
```bash
mvn test
```

你应该看到：
```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

### 步骤3：启动服务器
```bash
./start-server.sh
```

或者：
```bash
mvn exec:java -Dexec.mainClass="com.esmcp.main.ServerMain"
```

看到以下输出表示启动成功：
```
INFO  c.e.main.ServerMain - ESMCP Server is ready on port 2525
INFO  c.e.main.ServerMain - Test users: admin/admin123, user1/pass123, user2/pass456
```

### 步骤4：测试连接（新终端）

#### 方式A：使用测试客户端
```bash
./start-client.sh
```

#### 方式B：使用telnet
```bash
telnet localhost 2525
```

然后输入命令：
```
AUTH user1 pass123
SEND user2 Hello This_is_my_first_message
LIST 10
QUIT
```

## 完整示例

### 场景：user1给user2发送邮件

**终端1 - 启动服务器：**
```bash
./start-server.sh
```

**终端2 - user1发送邮件：**
```bash
telnet localhost 2525
```
```
220 ESMCP Server Ready
AUTH user1 pass123
250 Authentication successful
SEND user2 Meeting Lets_meet_at_3pm_today
250 Mail accepted, ID: 1
SEND user2 Documents Please_review_the_attached_files
250 Mail accepted, ID: 2
QUIT
221 Goodbye
```

**终端3 - user2接收邮件：**
```bash
telnet localhost 2525
```
```
220 ESMCP Server Ready
AUTH user2 pass456
250 Authentication successful
LIST 10
250-2 messages
250-ID:1 FROM:user1 SUBJ:Meeting
250-ID:2 FROM:user1 SUBJ:Documents
250 End of list
RETR 1
250 FROM:user1 TO:user2 SUBJ:Meeting BODY:Lets_meet_at_3pm_today
DELE 1
250 Mail deleted
QUIT
221 Goodbye
```

## 架构验证

### 验证微内核设计

1. **查看核心代码行数：**
```bash
wc -l src/main/java/com/esmcp/kernel/*.java
```
应该看到总行数 < 500行

2. **验证服务隔离：**
启动服务器后，查看线程：
```bash
jps -l  # 找到ServerMain的PID
jstack <PID> | grep "Service"
```
你会看到每个服务都有独立的线程。

3. **测试消息延迟：**
```bash
mvn test -Dtest=MicrokernelTest#testMessagePassing
```
查看日志中的时间戳，验证消息延迟 < 10ms

### 验证服务独立性

尝试在一个服务中抛出异常，其他服务应该继续正常工作。

## 常见问题

### Q: 端口2525已被占用？
A: 修改ServerMain.java中的DEFAULT_PORT，或者：
```bash
lsof -i :2525
kill -9 <PID>
```

### Q: 如何添加新用户？
A: 在AuthenticationService.java的initialize()方法中添加：
```java
users.put("newuser", new User("newuser", "password", "USER"));
```

### Q: 如何持久化邮件？
A: 创建一个PersistenceService，在MailStorageService中发送PERSIST消息。

### Q: 如何支持更多协议命令？
A: 在ProtocolHandlerService的processCommand()方法中添加新的case分支。

## 下一步

- 阅读 [README.md](README.md) 了解完整功能
- 阅读 [ARCHITECTURE.md](ARCHITECTURE.md) 理解架构设计
- 阅读 [EXAMPLES.md](EXAMPLES.md) 查看更多示例
- 尝试添加自己的服务（参考EXAMPLES.md第5节）

## 性能测试

### 测试并发连接
```bash
# 编译测试类
javac -cp target/classes examples/ConcurrencyTest.java

# 运行100个并发客户端
java -cp target/classes:examples ConcurrencyTest
```

### 测试消息延迟
```bash
mvn test -Dtest=MicrokernelTest
```
查看输出中的平均延迟时间。

## 调试技巧

### 启用DEBUG日志
修改 `src/main/resources/logback.xml`：
```xml
<root level="DEBUG">
    <appender-ref ref="STDOUT" />
</root>
```

### 查看消息流
在MessageBus.java中添加日志：
```java
logger.debug("Routing message: {} from {} to {}", 
    message.getMessageId(), message.getFrom(), message.getTo());
```

### 监控服务状态
添加一个MonitorService来收集统计信息：
- 消息数量
- 平均延迟
- 服务状态

## 总结

恭喜！你已经成功运行了一个微内核架构的邮件服务器。

核心特性：
- ✅ 微内核架构（核心 < 500行）
- ✅ 服务隔离（独立线程）
- ✅ 异步消息（< 10ms延迟）
- ✅ 易于扩展（添加服务无需改核心）

现在你可以：
1. 扩展新服务（垃圾邮件过滤、病毒扫描等）
2. 添加持久化（数据库、文件系统）
3. 支持更多协议（IMAP、POP3等）
4. 实现分布式部署

享受微内核架构的乐趣！🚀
