#!/bin/bash
# ============================================
# Free MQTT Platform - Docker 快速启动脚本
# ============================================

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}  Free MQTT Platform - Docker 部署   ${NC}"
echo -e "${GREEN}======================================${NC}"

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: Docker 未安装，请先安装 Docker${NC}"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}错误: docker-compose 未安装，请先安装 docker-compose${NC}"
    exit 1
fi

case "$1" in
    start)
        echo -e "${YELLOW}启动服务...${NC}"
        docker-compose up -d --build
        echo -e "${GREEN}服务启动完成！${NC}"
        echo ""
        echo "服务地址："
        echo "  - MQTT Broker: tcp://localhost:23242"
        echo "  - HTTP API:    http://localhost:23240"
        echo "  - Route API:   http://localhost:8084"
        echo "  - Redis:       localhost:6379"
        echo "  - ZooKeeper:   localhost:2181"
        ;;
    stop)
        echo -e "${YELLOW}停止服务...${NC}"
        docker-compose down
        echo -e "${GREEN}服务已停止${NC}"
        ;;
    restart)
        echo -e "${YELLOW}重启服务...${NC}"
        docker-compose down
        docker-compose up -d --build
        echo -e "${GREEN}服务重启完成${NC}"
        ;;
    logs)
        docker-compose logs -f ${2:-}
        ;;
    status)
        docker-compose ps
        ;;
    clean)
        echo -e "${RED}警告: 这将删除所有数据卷！${NC}"
        read -p "确定要继续吗? (y/N) " confirm
        if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
            docker-compose down -v
            echo -e "${GREEN}清理完成${NC}"
        fi
        ;;
    *)
        echo "用法: $0 {start|stop|restart|logs|status|clean}"
        echo ""
        echo "命令说明："
        echo "  start   - 构建并启动所有服务"
        echo "  stop    - 停止所有服务"
        echo "  restart - 重启所有服务"
        echo "  logs    - 查看日志 (可指定服务名)"
        echo "  status  - 查看服务状态"
        echo "  clean   - 停止服务并删除数据卷"
        echo ""
        echo "示例："
        echo "  $0 start"
        echo "  $0 logs mqtt-server"
        exit 1
        ;;
esac
