#!/bin/bash

echo "=========================================="
echo "  ESMCP Server Startup Script"
echo "=========================================="
echo ""

# 检查Maven是否安装
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed"
    exit 1
fi

# 编译项目
echo "Compiling project..."
mvn clean compile

if [ $? -ne 0 ]; then
    echo "Error: Compilation failed"
    exit 1
fi

echo ""
echo "Starting ESMCP Server..."
echo "Server will listen on port 2525"
echo "Press Ctrl+C to stop"
echo ""

# 启动服务器
mvn exec:java -Dexec.mainClass="com.esmcp.main.ServerMain"
