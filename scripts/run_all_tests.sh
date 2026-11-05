#!/usr/bin/env bash
# ============================================================================
#  一键运行全部测试
#
#  为什么要每个套件前都重置数据：
#    这几套测试都会改动数据库（录入反馈、生成候选卡片、推进任务），
#    而 AI 冒烟测试依赖"反馈处于未归类（NEW）"这个前提。
#    如果先跑主链路冒烟把反馈关联掉了，AI 测试就会取不到待处理数据而误报失败。
#    所以每个套件独立重置，保证结果可复现。
#
#  用法：
#    bash scripts/run_all_tests.sh
#
#  前置条件：后端已启动在 http://127.0.0.1:8081
# ============================================================================

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$ROOT_DIR" || exit 1

# Python 解释器（优先用托管版本）
PYTHON_BIN="${PYTHON_BIN:-C:/Users/Nebula/.workbuddy/binaries/python/versions/3.13.12/python.exe}"
if [ ! -f "$PYTHON_BIN" ]; then
    PYTHON_BIN="python"
fi

PASS_TOTAL=0
FAIL_TOTAL=0
SUITE_RESULTS=()

run_suite() {
    local name="$1"
    local script="$2"

    echo ""
    echo "────────────────────────────────────────────────────────"
    echo "▶ 重置数据后运行：$name"
    echo "────────────────────────────────────────────────────────"
    bash scripts/reset_demo_data.sh > /dev/null 2>&1

    local out
    out=$("$PYTHON_BIN" "scripts/$script" 2>&1)
    echo "$out" | tail -30

    # 抓取汇总行「通过 N 项，失败 M 项」
    local line
    line=$(echo "$out" | grep -oE "通过 [0-9]+ 项，失败 [0-9]+ 项" | tail -1)
    if [ -n "$line" ]; then
        local p f
        p=$(echo "$line" | grep -oE "通过 [0-9]+" | grep -oE "[0-9]+")
        f=$(echo "$line" | grep -oE "失败 [0-9]+" | grep -oE "[0-9]+")
        PASS_TOTAL=$((PASS_TOTAL + p))
        FAIL_TOTAL=$((FAIL_TOTAL + f))
        SUITE_RESULTS+=("$name : 通过 $p / 失败 $f")
    else
        SUITE_RESULTS+=("$name : 未识别到汇总行")
    fi
}

echo "=========================================================="
echo "  反馈改进助手 —— 全部测试套件"
echo "  目标服务：http://127.0.0.1:8081"
echo "=========================================================="

run_suite "主链路冒烟"      "smoke_test.py"
run_suite "AI 模块冒烟"    "smoke_test_ai.py"
run_suite "契约三方核对"    "contract_check.py"
run_suite "健壮性测试"      "test_robustness.py"

# 收尾：恢复干净演示数据
echo ""
echo "────────────────────────────────────────────────────────"
echo "▶ 全部测试结束，恢复演示数据"
echo "────────────────────────────────────────────────────────"
bash scripts/reset_demo_data.sh 2>&1 | tail -6

echo ""
echo "=========================================================="
echo "  汇总"
echo "=========================================================="
for r in "${SUITE_RESULTS[@]}"; do
    echo "  $r"
done
echo "  ----------------------------------------------"
echo "  合计：通过 $PASS_TOTAL 项，失败 $FAIL_TOTAL 项"
echo "=========================================================="

if [ "$FAIL_TOTAL" -gt 0 ]; then
    exit 1
fi
exit 0
