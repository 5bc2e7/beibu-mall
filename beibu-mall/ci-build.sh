#!/bin/bash
# ============================================================================
# CI/CD 构建脚本 - 北部湾海鲜商城
# ============================================================================
# 用途：在 CI 环境中运行完整的构建、测试和覆盖率检查
# 使用方法：./ci-build.sh
# ============================================================================

set -e  # 遇到错误立即退出

echo "=========================================="
echo "北部湾海鲜商城 - CI 构建开始"
echo "=========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

# ============================================================================
# 1. 环境检查
# ============================================================================
echo -e "${YELLOW}[1/5] 检查环境...${NC}"

# 检查 Java 版本
java_version=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$java_version" -lt 21 ]; then
    echo -e "${RED}错误：需要 Java 21+，当前版本：$java_version${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Java 版本：$java_version${NC}"

# 检查 Maven 版本
mvn_version=$(mvn -version 2>&1 | head -1 | cut -d' ' -f3)
echo -e "${GREEN}✓ Maven 版本：$mvn_version${NC}"

# ============================================================================
# 2. 启动测试依赖服务（如果 Docker 可用）
# ============================================================================
echo -e "${YELLOW}[2/5] 检查测试依赖服务...${NC}"

if command -v docker &> /dev/null; then
    echo "Docker 可用，检查服务状态..."
    
    # 检查 MySQL
    if ! docker ps | grep -q mall-mysql; then
        echo "启动 MySQL..."
        docker run -d --name mall-mysql -p 3307:3306 \
            -e MYSQL_ROOT_PASSWORD=root123456 \
            mysql:8.0 --character-set-server=utf8mb4
        sleep 10
    fi
    
    # 检查 Redis
    if ! docker ps | grep -q mall-redis; then
        echo "启动 Redis..."
        docker run -d --name mall-redis -p 6379:6379 \
            redis:7-alpine redis-server --requirepass redis123456
    fi
    
    # 检查 Elasticsearch
    if ! docker ps | grep -q mall-elasticsearch; then
        echo "启动 Elasticsearch..."
        docker run -d --name mall-elasticsearch -p 9200:9200 \
            -e "discovery.type=single-node" \
            -e "xpack.security.enabled=true" \
            -e "ELASTIC_PASSWORD=elastic123456" \
            -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
            elasticsearch:8.12.0
    fi
    
    # 检查 RocketMQ Nameserver
    if ! docker ps | grep -q mall-rocketmq-namesrv; then
        echo "启动 RocketMQ Nameserver..."
        docker run -d --name mall-rocketmq-namesrv -p 9876:9876 \
            apache/rocketmq:5.3.1 sh mqnamesrv
        sleep 5
    fi
    
    # 检查 RocketMQ Broker
    if ! docker ps | grep -q mall-rocketmq-broker; then
        echo "启动 RocketMQ Broker..."
        docker run -d --name mall-rocketmq-broker -p 10911:10911 -p 10909:10909 \
            -e "NAMESRV_ADDR=mall-rocketmq-namesrv:9876" \
            apache/rocketmq:5.3.1 sh mqbroker
    fi

    # 检查 Nacos
    if ! docker ps | grep -q mall-nacos; then
        echo "启动 Nacos..."
        docker run -d --name mall-nacos --network host \
            -e MODE=standalone \
            -e NACOS_AUTH_ENABLE=false \
            nacos/nacos-server:v2.4.3
        echo "等待 Nacos 启动..."
        sleep 20
    fi
    
    echo -e "${GREEN}✓ 所有服务已启动${NC}"
else
    echo -e "${YELLOW}⚠ Docker 不可用，跳过服务启动${NC}"
    echo "请确保以下服务已运行："
    echo "  - MySQL: localhost:3307 (root/root123456)"
    echo "  - Redis: localhost:6379 (密码: redis123456)"
    echo "  - Elasticsearch: localhost:9200 (elastic/elastic123456)"
    echo "  - RocketMQ Nameserver: localhost:9876"
fi

# ============================================================================
# 3. 清理和编译
# ============================================================================
echo -e "${YELLOW}[3/5] 清理和编译...${NC}"
mvn clean compile -DskipTests
echo -e "${GREEN}✓ 编译成功${NC}"

# ============================================================================
# 4. 运行测试和覆盖率检查
# ============================================================================
echo -e "${YELLOW}[4/5] 运行测试和覆盖率检查...${NC}"
echo "门禁阈值：70% 行覆盖率"
echo "失败策略：haltOnFailure=true"

# 运行完整构建（测试 + 覆盖率检查）
mvn verify \
    -DskipTests=false \
    -Djacoco.coverage.minimum=0.70 \
    -Djacoco.haltOnFailure=true

echo -e "${GREEN}✓ 所有测试通过，覆盖率达标${NC}"

# ============================================================================
# 5. 生成报告
# ============================================================================
echo -e "${YELLOW}[5/5] 生成覆盖率报告...${NC}"

# 创建报告目录
mkdir -p target/coverage-reports

# 复制各模块的覆盖率报告
for module in mall-user-server mall-product-server mall-inventory-server \
              mall-cart-server mall-order-server mall-payment-server \
              mall-seckill-server mall-search-server; do
    if [ -d "$module/target/site/jacoco" ]; then
        cp -r "$module/target/site/jacoco" "target/coverage-reports/$module"
        echo -e "${GREEN}✓ $module 报告已生成${NC}"
    fi
done

echo ""
echo "=========================================="
echo -e "${GREEN}CI 构建完成！${NC}"
echo "=========================================="
echo ""
echo "覆盖率报告位置："
echo "  target/coverage-reports/"
echo ""
echo "各模块报告："
for dir in target/coverage-reports/*/; do
    if [ -d "$dir" ]; then
        module=$(basename "$dir")
        echo "  - $module: $dir/index.html"
    fi
done
echo ""
