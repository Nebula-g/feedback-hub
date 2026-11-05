# -*- coding: utf-8 -*-
"""
阶段 4 · AI 模块冒烟测试脚本

用途：
    验证「AI 做归纳、人做判断」这条主线在接口层真的成立，
    尤其是四个关键验收场景与降级路径。

覆盖的验收点：
    1. 不同表述的多条反馈 → 聚合成一张候选卡片
    2. 一条反馈含两个问题 → 被拆成两张卡片（同一 feedback_id 出现两次）
    3. 关键词相似但现象不同 → 不合并
    4. 描述过于笼统 → 判为依据不足，不臆造
    5. AI 产出只是草稿：确认后才生成正式 issue，且 AI 原话被原样留存
    6. 重复确认 / 驳回缺理由 → 分别返回 409 / 400
    7. 越权：CS、DEV 不能触发 AI 生成
    8. 每次调用都写入 ai_generation_log，可回查

用法：
    python scripts/smoke_test_ai.py
    需先启动后端（bash backend/run.sh）
    ⚠️ 会消耗待归类反馈池，跑完建议执行一次 sql/init-data.sql 重置演示数据
"""

import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8081"

passed = 0
failed = 0


def call(method, path, body=None, token=None):
    """发起一次 HTTP 请求，返回 (HTTP状态码, 响应JSON)"""
    url = BASE + path
    data = None
    headers = {"Content-Type": "application/json; charset=utf-8"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    if token:
        headers["Authorization"] = "Bearer " + token

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, {"raw": raw}


def check(name, condition, detail=""):
    """记录一条断言结果"""
    global passed, failed
    if condition:
        passed += 1
        print("  [PASS] " + name)
    else:
        failed += 1
        print("  [FAIL] " + name + ("  -> " + str(detail) if detail else ""))


def login(username, password):
    """登录并返回 token"""
    code, resp = call("POST", "/api/auth/login",
                      {"username": username, "password": password})
    if code != 200 or resp.get("code") != 200:
        print("登录失败：" + username + " -> " + str(resp))
        return None
    return resp["data"]["token"]


def main():
    print("=" * 64)
    print("阶段 4 · AI 模块冒烟测试")
    print("=" * 64)

    # ---------- 0. 登录 ----------
    print("\n[0] 登录")
    cs = login("cs01", "cs123456")
    pm = login("pm01", "pm123456")
    dev = login("dev01", "dev123456")
    admin = login("admin", "admin123456")
    check("四个角色登录成功", all([cs, pm, dev, admin]))
    if not all([cs, pm, dev, admin]):
        return 1

    # ---------- 1. 越权拦截 ----------
    print("\n[1] 越权校验：只有 PM 能触发 AI 生成")
    code, _ = call("POST", "/api/ai/candidates/generate",
                   {"module": "OTHER"}, token=cs)
    check("客服触发 AI 生成被拒绝（403）", code == 403, code)

    code, _ = call("POST", "/api/ai/candidates/generate",
                   {"module": "OTHER"}, token=dev)
    check("开发触发 AI 生成被拒绝（403）", code == 403, code)

    code, _ = call("GET", "/api/ai/status", token=pm)
    check("产品经理查看 AI 状态被拒绝（403）", code == 403, code)

    # ---------- 2. AI 运行状态 ----------
    print("\n[2] 管理员查看 AI 运行状态")
    code, resp = call("GET", "/api/ai/status", token=admin)
    data = (resp or {}).get("data") or {}
    check("状态接口可访问", code == 200 and resp.get("code") == 200, resp)
    check("返回了期望实现与实际实现",
          "expectedProvider" in data and "actualProvider" in data, data)

    # ---------- 3. 生成候选卡片 ----------
    print("\n[3] PM 对「发票上传」模块生成候选问题")
    code, resp = call("POST", "/api/ai/candidates/generate",
                      {"module": "INVOICE_UPLOAD"}, token=pm)
    data = (resp or {}).get("data") or {}
    candidates = data.get("candidates") or []
    check("生成成功", code == 200 and resp.get("code") == 200, resp)
    check("返回了批次号", bool(data.get("batchNo")), data.get("batchNo"))
    check("生成了候选卡片", len(candidates) > 0, len(candidates))

    # ---------- 4. 场景①：不同表述聚成一张 ----------
    print("\n[4] 场景①：9 条不同表述的「上传失败」聚成一张卡片")
    merged = [c for c in candidates
              if set([1, 3, 4, 5, 6, 7, 8, 9, 16]).issubset(
                  set(c.get("relatedFeedbackIds") or []))]
    check("存在一张包含全部 9 条上传失败反馈的卡片",
          len(merged) >= 1, [c["title"] for c in candidates])
    if merged:
        check("该卡片置信度 >= 0.9",
              float(merged[0].get("aiConfidence") or 0) >= 0.9,
              merged[0].get("aiConfidence"))

    # ---------- 5. 场景②：一条反馈拆成两个问题 ----------
    print("\n[5] 场景②：FB-0008 含两个问题，应被拆开")
    cards_with_8 = [c for c in candidates
                    if 8 in (c.get("relatedFeedbackIds") or [])]
    check("FB-0008 出现在 2 张不同的卡片中",
          len(cards_with_8) == 2, len(cards_with_8))
    if len(cards_with_8) == 2:
        modules = sorted([c.get("module") for c in cards_with_8])
        check("两张卡片分属不同模块（跨模块不合并）",
              modules == ["INVOICE_UPLOAD", "RETURN_MODIFY"], modules)

    # ---------- 6. 场景③：相似但不合并 ----------
    print("\n[6] 场景③：FB-0009 与 FB-0010 不应合并")
    card_9 = [c for c in candidates
              if 9 in (c.get("relatedFeedbackIds") or [])]
    card_10 = [c for c in candidates
               if 10 in (c.get("relatedFeedbackIds") or [])]
    check("FB-0009 与 FB-0010 各自归属独立卡片",
          len(card_9) == 1 and len(card_10) == 1, (len(card_9), len(card_10)))
    if card_9 and card_10:
        check("两者不是同一张卡片",
              card_9[0].get("id") != card_10[0].get("id"),
              (card_9[0].get("title"), card_10[0].get("title")))

    # ---------- 7. 确认生成正式问题 ----------
    print("\n[7] PM 确认候选卡片 → 生成正式问题")
    target = merged[0] if merged else (candidates[0] if candidates else None)
    issue_id = None
    if target:
        code, resp = call(
            "POST", "/api/ai/candidates/%s/confirm" % target.get("id"), {
                "title": "移动端发票上传失败率偏高，需补充失败原因与重试入口",
                "description": "【现象】用户上传发票失败且无原因提示\n\n【诉求】明确原因并提供重试",
                "finalContent": "统一失败原因文案，增加重试入口，批量上传逐张反馈结果。",
                "acceptanceCriteria": "1. 失败原因可理解\n2. 提供重试按钮"
            }, token=pm)
        check("确认成功", code == 200 and resp.get("code") == 200, resp)
        if resp.get("code") == 200:
            issue_id = (resp.get("data") or {}).get("issueId")
            check("返回了正式问题ID", issue_id is not None, resp.get("data"))

        # 重复确认应 409（乐观锁生效）
        code, resp = call(
            "POST", "/api/ai/candidates/%s/confirm" % target.get("id"),
            {"title": "重复确认"}, token=pm)
        check("重复确认被拒绝（409）", code == 409, (code, resp))

    # ---------- 8. 证据链：AI 原话留存 ----------
    print("\n[8] 证据链：正式问题中原样保留 AI 草稿")
    if issue_id:
        code, resp = call("GET", "/api/issues/%s" % issue_id, token=pm)
        detail = (resp.get("data") or {}) if resp.get("code") == 200 else {}
        check("问题来源标记为 AI", detail.get("sourceType") == "AI",
              detail.get("sourceType"))
        check("保留了 AI 原稿", bool(detail.get("aiDraftContent")),
              detail.get("aiDraftContent"))
        check("记录了 PM 定稿内容",
              (detail.get("finalContent") or "").startswith("统一失败原因文案"),
              detail.get("finalContent"))
        check("标记为「已修改」", detail.get("modifiedFromAi") is True,
              detail.get("modifiedFromAi"))
        check("建立了反馈关联", len(detail.get("feedbackIds") or []) == 9,
              detail.get("feedbackIds"))

    # ---------- 9. 场景④：依据不足 ----------
    print("\n[9] 场景④：描述笼统的反馈应判为依据不足")
    code, resp = call("POST", "/api/feedbacks", {
        "content": "有问题，找你们技术",
        "module": "OTHER",
        "source": "PHONE",
        "customerTag": "冒烟测试客户",
    }, token=cs)
    vague_id = (resp.get("data") or {}).get("id")
    check("笼统反馈录入成功", vague_id is not None, resp)

    if vague_id:
        code, resp = call("POST", "/api/ai/candidates/generate",
                          {"module": "OTHER"}, token=pm)
        others = ((resp.get("data") or {}).get("candidates")) or []
        hit = [c for c in others if vague_id in (c.get("relatedFeedbackIds") or [])]
        check("笼统反馈被归入候选卡片", len(hit) == 1, len(hit))
        if hit:
            check("置信度低（<=0.30）",
                  float(hit[0].get("aiConfidence") or 1) <= 0.30,
                  hit[0].get("aiConfidence"))
            check("现象中标注依据不足",
                  "依据不足" in (hit[0].get("phenomenon") or ""),
                  hit[0].get("phenomenon"))

    # ---------- 10. 驳回 ----------
    print("\n[10] PM 驳回候选卡片")
    if others:
        first = others[0]
        code, resp = call("POST", "/api/ai/candidates/%s/reject" % first.get("id"),
                          {"reason": ""}, token=pm)
        check("驳回不填原因被拒绝（400）", code == 400, (code, resp))

        code, resp = call("POST", "/api/ai/candidates/%s/reject" % first.get("id"),
                          {"reason": "表述过于笼统，无法定位到具体功能点"}, token=pm)
        check("写明理由后驳回成功", code == 200 and resp.get("code") == 200, resp)

        # 驳回后反馈应回到未归类
        fb_id = (first.get("relatedFeedbackIds") or [None])[0]
        if fb_id:
            code, resp = call("GET", "/api/feedbacks/%s" % fb_id, token=cs)
            status = (resp.get("data") or {}).get("status")
            check("驳回后反馈回到未归类（NEW）", status == "NEW", status)

    # ---------- 11. AI 调用记录回查 ----------
    print("\n[11] AI 调用记录回查")
    code, resp = call("GET", "/api/ai/logs?pageNo=1&pageSize=10", token=pm)
    data = (resp.get("data") or {}) if resp.get("code") == 200 else {}
    records = data.get("records") or []
    check("日志分页可查询", code == 200 and resp.get("code") == 200, resp)
    check("存在调用记录", len(records) > 0, len(records))
    if records:
        r = records[0]
        check("日志含输入材料快照", "inputSnapshot" in r, list(r.keys()))
        check("日志含调用结果", bool(r.get("status")), r.get("status"))
        check("日志含实际使用的实现类型", bool(r.get("provider")), r.get("provider"))

        code2, resp2 = call("GET", "/api/ai/logs/%s" % r.get("id"), token=pm)
        check("日志详情可查询", code2 == 200 and resp2.get("code") == 200, resp2)
        if resp2.get("code") == 200:
            d2 = resp2["data"]
            check("详情含 AI 返回原文", "outputRaw" in d2, list(d2.keys()))
            check("详情含耗时", d2.get("costMs") is not None, d2.get("costMs"))

    # ---------- 12. 幂等：同一模块重复生成不产生重复卡片 ----------
    print("\n[12] 幂等性：反馈被占用后不会重复进入下一批")
    code, resp = call("POST", "/api/ai/candidates/generate",
                      {"module": "INVOICE_UPLOAD"}, token=pm)
    data = (resp.get("data") or {})
    check("已归类的反馈不会重复生成卡片",
          data.get("count") == 0, data)

    # ---------- 汇总 ----------
    print("\n" + "=" * 64)
    print("通过 %d 项，失败 %d 项" % (passed, failed))
    print("=" * 64)
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
