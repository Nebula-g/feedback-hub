#!/usr/bin/env bash
# ============================================================================
# 演示数据重置脚本
#
# 用途：把数据库恢复到"干净可演示"的初始状态
#   - 60 条脱敏演示反馈
#   - 3 个历史问题、4 个改进任务
#   - 0 张 AI 候选卡片
#
# 何时需要执行：
#   1. 跑完 scripts/test_robustness.py 之后（该脚本会写入批量测试数据）
#   2. 演示过程中把数据走乱了，需要回到起点
#   3. 答辩前最后一遍确认
#
# 用法：
#   bash scripts/reset_demo_data.sh
# ============================================================================

set -e

# 定位项目根目录（本脚本所在目录的上一级）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

MYSQL_BIN="${MYSQL_BIN:-E:/MySQL/mysql-8.0.46-winx64/bin/mysql.exe}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3308}"
DB_USER="${DB_USER:-root}"
# MySQL 口令不写死在脚本里（避免提交到代码库）：
# 优先读环境变量 DB_PASS；否则读 scripts/.db.local（本地私有文件，已被 gitignore）
DB_PASS="${DB_PASS:-}"
if [ -z "$DB_PASS" ] && [ -f "$SCRIPT_DIR/.db.local" ]; then
  # shellcheck disable=SC1091
  . "$SCRIPT_DIR/.db.local"   # 文件内容一行：DB_PASS=你的MySQL密码
fi
: "${DB_PASS:?未提供 MySQL 口令：请 export DB_PASS=... ，或在 scripts/.db.local 写入 DB_PASS=...}"
DB_NAME="${DB_NAME:-feedback_hub}"

echo "=============================================="
echo "重置演示数据"
echo "  数据库：${DB_NAME} @ ${DB_HOST}:${DB_PORT}"
echo "=============================================="

if [ ! -f "$ROOT_DIR/sql/init-data.sql" ]; then
    echo "[ERROR] 找不到 sql/init-data.sql"
    exit 1
fi

"$MYSQL_BIN" -u"$DB_USER" -p"$DB_PASS" -h"$DB_HOST" -P"$DB_PORT" \
    "$DB_NAME" < "$ROOT_DIR/sql/init-data.sql" 2>&1 \
    | grep -v "Using a password" || true

echo ""
echo "重置完成，当前数据统计："
"$MYSQL_BIN" -u"$DB_USER" -p"$DB_PASS" -h"$DB_HOST" -P"$DB_PORT" "$DB_NAME" -e "
SELECT
    (SELECT COUNT(*) FROM feedback)     AS '反馈总数',
    (SELECT COUNT(*) FROM feedback WHERE status='NEW') AS '待归类',
    (SELECT COUNT(*) FROM issue)        AS '问题数',
    (SELECT COUNT(*) FROM improvement_task) AS '任务数',
    (SELECT COUNT(*) FROM ai_candidate_issue) AS '候选卡片',
    (SELECT COUNT(*) FROM ai_generation_log)  AS 'AI日志';
" 2>&1 | grep -v "Using a password"

echo ""
echo "提示：后端无需重启，数据已生效。"
