#!/usr/bin/env bash
# ============================================================================
# 后端开发启动脚本
#
# 为什么需要它：
#   某些环境（含部分 IDE 插件、容器、CI）会注入 SERVER__PORT / SERVER__HOST
#   这类环境变量。Spring Boot 的宽松绑定会把它们映射成 server.port，
#   导致应用忽略 application.yml 里配置的 8081 端口，随机换一个端口启动。
#   本脚本在启动前清除这些变量，确保端口与配置一致。
#
# 用法：
#   bash run.sh
# ============================================================================

cd "$(dirname "${BASH_SOURCE[0]}")" || exit 1

# 清除可能覆盖 server.port / server.address 的环境变量
unset SERVER__PORT SERVER__HOST SERVER_PORT

# 强制指定端口，双保险（-D 参数优先级高于配置文件）
exec bash mvn.sh -o spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8081"
