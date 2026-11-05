# 接口清单（第三章成果）

> 后端：Spring Boot 3.2 + Spring MVC + Spring Security + MyBatis-Plus
> 接口前缀：`/api` ｜ 文档：springdoc-openapi（`/swagger-ui.html`）
> 版本：v1.0 ｜ 日期：2026-10-26

---

## 一、统一约定

### 1.1 统一响应结构

```json
{
  "code": 200,
  "message": "OK",
  "data": { },
  "traceId": "a1b2c3d4",
  "timestamp": 1761475200000
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | 业务状态码，200 表示成功 |
| `message` | string | 提示信息，可直接展示给用户 |
| `data` | object | 业务数据 |
| `traceId` | string | 链路追踪 ID，便于排查 |
| `timestamp` | long | 响应时间戳 |

### 1.2 错误码

| code | 含义 | 前端处理 |
| ---: | --- | --- |
| 200 | 成功 | 轻提示 + 刷新 |
| 400 | 请求参数错误 | 内联错误提示 |
| 401 | 未登录 / Token 失效 | 清除登录态，跳转登录页 |
| 403 | 无权限 | 提示「无权限执行此操作」 |
| 404 | 资源不存在 | 提示「记录不存在或已删除」 |
| 409 | 状态冲突 / 乐观锁冲突 | 弹窗「数据已被修改，请刷新后重试」 |
| 500 | 服务器内部错误 | 内联错误条 + 重试按钮 |
| **503** | **AI 服务不可用** | **横幅提示「已切换为手工模式」，展示手工入口** |

> **503 是本项目特意设计的**：它**不代表业务失败**，只是 AI 不可用，业务流程继续。
> 这是挑战赛道验收场景 8 的关键。

### 1.3 分页约定

**请求参数**：`pageNo`（从 1 开始，默认 1）、`pageSize`（默认 10）

**响应结构**：
```json
{
  "pageNo": 1,
  "pageSize": 10,
  "total": 60,
  "records": [ ]
}
```

> 命名与 MyBatis-Plus 的 `IPage` 保持一致，避免实体转换时再做一层字段映射。

### 1.3.1 HTTP 状态码与业务码一致

响应体里的 `code` **同时**作为 HTTP 状态码返回，两者永远相等。

| HTTP 状态 | 场景 |
| ---: | --- |
| 200 | 成功 |
| 400 | 参数校验失败、请求格式错误 |
| 401 | 未登录 / Token 失效 |
| 403 | 角色越权（`@PreAuthorize` 拦截） |
| 404 | 资源不存在 |
| 409 | 状态机非法跳转、乐观锁并发冲突 |
| 500 | 未预期的系统异常 |
| 502 / 503 | AI 返回异常 / AI 不可用（已降级） |

> **这样做的原因**：前端要做「六态反馈」，其中「冲突态」必须和「失败态」区分开。
> 如果所有错误都返回 HTTP 200，前端只能解析 body 判断，axios 拦截器、浏览器网络面板、
> Nginx 访问日志全部失去意义。让 HTTP 状态码说真话，`if (status === 409)` 就能直接切 UI。

### 1.4 认证方式

```
Authorization: Bearer <JWT>
```
JWT payload 仅包含：`userId`、`username`、`role`、`exp`。**不放业务数据**。

---

## 二、接口清单

### 2.1 认证模块 `/api/auth`

| # | 方法 | 路径 | 说明 | 权限 |
| ---: | --- | --- | --- | --- |
| 1 | POST | `/api/auth/login` | 登录，返回 JWT | 公开 |
| 2 | GET | `/api/auth/profile` | 获取当前用户信息与角色 | 登录 |
| 3 | POST | `/api/auth/logout` | 退出登录 | 登录 |

**① POST `/api/auth/login`**

请求：
```json
{ "username": "pm01", "password": "pm123456" }
```
响应：
```json
{
  "code": 200,
  "message": "OK",
  "data": {
    "token": "eyJhbGciOi...",
    "userId": 2,
    "username": "pm01",
    "displayName": "张PM",
    "role": "PM"
  }
}
```

---

### 2.2 反馈模块 `/api/feedbacks`

| # | 方法 | 路径 | 说明 | 权限 |
| ---: | --- | --- | --- | --- |
| 4 | GET | `/api/feedbacks` | 反馈列表（分页 + 筛选） | CS（仅自己）/ PM |
| 5 | POST | `/api/feedbacks` | 单条录入 | CS |
| 6 | POST | `/api/feedbacks/batch` | 批量导入 | CS |
| 7 | GET | `/api/feedbacks/{id}` | 反馈详情 | CS（仅自己）/ PM |
| 8 | PATCH | `/api/feedbacks/{id}/status` | 变更状态（IGNORED） | PM |

> **❗ 注意：本模块不提供任何修改 `content` 的接口**（约束 A6 + 需求文档约定）

**④ GET `/api/feedbacks` 查询参数**

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `module` | string | 功能模块 |
| `source` | string | 来源渠道 |
| `status` | string | 状态 |
| `keyword` | string | 原文关键词模糊搜索 |
| `startDate` / `endDate` | date | 时间范围 |
| `page` / `size` | int | 分页 |

**⑥ POST `/api/feedbacks/batch`**

请求：
```json
{
  "items": [
    { "content": "发票死活传不上去", "module": "INVOICE_UPLOAD",
      "source": "TICKET", "customerTag": "C-0017" },
    { "content": "审批太麻烦", "module": "APPROVAL_PROGRESS",
      "source": "SURVEY", "customerTag": "C-0032" }
  ]
}
```
响应：
```json
{
  "code": 200,
  "data": { "successCount": 58, "failCount": 2,
            "failures": [ { "index": 12, "reason": "内容为空" } ] }
}
```

---

### 2.3 AI 候选问题模块 `/api/candidates`（**核心**）

| # | 方法 | 路径 | 说明 | 权限 |
| ---: | --- | --- | --- | --- |
| 9 | POST | `/api/candidates/generate` | **触发 AI 生成候选卡片** | PM |
| 10 | GET | `/api/candidates` | 候选卡片列表（按状态筛选） | PM |
| 11 | GET | `/api/candidates/{id}` | 候选卡片详情（含关联原文 + AI 理由） | PM |
| 12 | POST | `/api/candidates/{id}/confirm` | 确认 | PM |
| 13 | POST | `/api/candidates/{id}/modify` | 修改后确认 | PM |
| 14 | POST | `/api/candidates/{id}/merge` | 合并到已有问题 | PM |
| 15 | POST | `/api/candidates/{id}/reject` | 驳回（**remark 必填**） | PM |

**⑨ POST `/api/candidates/generate`**

请求：
```json
{
  "module": "INVOICE_UPLOAD",
  "startDate": "2026-10-20",
  "endDate": "2026-10-26",
  "onlyNew": true
}
```
成功响应：
```json
{
  "code": 200,
  "data": {
    "batchNo": "B20261026143000",
    "provider": "LLM",
    "candidateCount": 3,
    "coveredFeedbackCount": 12,
    "costMs": 4820
  }
}
```
AI 不可用响应（**降级，业务继续**）：
```json
{
  "code": 503,
  "message": "AI 服务暂不可用，已切换为手工模式",
  "data": { "provider": "RULE", "fallback": true }
}
```

**⑮ POST `/api/candidates/{id}/reject`**

请求：
```json
{ "remark": "描述过于模糊，无法判断具体问题，已联系客户补充" }
```
> 后端校验：`remark` 为空则返回 400

---

### 2.4 问题模块 `/api/issues`

| # | 方法 | 路径 | 说明 | 权限 |
| ---: | --- | --- | --- | --- |
| 16 | GET | `/api/issues` | 已确认问题列表 | PM, DEV |
| 17 | GET | `/api/issues/{id}` | 问题详情（含 AI 草稿 + PM 定稿） | PM, DEV |
| 18 | POST | `/api/issues` | 手工创建问题（AI 不可用时） | PM |
| 19 | POST | `/api/issues/{id}/draft` | **AI 生成需求与验收条件草稿** | PM |
| 20 | PUT | `/api/issues/{id}/final` | PM 保存定稿 | PM |
| 21 | POST | `/api/issues/{id}/links` | 手工关联原始反馈 | PM |

**⑲ POST `/api/issues/{id}/draft`**

成功响应：
```json
{
  "code": 200,
  "data": {
    "draftContent": "## 需求说明\n优化发票上传失败时的错误提示…",
    "acceptanceCriteria": "1. 上传失败时展示明确原因\n2. 提供重试按钮\n3. 记录失败日志",
    "provider": "LLM",
    "costMs": 3210
  }
}
```
> 草稿存入 `issue.ai_draft_content`，**不覆盖任何已有正式内容**

---

### 2.5 改进任务模块 `/api/tasks`

| # | 方法 | 路径 | 说明 | 权限 |
| ---: | --- | --- | --- | --- |
| 22 | GET | `/api/tasks` | 任务看板（按状态分组） | PM, DEV |
| 23 | GET | `/api/tasks/{id}` | 任务详情（含时间线） | PM, DEV |
| 24 | POST | `/api/tasks` | PM 建立任务（**手选优先级**） | PM |
| 25 | POST | `/api/tasks/{id}/transition` | 状态流转 | DEV（PM 仅可关闭） |
| 26 | POST | `/api/tasks/{id}/assign` | 分派负责人 | PM |

**㉔ POST `/api/tasks`**

请求：
```json
{
  "issueId": 3,
  "title": "优化发票上传失败提示",
  "requirement": "上传失败时展示明确原因并提供重试入口",
  "acceptanceCriteria": "1. …\n2. …",
  "priority": "P0",
  "priorityReason": "7 条反馈、5 家客户受影响，且阻塞报销主流程",
  "assigneeId": 5
}
```

**㉕ POST `/api/tasks/{id}/transition`**

请求：
```json
{ "toStatus": "IN_PROGRESS", "remark": "" }
```
非法跳转响应：
```json
{ "code": 409, "message": "不允许从 待开发 直接跳转到 已完成" }
```
乐观锁冲突响应：
```json
{ "code": 409, "message": "数据已被其他用户修改，请刷新后重试" }
```

---

### 2.6 AI 日志模块 `/api/ai-logs`

| # | 方法 | 路径 | 说明 | 权限 |
| ---: | --- | --- | --- | --- |
| 27 | GET | `/api/ai-logs` | AI 调用日志列表 | PM |
| 28 | GET | `/api/ai-logs/{id}` | 日志详情（输入快照 + 输出原文） | PM |

> 对应挑战赛道「AI 输入、参考资料和输出结构说明」要求

---

### 2.7 文件模块 `/api/files`

| # | 方法 | 路径 | 说明 | 权限 |
| ---: | --- | --- | --- | --- |
| 29 | POST | `/api/files/upload` | 上传凭证图片 | CS |

**存储抽象层**：
```java
public interface FileStorageService {
    String upload(MultipartFile file);
    void delete(String fileKey);
}
```
实现：
- `LocalFileStorageServiceImpl`（默认，写入本地目录，Nginx 代理）
- `OssFileStorageServiceImpl`（阿里云 OSS，Key 占位符）

配置：`storage.type=local|oss`

---

### 2.8 统计模块 `/api/dashboard`（扩展功能，最后做）

| # | 方法 | 路径 | 说明 | 权限 |
| ---: | --- | --- | --- | --- |
| 30 | GET | `/api/dashboard/overview` | 概览统计 | PM |
| 31 | POST | `/api/feedbacks/search-similar` | 相似反馈回查 | PM, CS |

---

## 三、权限矩阵总表

| 接口 | CS | PM | DEV | ADMIN |
| --- |:---:|:---:|:---:|:---:|
| 登录 | ✅ | ✅ | ✅ | ✅ |
| 录入/导入反馈 | ✅ | ❌ | ❌ | ❌ |
| 查看反馈列表 | 仅自己 | 全部 | ❌ | ❌ |
| 变更反馈状态为无效 | ❌ | ✅ | ❌ | ❌ |
| **触发 AI 生成候选** | ❌ | ✅ | ❌ | ❌ |
| 确认/修改/合并/驳回候选 | ❌ | ✅ | ❌ | ❌ |
| 手工创建问题 | ❌ | ✅ | ❌ | ❌ |
| **AI 生成需求草稿** | ❌ | ✅ | ❌ | ❌ |
| **设定/修改优先级** | ❌ | ✅ | ❌ | ❌ |
| 建立改进任务 | ❌ | ✅ | ❌ | ❌ |
| 推进任务状态 | ❌ | 仅关闭 | ✅ | ❌ |
| 查看 AI 日志 | ❌ | ✅ | ❌ | ❌ |
| 上传凭证图片 | ✅ | ❌ | ❌ | ❌ |

---

## 四、Swagger 配置要点

使用 **springdoc-openapi 2.x**（非 springfox）：

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
  group-configs:
    - group: 'default'
      paths-to-match: '/api/**'
```

**注解对照**（springfox → springdoc）：

| springfox（旧） | springdoc（新） |
| --- | --- |
| `@Api` | `@Tag` |
| `@ApiOperation` | `@Operation` |
| `@ApiModel` | `@Schema` |
| `@ApiModelProperty` | `@Schema` |
| `@ApiParam` | `@Parameter` |

> **注意**：Spring Boot 3.x 全面使用 `jakarta.*` 包名，
> 导入 `javax.*` 会编译失败，需特别注意 JWT 相关依赖。
