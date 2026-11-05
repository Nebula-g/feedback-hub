# -*- coding: utf-8 -*-
"""
阶段 2 后端冒烟测试脚本

用途：
    在没有任何前端的情况下，用 HTTP 请求验证后端主链路是否跑通，
    同时验证权限与状态机的拦截是否生效。

覆盖的验收点：
    1. 四个角色都能登录（JWT 签发正常）
    2. CS 可以录入反馈
    3. CS 不能创建问题（越权必须被拒绝）
    4. PM 可以创建问题、关联反馈
    5. PM 可以创建任务并定级
    6. DEV 可以推进任务状态
    7. DEV 不能一步把任务改成已完成（非法跳转必须被拒绝）
    8. CS 不能推进任务状态（越权必须被拒绝）

用法：
    python scripts/smoke_test.py
    需先启动后端（bash backend/run.sh）
"""

import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8081"

# 统计通过/失败数量，最后打印汇总
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
        with urllib.request.urlopen(req, timeout=15) as resp:
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
    print("=" * 60)
    print("阶段 2 后端冒烟测试")
    print("=" * 60)

    # ---------- 1. 登录 ----------
    print("\n[1] 四个角色登录")
    cs = login("cs01", "cs123456")
    pm = login("pm01", "pm123456")
    dev = login("dev01", "dev123456")
    admin = login("admin", "admin123456")
    check("客服登录成功", cs is not None)
    check("产品经理登录成功", pm is not None)
    check("开发测试登录成功", dev is not None)
    check("管理员登录成功", admin is not None)
    if not all([cs, pm, dev]):
        print("\n登录未全部成功，后续测试无法继续")
        return 1

    # ---------- 2. CS 录入反馈 ----------
    print("\n[2] 客服录入反馈")
    code, resp = call("POST", "/api/feedbacks", {
        "content": "冒烟测试：报销单附件上传后一直转圈，最后提示网络异常",
        "module": "INVOICE_UPLOAD",
        "source": "PHONE",
        "customerTag": "中型制造业客户",
        "imageUrls": []
    }, token=cs)
    check("反馈创建成功", code == 200 and resp.get("code") == 200, resp)
    feedback_id = None
    if code == 200 and resp.get("code") == 200:
        feedback_id = resp["data"].get("id")
        check("返回了反馈ID", feedback_id is not None, resp)

    # ---------- 3. CS 越权创建问题 ----------
    print("\n[3] 越权校验：客服不能创建问题")
    code, resp = call("POST", "/api/issues", {
        "title": "越权测试",
        "description": "客服不应能创建问题",
        "module": "INVOICE_UPLOAD"
    }, token=cs)
    check("客服创建问题被拒绝（403）", code == 403, (code, resp))

    # ---------- 4. PM 创建问题并关联反馈 ----------
    print("\n[4] 产品经理创建问题并关联反馈")
    code, resp = call("POST", "/api/issues", {
        "title": "冒烟测试：附件上传超时",
        "description": "多个客户反映附件上传长时间无响应，最终提示网络异常",
        "module": "INVOICE_UPLOAD",
        "acceptanceCriteria": "10MB 以内附件 5 秒内上传成功，失败有明确提示"
    }, token=pm)
    check("问题创建成功", code == 200 and resp.get("code") == 200, resp)
    issue_id = resp["data"].get("id") if resp.get("code") == 200 else None

    if issue_id and feedback_id:
        code, resp = call("POST", "/api/issues/%s/feedbacks" % issue_id,
                          {"feedbackIds": [feedback_id]}, token=pm)
        check("反馈关联成功", code == 200 and resp.get("code") == 200, resp)
        if resp.get("code") == 200:
            check("实际新增关联 1 条",
                  resp["data"].get("linkedCount") == 1, resp["data"])

        # 重复关联应幂等（不报错、不重复计数）
        code, resp = call("POST", "/api/issues/%s/feedbacks" % issue_id,
                          {"feedbackIds": [feedback_id]}, token=pm)
        check("重复关联保持幂等（新增 0 条）",
              resp.get("code") == 200 and resp["data"].get("linkedCount") == 0, resp)

        # 反馈状态应变为 LINKED
        code, resp = call("GET", "/api/feedbacks/%s" % feedback_id, token=cs)
        if resp.get("code") == 200:
            check("反馈状态已变为 LINKED",
                  resp["data"].get("status") == "LINKED", resp["data"].get("status"))

    # ---------- 5. PM 创建任务 ----------
    print("\n[5] 产品经理创建改进任务")
    task_id = None
    if issue_id:
        code, resp = call("POST", "/api/tasks", {
            "issueId": issue_id,
            "title": "修复附件上传超时",
            "requirement": "排查上传链路超时配置，完善失败提示",
            "acceptanceCriteria": "10MB 以内附件 5 秒内上传成功",
            "priority": "P0",
            "priorityReason": "阻塞报销提交主流程，涉及多家客户",
            "assigneeId": 3
        }, token=pm)
        check("任务创建成功", code == 200 and resp.get("code") == 200, resp)
        if resp.get("code") == 200:
            task_id = resp["data"].get("id")

    # 缺定级理由应被拒绝
    if issue_id:
        code, resp = call("POST", "/api/tasks", {
            "issueId": issue_id,
            "title": "缺少理由的任务",
            "priority": "P0"
        }, token=pm)
        check("缺定级理由被拒绝（400）", code == 400, (code, resp))

    # ---------- 6. DEV 推进状态 ----------
    if task_id:
        print("\n[6] 开发推进任务状态")

        # 非法跳转：TODO 直接到 DONE
        code, resp = call("PUT", "/api/tasks/%s/status" % task_id,
                          {"status": "DONE"}, token=dev)
        check("TODO 直接跳 DONE 被拒绝（409）", code == 409, (code, resp))

        # 合法：TODO -> IN_PROGRESS
        code, resp = call("PUT", "/api/tasks/%s/status" % task_id,
                          {"status": "IN_PROGRESS"}, token=dev)
        check("TODO -> IN_PROGRESS 成功", code == 200 and resp.get("code") == 200, resp)

        # 合法：IN_PROGRESS -> PENDING_VERIFY
        code, resp = call("PUT", "/api/tasks/%s/status" % task_id,
                          {"status": "PENDING_VERIFY"}, token=dev)
        check("IN_PROGRESS -> PENDING_VERIFY 成功",
              code == 200 and resp.get("code") == 200, resp)

        # 打回必须写理由
        code, resp = call("PUT", "/api/tasks/%s/status" % task_id,
                          {"status": "IN_PROGRESS"}, token=dev)
        check("打回不写理由被拒绝（400）", code == 400, (code, resp))

        code, resp = call("PUT", "/api/tasks/%s/status" % task_id,
                          {"status": "IN_PROGRESS",
                           "remark": "大文件仍超时，请检查分片逻辑"}, token=dev)
        check("打回并写明理由成功",
              code == 200 and resp.get("code") == 200, resp)

        # 越权：CS 不能推进状态
        code, resp = call("PUT", "/api/tasks/%s/status" % task_id,
                          {"status": "PENDING_VERIFY"}, token=cs)
        check("客服推进状态被拒绝（403）", code == 403, (code, resp))

        # 越权：DEV 不能改优先级
        code, resp = call("PUT", "/api/tasks/%s/priority" % task_id,
                          {"priority": "P1", "priorityReason": "我想降级"}, token=dev)
        check("开发改优先级被拒绝（403）", code == 403, (code, resp))

        # 时间线
        code, resp = call("GET", "/api/tasks/%s/events" % task_id, token=dev)
        check("时间线可查询且有多条记录",
              resp.get("code") == 200 and len(resp.get("data") or []) >= 3, resp)
        if resp.get("code") == 200:
            events = resp["data"]
            check("时间线首条为创建事件",
                  events[0].get("toStatus") == "TODO", events[0])

    # ---------- 7. 未登录访问 ----------
    print("\n[7] 未登录访问受保护接口")
    code, resp = call("GET", "/api/feedbacks?pageNo=1&pageSize=5")
    check("未登录被拒绝（401）", code == 401, (code, resp))

    # ---------- 汇总 ----------
    print("\n" + "=" * 60)
    print("通过 %d 项，失败 %d 项" % (passed, failed))
    print("=" * 60)
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
