#!/bin/bash

echo "=========================================="
echo "  ESMCP Test Client"
echo "=========================================="
echo ""

# 检查Maven是否安装
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed"
    exit 1
fi

echo "Connecting to ESMCP Server at localhost:2525..."
echo ""

# 启动测试客户端
mvn exec:java -Dexec.mainClass="com.esmcp.main.TestClient"
