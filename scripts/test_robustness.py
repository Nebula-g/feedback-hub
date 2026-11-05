#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
阶段 5-C / 5-D：健壮性测试脚本（异常 / 边界 / 并发 / 越权）
=========================================================

本脚本对应验收要求中最能拉开差距的四类测试：

  【A】异常测试 —— 输入不合法时系统是否"明确拒绝"而不是崩溃或静默接受
  【B】边界测试 —— 极端取值下系统行为是否可控
  【C】并发测试 —— 两人同时操作同一条数据时是否只有一个成功
  【D】越权测试 —— 绕过页面直接调接口，是否还能被拦住

与 smoke_test*.py 的区别：
  smoke 验证"正常情况能不能跑通"，本脚本验证"不正常情况会不会崩"。

  ⚠️ 本脚本会往数据库写入测试数据（批量导入 200 条 + 若干临时反馈）。
     跑完后请执行一次 scripts/reset_demo_data.sh 恢复演示数据。

用法
----
    python scripts/test_robustness.py

前置条件：后端已启动在 http://127.0.0.1:8081
"""

import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8081"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ---------------------------------------------------------------- 统计
TOTAL = 0
FAILED = 0
WARNED = 0
ISSUES = []


def ok(msg):
    global TOTAL
    TOTAL += 1
    print("  [PASS] " + msg)


def fail(msg, detail=""):
    global TOTAL, FAILED
    TOTAL += 1
    FAILED += 1
    line = msg + ("  <- " + str(detail) if detail else "")
    ISSUES.append(line)
    print("  [FAIL] " + line)


def warn(msg, detail=""):
    """非致命问题：不影响功能但值得改进"""
    global TOTAL, WARNED
    TOTAL += 1
    WARNED += 1
    line = msg + ("  <- " + str(detail) if detail else "")
    ISSUES.append("[建议] " + line)
    print("  [WARN] " + line)


def section(title):
    print("\n" + "=" * 64)
    print(title)
    print("=" * 64)


# ---------------------------------------------------------------- HTTP
def http(method, path, token=None, body=None, timeout=30):
    """返回 (status, json或None, raw)"""
    url = BASE + path
    data = None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            try:
                return resp.status, json.loads(raw), raw
            except ValueError:
                return resp.status, None, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(raw), raw
        except ValueError:
            return e.code, None, raw
    except Exception as e:
        return 0, None, str(e)


def login(username, password):
    _, body, raw = http("POST", "/api/auth/login",
                        body={"username": username, "password": password})
    if body and body.get("code") == 200 and body.get("data"):
        return body["data"].get("token")
    print("  [ERROR] 登录失败 %s: %s" % (username, raw[:200]))
    return None


# ================================================================
# 【A】异常测试
# ================================================================
def test_exception(tokens):
    section("[A] 异常测试：非法输入必须被明确拒绝")

    cs, pm, dev = tokens["cs"], tokens["pm"], tokens["dev"]

    # A1 空数据：查询不存在的资源
    _, body, _ = http("GET", "/api/feedbacks/999999", token=pm)
    if body and body.get("code") == 404:
        ok("查询不存在的反馈返回 404")
    else:
        fail("查询不存在的反馈应返回 404", body.get("code") if body else "无响应")

    _, body, _ = http("GET", "/api/issues/999999", token=pm)
    if body and body.get("code") == 404:
        ok("查询不存在的问题返回 404")
    else:
        fail("查询不存在的问题应返回 404", body.get("code") if body else "无响应")

    _, body, _ = http("GET", "/api/tasks/999999", token=pm)
    if body and body.get("code") == 404:
        ok("查询不存在的任务返回 404")
    else:
        fail("查询不存在的任务应返回 404", body.get("code") if body else "无响应")

    # A2 无效输入：非法状态值应被拒绝，不能静默写入
    _, body, _ = http("PUT", "/api/feedbacks/1/status", token=pm,
                      body={"status": "NOT_A_STATUS", "remark": "测试"})
    if body and body.get("code") in (400, 409):
        ok("非法状态值被拒绝（%s）" % body.get("code"))
    else:
        fail("非法状态值应返回 400/409", body.get("code") if body else "无响应")

    # A3 必填缺失：反馈内容为空
    _, body, _ = http("POST", "/api/feedbacks", token=cs,
                      body={"content": "", "module": "INVOICE_UPLOAD",
                            "source": "TICKET"})
    if body and body.get("code") == 400:
        ok("反馈内容为空被拒绝（400）")
    else:
        fail("反馈内容为空应返回 400", body.get("code") if body else "无响应")

    # A4 必填缺失：登录不填密码
    _, body, _ = http("POST", "/api/auth/login", body={"username": "pm01"})
    if body and body.get("code") == 400:
        ok("登录缺密码被拒绝（400）")
    else:
        fail("登录缺密码应返回 400", body.get("code") if body else "无响应")

    # A5 必填缺失：任务缺定级理由（优先级接口强制要求理由）
    _, body, _ = http("POST", "/api/tasks", token=pm,
                      body={"issueId": 1, "title": "测试任务", "priority": "P1"})
    if body and body.get("code") == 400:
        ok("创建任务缺定级理由被拒绝（400）")
    else:
        fail("创建任务缺定级理由应返回 400", body.get("code") if body else "无响应")

    # A6 空批次导入
    _, body, _ = http("POST", "/api/feedbacks/batch", token=cs,
                      body={"items": []})
    if body and body.get("code") == 400:
        ok("空批次导入被拒绝（400）")
    else:
        fail("空批次导入应返回 400", body.get("code") if body else "无响应")


# ================================================================
# 【B】边界测试
# ================================================================
def test_boundary(tokens):
    section("[B] 边界测试：极端取值下行为可控")

    cs, pm, dev = tokens["cs"], tokens["pm"], tokens["dev"]

    # B1 超长文本：超过 2000 字上限应被拒绝（而不是截断后静默入库）
    long_content = "长" * 5000
    _, body, _ = http("POST", "/api/feedbacks", token=cs,
                      body={"content": long_content,
                            "module": "INVOICE_UPLOAD", "source": "TICKET"})
    if body and body.get("code") == 400:
        ok("超长反馈（5000字）被拒绝（400）")
    else:
        fail("超长反馈应返回 400", body.get("code") if body else "无响应")

    # B2 边界值：恰好 2000 字应通过
    exact_content = "长" * 2000
    _, body, _ = http("POST", "/api/feedbacks", token=cs,
                      body={"content": exact_content,
                            "module": "INVOICE_UPLOAD", "source": "TICKET"})
    if body and body.get("code") == 200:
        ok("恰好 2000 字的反馈可以正常录入")
    else:
        fail("2000 字边界值应能通过", body.get("code") if body else "无响应")

    # B3 0 条关联反馈：创建问题时不关联任何反馈
    _, body, _ = http("POST", "/api/issues", token=pm,
                      body={"title": "边界测试-无关联反馈的问题",
                            "module": "OTHER",
                            "finalContent": "验证 0 条关联反馈时可以正常创建"})
    if body and body.get("code") == 200 and body.get("data"):
        ok("0 条关联反馈时仍可创建问题")
        empty_issue_id = body["data"].get("id")
        # 回查确认 feedbackIds 为空
        _, d, _ = http("GET", "/api/issues/" + str(empty_issue_id), token=pm)
        ids = (d or {}).get("data", {}).get("feedbackIds") or []
        if len(ids) == 0:
            ok("回查确认该问题关联反馈数为 0")
        else:
            fail("该问题应无关联反馈", ids)
    else:
        fail("0 条关联反馈时创建问题应成功", body.get("code") if body else "无响应")

    # B4 批量导入上限：201 条应被拒绝
    items_201 = [{"content": "批量测试%d" % i, "module": "OTHER",
                  "source": "TICKET"} for i in range(201)]
    _, body, _ = http("POST", "/api/feedbacks/batch", token=cs,
                      body={"items": items_201})
    if body and body.get("code") == 400:
        ok("批量导入 201 条被拒绝（400，符合 200 条上限约束）")
    else:
        fail("批量导入 201 条应返回 400", body.get("code") if body else "无响应")

    # B5 批量导入 200 条（上限内）应成功，并记录耗时
    items_200 = [{"content": "批量边界测试%d" % i, "module": "OTHER",
                  "source": "TICKET"} for i in range(200)]
    t0 = time.time()
    st, body, raw = http("POST", "/api/feedbacks/batch", token=cs,
                         body={"items": items_200}, timeout=120)
    cost = time.time() - t0
    if body and body.get("code") == 200:
        ok("批量导入 200 条成功，耗时 %.2fs" % cost)
        if cost > 30:
            warn("批量导入 200 条耗时 %.2fs 偏长，建议改为批量 insert" % cost)
    else:
        fail("批量导入 200 条应成功", (body.get("code") if body else raw[:120]))


# ================================================================
# 【C】并发测试
# ================================================================
def test_concurrent(tokens):
    section("[C] 并发测试：同一数据只能被一人操作成功")

    pm, dev = tokens["pm"], tokens["dev"]

    # C1 两个 PM 会话同时确认同一张候选卡片
    #     先确保有一张 DRAFT 卡片
    _, body, _ = http("POST", "/api/ai/candidates/generate", token=pm,
                      body={"module": "APPROVAL_PROGRESS"})
    cards = ((body or {}).get("data") or {}).get("candidates") or []
    if not cards:
        print("  [SKIP] 没有可供并发测试的候选卡片")
    else:
        cid = cards[0]["id"]
        results = []
        lock = threading.Lock()

        def do_confirm():
            st, b, _ = http("POST", "/api/ai/candidates/%d/confirm" % cid,
                            token=pm, body={"title": "并发确认测试",
                                            "description": "并发",
                                            "finalContent": "并发",
                                            "acceptanceCriteria": ""})
            with lock:
                results.append(st)

        barrier = threading.Barrier(2)
        threads = []

        def run_with_barrier():
            barrier.wait()  # 两个线程在此对齐，尽量同时发出请求
            do_confirm()

        for _ in range(2):
            t = threading.Thread(target=run_with_barrier)
            threads.append(t)
            t.start()
        for t in threads:
            t.join()

        codes = sorted(results)
        if codes == [200, 409]:
            ok("并发确认同一候选卡片：一个成功(200)，一个版本冲突(409)")
        elif 409 in codes:
            ok("并发确认检测到冲突，返回码 %s" % codes)
        else:
            fail("并发确认未检测到冲突，返回码 %s" % codes)

    # C2 两个 DEV 会话同时推进同一任务状态
    _, body, _ = http("POST", "/api/tasks", token=pm,
                      body={"issueId": 1,
                            "title": "并发测试任务",
                            "requirement": "验证并发流转",
                            "priority": "P1",
                            "priorityReason": "并发测试"})
    task_id = ((body or {}).get("data") or {}).get("id")
    if not task_id:
        print("  [SKIP] 无法创建并发测试任务")
        return

    # 先推进到 IN_PROGRESS，为后续并发留出目标状态
    http("PUT", "/api/tasks/%d/status" % task_id, token=dev,
         body={"status": "IN_PROGRESS", "remark": ""})

    results2 = []
    lock2 = threading.Lock()

    def do_transit():
        st, b, _ = http("PUT", "/api/tasks/%d/status" % task_id,
                        token=dev,
                        body={"status": "PENDING_VERIFY", "remark": ""})
        with lock2:
            results2.append(st)

    barrier2 = threading.Barrier(2)
    threads2 = []

    def run_with_barrier2():
        barrier2.wait()
        do_transit()

    for _ in range(2):
        t = threading.Thread(target=run_with_barrier2)
        threads2.append(t)
        t.start()
    for t in threads2:
        t.join()

    codes2 = sorted(results2)
    if codes2 == [200, 409]:
        ok("并发推进同一任务状态：一个成功(200)，一个版本冲突(409)")
    elif 409 in codes2:
        ok("并发流转检测到冲突，返回码 %s" % codes2)
    else:
        fail("并发流转未检测到冲突，返回码 %s" % codes2)


# ================================================================
# 【D】越权测试
# ================================================================
def test_authz(tokens):
    section("[D] 越权测试：绕过页面直调接口也必须被拦住")

    cs, pm, dev, admin = tokens["cs"], tokens["pm"], tokens["dev"], tokens["admin"]

    # D1 CS 调建任务接口（R1：仅 PM 可建任务）
    _, body, _ = http("POST", "/api/tasks", token=cs,
                      body={"issueId": 1, "title": "越权测试",
                            "priority": "P1", "priorityReason": "测试"})
    if body and body.get("code") == 403:
        ok("客服创建任务被拒绝（403）")
    else:
        fail("客服创建任务应返回 403", body.get("code") if body else "无响应")

    # D2 DEV 调改优先级接口（R1：优先级由 PM 定）
    _, body, _ = http("PUT", "/api/tasks/1/priority", token=dev,
                      body={"priority": "P0", "priorityReason": "越权测试"})
    if body and body.get("code") == 403:
        ok("开发修改优先级被拒绝（403）")
    else:
        fail("开发修改优先级应返回 403", body.get("code") if body else "无响应")

    # D3 DEV 调建任务接口
    _, body, _ = http("POST", "/api/tasks", token=dev,
                      body={"issueId": 1, "title": "越权测试",
                            "priority": "P1", "priorityReason": "测试"})
    if body and body.get("code") == 403:
        ok("开发创建任务被拒绝（403）")
    else:
        fail("开发创建任务应返回 403", body.get("code") if body else "无响应")

    # D4 CS 调确认候选卡片接口（仅 PM）
    _, body, _ = http("POST", "/api/ai/candidates/1/confirm", token=cs,
                      body={"title": "x", "description": "", "finalContent": ""})
    if body and body.get("code") == 403:
        ok("客服确认候选卡片被拒绝（403）")
    else:
        fail("客服确认候选卡片应返回 403", body.get("code") if body else "无响应")

    # D5 CS 调变更反馈状态接口（仅 PM/ADMIN）
    _, body, _ = http("PUT", "/api/feedbacks/1/status", token=cs,
                      body={"status": "IGNORED", "remark": "越权测试"})
    if body and body.get("code") == 403:
        ok("客服变更反馈状态被拒绝（403）")
    else:
        fail("客服变更反馈状态应返回 403", body.get("code") if body else "无响应")

    # D6 CS 调 AI 运行状态接口（仅 ADMIN）
    _, body, _ = http("GET", "/api/ai/status", token=cs)
    if body and body.get("code") == 403:
        ok("客服查看 AI 运行状态被拒绝（403）")
    else:
        fail("客服查看 AI 运行状态应返回 403", body.get("code") if body else "无响应")

    # D7 PM 调 AI 运行状态接口（仅 ADMIN）
    _, body, _ = http("GET", "/api/ai/status", token=pm)
    if body and body.get("code") == 403:
        ok("产品经理查看 AI 运行状态被拒绝（403）")
    else:
        fail("产品经理查看 AI 运行状态应返回 403", body.get("code") if body else "无响应")

    # D8 未认证访问受保护接口
    _, body, _ = http("GET", "/api/feedbacks")
    if body and body.get("code") == 401:
        ok("未携带 Token 访问被拒绝（401）")
    else:
        fail("未携带 Token 应返回 401", body.get("code") if body else "无响应")

    # D9 伪造 Token
    _, body, _ = http("GET", "/api/feedbacks", token="fake.token.value")
    if body and body.get("code") in (401, 403):
        ok("伪造 Token 被拒绝（%s）" % body.get("code"))
    else:
        fail("伪造 Token 应返回 401/403", body.get("code") if body else "无响应")


# ================================================================
# 入口
# ================================================================
def main():
    print("=" * 64)
    print("阶段 5-C / 5-D：健壮性测试（异常 / 边界 / 并发 / 越权）")
    print("目标服务：" + BASE)
    print("=" * 64)

    tokens = {}
    for who, u, p in [("pm", "pm01", "pm123456"),
                      ("cs", "cs01", "cs123456"),
                      ("dev", "dev01", "dev123456"),
                      ("admin", "admin", "admin123456")]:
        t = login(u, p)
        if not t:
            print("[FATAL] 账号 %s 登录失败" % u)
            return 1
        tokens[who] = t
    print("\n四个演示账号均登录成功")

    test_exception(tokens)
    test_boundary(tokens)
    test_concurrent(tokens)
    test_authz(tokens)

    print("\n" + "=" * 64)
    print("测试结果：通过 %d 项，失败 %d 项，建议 %d 项"
          % (TOTAL - FAILED - WARNED, FAILED, WARNED))
    print("=" * 64)
    if ISSUES:
        print("\n问题清单：")
        for i, it in enumerate(ISSUES, 1):
            print("  %2d. %s" % (i, it))
        return 1
    print("\n四类健壮性测试全部通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
