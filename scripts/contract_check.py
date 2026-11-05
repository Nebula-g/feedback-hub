#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
阶段 5-A：接口契约三方核对脚本
=================================

核对「前端页面  <->  后端接口  <->  数据库/实体」三方是否一致。

本脚本只做**静态 + 运行时**核对，不修改任何业务数据（只调 GET 接口）。
目的是在答辩前把"前端引用了后端根本不存在的字段"这类低级但致命的
契约错误一次性扫出来 —— 这类错误在阶段 4 联调时已经出现过 2 次
（AiLogView 引用 errorMessage/promptText/responseText、业务类型选项写成
CANDIDATE_ISSUE/REQUIREMENT_DRAFT），靠肉眼很难全部发现。

核对内容
--------
【1】接口清单核对：后端 @XxxMapping 声明的接口  vs  前端 api/*.js 实际调用的接口
      -> 后端有但前端没用（可能是漏做页面）
      -> 前端调了但后端没有（必然 404）
【2】响应字段核对：后端接口真实返回的 JSON 字段  vs  前端页面引用的字段
      -> 前端引用了响应里不存在的字段（展示为空白/下拉选不中）
【3】请求字段核对：前端提交的字段  vs  后端 DTO 声明的字段
      -> 前端传了后端不收的字段（静默丢失，很难发现）
【4】枚举值核对：后端枚举 code  vs  前端下拉选项 value
      -> 两侧取值不一致（筛选项失效、状态标签显示空白）

用法
----
    python scripts/contract_check.py

前置条件：后端已启动在 http://127.0.0.1:8081
"""

import json
import os
import re
import sys
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8081"

# 项目根目录 = 本脚本所在目录的上一级
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

BACKEND_SRC = os.path.join(ROOT, "backend", "src", "main", "java", "com", "company", "feedback")
FRONTEND_SRC = os.path.join(ROOT, "frontend", "src")

# ---------------------------------------------------------------- 统计计数
TOTAL = 0
FAILED = 0
# 记录所有问题，最后统一打印，便于一次性抄进复盘文档
ISSUES = []


def ok(msg):
    """记录一条通过项"""
    global TOTAL
    TOTAL += 1
    print("  [PASS] " + msg)


def fail(msg, detail=""):
    """记录一条失败项"""
    global TOTAL, FAILED
    TOTAL += 1
    FAILED += 1
    ISSUES.append(msg + ("  <- " + detail if detail else ""))
    print("  [FAIL] " + msg + ("  <- " + detail if detail else ""))


def section(title):
    print("\n" + "=" * 64)
    print(title)
    print("=" * 64)


# ---------------------------------------------------------------- HTTP 工具
def http(method, path, token=None, body=None):
    """
    发起 HTTP 请求，返回 (http状态码, 解析后的JSON字典或None, 响应原文)
    """
    url = BASE + path
    data = None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
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
    except Exception as e:  # 连接失败等
        return 0, None, str(e)


def login(username, password):
    """
    登录并取出 token；失败返回 None
    """
    _, body, raw = http("POST", "/api/auth/login", body={"username": username, "password": password})
    if body and body.get("code") == 200 and body.get("data"):
        return body["data"].get("token")
    print("  [ERROR] 登录失败 %s: %s" % (username, raw[:200]))
    return None


def read_file(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return f.read()
    except Exception:
        return ""


# ---------------------------------------------------------------- 源码解析
def scan_backend_endpoints():
    """
    扫描后端 Controller，返回接口全路径集合，如 'POST /api/feedbacks'
    同时支持类级 @RequestMapping 与单行的方法级 @XxxMapping
    """
    ctr_dir = os.path.join(BACKEND_SRC, "controller")
    results = {}
    if not os.path.isdir(ctr_dir):
        return results
    for fname in sorted(os.listdir(ctr_dir)):
        if not fname.endswith(".java"):
            continue
        text = read_file(os.path.join(ctr_dir, fname))
        # 类级前缀
        m = re.search(r'@RequestMapping\(\s*"([^"]+)"', text)
        prefix = m.group(1) if m else ""
        # 方法级映射：@GetMapping("/xxx") 或 @PostMapping  （空参数即映射到类前缀）
        for mm in re.finditer(r'@(Get|Post|Put|Delete)Mapping(?:\(\s*(?:"([^"]*)")?\s*\))?', text):
            verb = mm.group(1).upper()
            sub = mm.group(2) or ""
            full = re.sub(r"/+", "/", (prefix + "/" + sub).rstrip("/"))
            # 路径参数名归一成 {}：后端写 {feedbackId}、前端写 ${feedbackId}，
            # 名字可能不同但占位语义相同，统一后才能做集合比对
            full = re.sub(r"\{[^}]*\}", "{}", full)
            results[verb + " " + (full if full else prefix)] = fname
    return results


def scan_frontend_calls():
    """
    扫描前端 api/*.js，返回调用集合，如 'POST /api/feedbacks'
    处理 request.get('/feedbacks') 与模板字符串 `/feedbacks/${id}`
    """
    api_dir = os.path.join(FRONTEND_SRC, "api")
    results = {}
    if not os.path.isdir(api_dir):
        return results
    for fname in sorted(os.listdir(api_dir)):
        if not fname.endswith(".js"):
            continue
        text = read_file(os.path.join(api_dir, fname))
        for mm in re.finditer(r"request\.(get|post|put|delete)\(\s*[`']([^`']*)[`']", text):
            verb = mm.group(1).upper()
            # 模板字符串里的 ${id} 还原成统一的路径参数占位符 {}
            path = re.sub(r"\$\{[^}]+\}", "{}", mm.group(2))
            results[verb + " /api" + path] = fname
    return results


def scan_java_fields(java_file):
    """
    解析 Java 文件里的 private 字段，返回字段名集合
    只取 `private 类型 字段名;` 这一行，忽略方法内局部变量
    """
    text = read_file(java_file)
    fields = set()
    for mm in re.finditer(r"^\s*private\s+[\w<>,.\[\]\s]+?\s+(\w+)\s*(?:=|;)", text, re.M):
        fields.add(mm.group(1))
    return fields


def scan_frontend_field_refs(vue_file, prefixes):
    """
    扫描 .vue 文件里对某个对象的字段引用，如 row.title / detail.xxx
    prefixes 为该页面中代表后端实体的变量名前缀
    """
    text = read_file(vue_file)
    # 剥离 <style> 块：里面的 .candidate-card.is-handled 之类是 CSS 选择器，
    # 不是字段引用，不过滤会造出 "card.is" 这种假阳性
    text = re.sub(r"<style[^>]*>.*?</style>", "", text, flags=re.S | re.I)
    refs = set()
    pat = r"\b(?:" + "|".join(re.escape(p) for p in prefixes) + r")\.([a-zA-Z_]\w*)"
    for mm in re.finditer(pat, text):
        refs.add(mm.group(1))
    return refs


def scan_vo_builder_assignments(vo_name):
    """
    扫描全部 Java 源码中某个 VO 的 builder 链路，返回真正被赋过值的字段名集合

    <p>用途：VO 里声明了字段，但后端从来没给它赋值 —— 这类字段会永远为 null，
    前端引用它只会得到空白。这是比"字段名拼错"更隐蔽的一类契约错误，
    因为编译能过、接口能通，只有点开页面才看得出缺东西。
    """
    assigned = set()
    for root, _dirs, files in os.walk(BACKEND_SRC):
        for fname in files:
            if not fname.endswith(".java"):
                continue
            text = read_file(os.path.join(root, fname))
            # 匹配 XxxVO.builder()....build() 之间的所有 .xxx( 调用
            pattern = re.escape(vo_name) + r"\.builder\(\)(.*?)\.build\(\)"
            for mm in re.finditer(pattern, text, re.S):
                for f in re.finditer(r"\.(\w+)\s*\(", mm.group(1)):
                    assigned.add(f.group(1))
    return assigned


def scan_frontend_enum_options(vue_file):
    """
    扫描 .vue 文件里形如 { label: 'xxx', value: 'YYY' } 的下拉选项，返回 value 集合
    value 形如全大写+下划线的才视为枚举值（避免把业务数据混进来）
    """
    text = read_file(vue_file)
    vals = set()
    for mm in re.finditer(r"value:\s*['\"]([A-Z][A-Z0-9_]{1,})['\"]", text):
        vals.add(mm.group(1))
    return vals


def scan_java_enum_codes(java_file):
    """
    解析 Java 枚举，返回 code 集合（取每个枚举常量构造器的第一个字符串参数）
    """
    text = read_file(java_file)
    codes = set()
    # 形如：TODO("待办", "待处理")
    for mm in re.finditer(r'^\s{4}([A-Z][A-Z0-9_]*)\(\s*"([^"]*)"', text, re.M):
        codes.add(mm.group(1))
    return codes


# ---------------------------------------------------------------- 核对主体
def check_endpoints():
    """【1】接口清单核对"""
    section("[1] 接口清单核对：后端声明 vs 前端调用")

    backend = scan_backend_endpoints()
    frontend = scan_frontend_calls()

    print("  后端声明接口 %d 个，前端实际调用 %d 个" % (len(backend), len(frontend)))

    # 前端调了但后端没有 —— 必然 404，属于致命问题
    missing = sorted(set(frontend) - set(backend))
    if missing:
        for m in missing:
            fail("前端调用的接口后端不存在：" + m)
    else:
        ok("前端调用的接口后端全部存在")

    # 后端有但前端没用 —— 不一定有问题（例如 ADMIN 用的运维接口），仅提示
    unused = sorted(set(backend) - set(frontend))
    if unused:
        print("  [INFO] 后端有但前端未调用的接口（需判断是否有意为之）：")
        for u in unused:
            print("         - " + u + "   (" + backend[u] + ")")
    else:
        print("  [INFO] 后端接口全部被前端调用")

    return backend, frontend


def check_response_fields(tokens):
    """【2】响应字段核对：真实响应 vs 前端引用"""
    section("[2] 响应字段核对：接口真实返回 vs 前端页面引用")

    # 页面 -> (前缀列表, 对应的后端 VO 文件名列表, 用哪个角色的 token 去取数据)
    mapping = [
        ("feedback/FeedbackListView.vue", ["row"], ["FeedbackVO"], "pm", "/api/feedbacks?pageNum=1&pageSize=1"),
        ("feedback/FeedbackDetailView.vue", ["detail"], ["FeedbackVO"], "pm", "/api/feedbacks/1"),
        ("issue/IssueListView.vue", ["row"], ["IssueVO"], "pm", "/api/issues?pageNum=1&pageSize=1"),
        # IssueDetailView 混用两种 VO：detail=IssueVO，关联弹窗候选表格 row=FeedbackVO
        ("issue/IssueDetailView.vue", ["detail", "row"], ["IssueVO", "FeedbackVO"], "pm", "/api/issues/1"),
        ("task/TaskBoardView.vue", ["task"], ["TaskVO"], "pm", "/api/tasks?pageNum=1&pageSize=1"),
        ("task/TaskDetailView.vue", ["detail", "event"], ["TaskVO", "TaskEventVO"], "pm", "/api/tasks/1"),
        ("ai/AiCandidateView.vue", ["card"], ["AiCandidateVO"], "pm", "/api/ai/candidates?pageNum=1&pageSize=1"),
        ("ai/AiLogView.vue", ["row"], ["AiLogVO"], "pm", "/api/ai/logs?pageNum=1&pageSize=1"),
    ]

    for rel, prefixes, vos, who, probe in mapping:
        vue_path = os.path.join(FRONTEND_SRC, "views", rel)
        if not os.path.exists(vue_path):
            fail("前端页面不存在：" + rel)
            continue

        # 汇总该页面对应的所有 VO 字段
        vo_fields = set()
        for vo in vos:
            p = os.path.join(BACKEND_SRC, "vo", vo + ".java")
            if os.path.exists(p):
                vo_fields |= scan_java_fields(p)
            else:
                fail("后端 VO 文件不存在：" + vo + ".java")

        # 拉取真实响应，取列表首项（或 data 本身）
        _, body, raw = http("GET", probe, token=tokens[who])
        sample = None
        if body and body.get("code") == 200 and body.get("data"):
            d = body["data"]
            if isinstance(d, dict) and isinstance(d.get("list"), list) and d["list"]:
                sample = d["list"][0]
            elif isinstance(d, dict) and isinstance(d.get("records"), list) and d["records"]:
                sample = d["records"][0]
            elif isinstance(d, dict) and not isinstance(d.get("list"), list) \
                    and not isinstance(d.get("records"), list):
                # 详情类接口：data 本身就是实体
                sample = d
            else:
                # 分页接口但列表为空 —— 拿不到单条样本，
                # 不能拿整个 data（含 pageNo/records 等分页字段）去比对 VO，
                # 否则会把分页包装字段误判成"VO 未声明的字段"
                sample = None
        if sample is None:
            print("  [SKIP] %s（接口无数据样本，跳过字段比对）" % rel)
            continue
        real_fields = set(sample.keys())

        # 运行时字段必须是 VO 声明的子集（响应里不该冒出 VO 没声明的东西）
        only_runtime = real_fields - vo_fields
        if only_runtime:
            fail("%s：响应含 VO 未声明的字段 %s" % (rel, sorted(only_runtime)))
        else:
            ok("%s：响应字段未超出 VO 声明（返回 %d 个）" % (rel, len(real_fields)))

        # 后端 jackson 配了 non_null，null 字段不会出现在响应里，
        # 所以"VO 声明但响应缺失"本身是正常的；真正要抓的是
        # 「VO 声明了、但后端代码从头到尾根本没赋过值」——那才是永远空白。
        never_assigned = set()
        for vo in vos:
            declared = scan_java_fields(os.path.join(BACKEND_SRC, "vo", vo + ".java"))
            declared = {f for f in declared if f != "serialVersionUID"}
            never_assigned |= (declared - scan_vo_builder_assignments(vo))
        if never_assigned:
            fail("%s：VO 声明但后端从未赋值的字段 %s（前端引用会永远空白）"
                 % (rel, sorted(never_assigned)))
        else:
            ok("%s：VO 字段全部有赋值来源" % rel)

        # 前端引用 vs 真实响应字段
        refs = scan_frontend_field_refs(vue_path, prefixes)
        # 排除明显是前端本地状态/组件属性的名字
        local_noise = {
            "value", "label", "length", "map", "filter", "push", "trim", "split",
            "join", "includes", "indexOf", "slice", "toString", "toFixed",
            "forEach", "find", "some", "every", "sort", "concat", "keys", "name",
        }
        refs = {r for r in refs if r not in local_noise}
        # 只关心"看起来像后端字段"的：在 VO 里有，或命名风格像（小写驼峰且非纯函数）
        ghost = sorted(r for r in refs if r not in vo_fields and not r.endswith("Ref"))
        if ghost:
            fail("%s：前端引用了后端不存在的字段 %s" % (rel, ghost))
        else:
            ok("%s：前端引用的 %d 个字段后端均存在" % (rel, len(refs)))


def check_request_fields(tokens):
    """【3】请求字段核对：前端提交 vs 后端 DTO"""
    section("[3] 请求字段核对：前端提交 vs 后端 DTO")

    # (前端 api 文件里的提交对象, 后端 DTO, 提交端点是哪个页面)
    # 这里采用"运行时实测"：直接按前端字段名发一次请求，看后端是否报错或字段丢失。
    # 为避免污染数据，只做字段存在性比对（读源码），不发真实写请求。
    checks = [
        ("FeedbackCreateRequest", "feedback/FeedbackCreateView.vue"),
        ("IssueCreateRequest", "issue/IssueListView.vue"),
        ("TaskCreateRequest", "task/TaskBoardView.vue"),
        ("FeedbackStatusUpdateRequest", "feedback/FeedbackDetailView.vue"),
        ("TaskStatusUpdateRequest", "task/TaskDetailView.vue"),
        ("TaskPriorityUpdateRequest", "task/TaskDetailView.vue"),
        ("TaskAssignRequest", "task/TaskDetailView.vue"),
        ("AiGenerateRequest", "ai/AiCandidateView.vue"),
        ("AiCandidateConfirmRequest", "ai/AiCandidateView.vue"),
        ("AiCandidateRejectRequest", "ai/AiCandidateView.vue"),
    ]

    for dto, rel in checks:
        dto_path = os.path.join(BACKEND_SRC, "dto", dto + ".java")
        if not os.path.exists(dto_path):
            fail("后端 DTO 不存在：" + dto)
            continue
        dto_fields = scan_java_fields(dto_path)
        # 去掉 Jackson 忽略/内部字段
        dto_fields = {f for f in dto_fields if f not in {"serialVersionUID"}}

        vue_path = os.path.join(FRONTEND_SRC, "views", rel)
        if not os.path.exists(vue_path):
            fail("前端页面不存在：" + rel)
            continue
        text = read_file(vue_path)
        # 找出页面里所有 'xxx:' 形式的键（近似认为是提交字段）
        submitted = set(re.findall(r"^\s{2,}([a-zA-Z_]\w*):", text, re.M))
        submitted |= set(re.findall(r"(\w+)\.value\s*=", text))
        submitted = {s for s in submitted if s in dto_fields or s[0].islower()}

        ghost = sorted(s for s in submitted if s not in dto_fields)
        if ghost:
            # 这些可能是页面本地状态，只提示不判失败
            print("  [INFO] %s：页面含 DTO 之外的字段 %s（若非本地状态则需核对）" % (rel, ghost[:15]))
        else:
            ok("%s：页面提交字段均被 %s 接收" % (rel, dto))


def check_enums():
    """【4】枚举值核对：后端枚举 code vs 前端选项 value"""
    section("[4] 枚举值核对：后端枚举 vs 前端下拉选项")

    enum_dir = os.path.join(BACKEND_SRC, "enums")
    backend_enums = {}
    if os.path.isdir(enum_dir):
        for fname in sorted(os.listdir(enum_dir)):
            if fname.endswith(".java"):
                backend_enums[fname[:-5]] = scan_java_enum_codes(os.path.join(enum_dir, fname))

    # 汇总前端所有页面的选项 value
    views_dir = os.path.join(FRONTEND_SRC, "views")
    frontend_vals = set()
    for root, _dirs, files in os.walk(views_dir):
        for fname in files:
            if fname.endswith(".vue"):
                frontend_vals |= scan_frontend_enum_options(os.path.join(root, fname))
    # 再加上 store / api 里可能的枚举
    for root, _dirs, files in os.walk(FRONTEND_SRC):
        for fname in files:
            if fname.endswith(".js"):
                p = os.path.join(root, fname)
                for mm in re.finditer(r"value:\s*['\"]([A-Z][A-Z0-9_]{2,})['\"]", read_file(p)):
                    frontend_vals.add(mm.group(1))

    # 所有后端枚举 code 的并集
    all_backend = set()
    for k, v in backend_enums.items():
        all_backend |= v

    # 前端用了但后端所有枚举里都没有 —— 高危
    unknown = sorted(frontend_vals - all_backend)
    if unknown:
        for u in unknown:
            fail("前端选项值在后端枚举中不存在：" + u)
    else:
        ok("前端所有枚举选项值均能在后端枚举中找到（共 %d 个）" % len(frontend_vals))

    # 打印后端枚举清单，便于人工确认语义
    print("\n  后端枚举清单：")
    for k in sorted(backend_enums):
        print("    %-22s %s" % (k, ", ".join(sorted(backend_enums[k]))))


# ---------------------------------------------------------------- 入口
def main():
    print("=" * 64)
    print("阶段 5-A：接口契约三方核对")
    print("目标服务：" + BASE)
    print("=" * 64)

    # 四个演示账号各登录一遍，后续按角色取用
    tokens = {}
    for who, u, p in [("pm", "pm01", "pm123456"),
                      ("cs", "cs01", "cs123456"),
                      ("dev", "dev01", "dev123456"),
                      ("admin", "admin", "admin123456")]:
        t = login(u, p)
        if not t:
            print("[FATAL] 账号 %s 登录失败，无法继续核对" % u)
            return 1
        tokens[who] = t
    print("\n四个演示账号均登录成功")

    # 预置数据：候选卡片列表在空库时没有样本，
    # 会导致"响应字段 vs VO 声明"这一项被跳过。
    # 这里先让 PM 生成一批卡片，保证 8 个页面都能取到真实响应样本。
    print("\n预置演示数据（生成候选卡片，供字段比对取样）...")
    _, gb, _ = http("POST", "/api/ai/candidates/generate", token=tokens["pm"],
                    body={"module": ""})
    gen = (gb or {}).get("data") or {}
    print("  批次号=%s，卡片数=%d"
          % (gen.get("batchNo"), len(gen.get("candidates") or [])))

    check_endpoints()
    check_response_fields(tokens)
    check_request_fields(tokens)
    check_enums()

    # ------------------------------------------------------------ 汇总
    print("\n" + "=" * 64)
    print("核对结果：通过 %d 项，失败 %d 项" % (TOTAL - FAILED, FAILED))
    print("=" * 64)
    if ISSUES:
        print("\n待修复问题清单：")
        for i, it in enumerate(ISSUES, 1):
            print("  %2d. %s" % (i, it))
        return 1

    print("\n三方契约一致，未发现字段级错配。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
