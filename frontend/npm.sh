#!/usr/bin/env bash
# ============================================================
# npm 包装脚本
#
# 为什么需要这个脚本：
# 本机 PATH 里的 node 是 v24（系统安装），而项目锁定 v22（隔离安装）。
# 直接用 `npm install` 会跑到系统 node 上，版本不一致容易出现
# 依赖构建的原生模块不匹配问题。
#
# 本脚本做的事：
#   1. 把隔离版 node 目录临时插到 PATH 最前面
#   2. 转发所有参数给真正的 npm
#
# 用法：bash npm.sh install / bash npm.sh run dev
# ============================================================

set -euo pipefail

# 隔离版 Node 安装目录（managed runtime）
NODE_HOME="C:/Users/Nebula/.workbuddy/binaries/node/versions/22.22.2"

# 插到 PATH 最前，优先级高于系统 node
export PATH="$NODE_HOME:$PATH"

# 国内网络走淘宝镜像，安装更快（如已配置则保留原值）
export npm_config_registry="${npm_config_registry:-https://registry.npmmirror.com}"

# 转发全部参数
exec "$NODE_HOME/node.exe" "$NODE_HOME/node_modules/npm/bin/npm-cli.js" "$@"
