#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
大模型"故障模拟"服务（仅用于测试 AI 降级链路）
================================================

为什么需要它
------------
验收要求里有一条是「AI 返回垃圾数据」时系统应如何表现。
但真实大模型平时不会返回垃圾数据，靠运气等它出错不现实。

本项目已经有一个"模拟 AI 故障"开关（ai.fallback.simulate-failure），
但它只模拟了「服务不可用」这一种情况（HTTP 连不上 / 超时）。
而「服务能连通、也返回了 200，但内容不是约定好的 JSON」
是另一种完全不同的故障：它会走**解析失败**的分支，
而不是**请求失败**的分支。两条分支的兜底逻辑不一样，都要验证。

所以这里起一个假的 OpenAI 兼容端点，故意返回非法内容，
用来稳定复现"垃圾数据"场景。

支持的故障模式（通过路由区分）
------------------------------
  /garbage    —— 返回一段完全不是 JSON 的自然语言
  /truncated  —— 返回半截 JSON（缺右花括号）
  /wrapped    —— 返回被 ```json 代码块包裹的合法 JSON（应当解析成功）
  /ok         —— 返回裸的合法 JSON（应当解析成功）

用法
----
    python scripts/mock_ai_server.py [port]
    默认端口 8099

然后把后端以如下环境变量启动即可指向本服务：
    AI_PROVIDER=llm
    AI_BASE_URL=http://127.0.0.1:8099/garbage
    AI_API_KEY=mock-key
"""

import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

# 一段"看起来像人随口说的"内容，肯定不是 JSON
GARBAGE = "好的，我已了解您的问题。关于报销软件，我建议您可以尝试重新登录。" \
          "如果还是不行的话，可能需要联系一下技术人员看看。希望对你有帮助！"

# 半截 JSON：少了结尾的 }
TRUNCATED = '{"candidates": [{"title": "上传失败", "phenomenon": "无法上传发票"'

# 合法 JSON，但被 markdown 代码块包裹（这是真实模型最常见的"不听话"表现）
WRAPPED = "```json\n" + json.dumps({
    "candidates": [{
        "title": "发票上传失败",
        "phenomenon": "用户上传发票时提示失败",
        "userDemand": "希望上传成功",
        "module": "INVOICE_UPLOAD",
        "confidence": 0.8,
        "reason": "命中上传失败关键词",
        "feedbackIds": [1]
    }]
}, ensure_ascii=False) + "\n```"

# 裸的合法 JSON
OK = json.dumps({
    "candidates": [{
        "title": "发票上传失败",
        "phenomenon": "用户上传发票时提示失败",
        "userDemand": "希望上传成功",
        "module": "INVOICE_UPLOAD",
        "confidence": 0.8,
        "reason": "命中上传失败关键词",
        "feedbackIds": [1]
    }]
}, ensure_ascii=False)

ROUTES = {
    "/garbage": GARBAGE,
    "/truncated": TRUNCATED,
    "/wrapped": WRAPPED,
    "/ok": OK,
}


class Handler(BaseHTTPRequestHandler):
    """处理 /v1/chat/completions 的模拟响应"""

    def do_POST(self):
        # 读取并忽略请求体（测试只关心返回内容）
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(length)

        # 从路径里取出故障模式：/garbage/v1/chat/completions → garbage
        mode = self.path.strip("/").split("/")[0]
        content = ROUTES.get("/" + mode, GARBAGE)

        body = json.dumps({
            "id": "mock-chat",
            "object": "chat.completion",
            "model": "mock-model",
            "choices": [{
                "index": 0,
                "message": {"role": "assistant", "content": content},
                "finish_reason": "stop"
            }]
        }, ensure_ascii=False).encode("utf-8")

        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        # 静音，避免刷屏
        pass


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    server = HTTPServer(("127.0.0.1", port), Handler)
    print("mock AI server listening on http://127.0.0.1:%d" % port)
    print("  可用模式： /garbage  /truncated  /wrapped  /ok")
    print("  后端指向示例：AI_BASE_URL=http://127.0.0.1:%d/garbage" % port)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nstopped")
        server.shutdown()


if __name__ == "__main__":
    main()
