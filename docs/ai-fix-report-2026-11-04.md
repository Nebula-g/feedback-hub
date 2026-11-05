# AI 功能验收问题修复报告

- 日期：2026-11-04
- 背景：同日完成的《AI 功能验收测试》（`docs/ai-feature-acceptance-test-2026-11-04.md`）发现 3 个问题，本次逐项修复并实测。
- 涉及改动：后端 5 个文件、前端 2 个文件，均已编译/运行验证。

---

## P1（功能缺口）：候选问题「合并」接口

**问题**：`CandidateStatus.MERGED` 是死枚举值，全代码库无任何入口，验收点 4 的「合并」无法完成。

**修复**：

| 层 | 文件 | 改动 |
|---|---|---|
| DTO | `dto/AiCandidateMergeRequest.java`（新增） | `targetIssueId`（必填）+ `remark`（选填，合并说明） |
| 接口 | `service/AiCandidateService.java` | 新增 `merge(Long id, AiCandidateMergeRequest request)` |
| 实现 | `service/impl/AiCandidateServiceImpl.java` | 见下方逻辑 |
| 控制器 | `controller/AiController.java` | 新增 `POST /api/ai/candidates/{id}/merge`，仅 PM/ADMIN |
| 前端 | `api/ai.js` / `views/ai/AiCandidateView.vue` | `mergeCandidate` API + 卡片「合并」按钮（拆分旁）+ 合并弹窗（目标问题下拉，filterable 可搜索） |

**合并逻辑**：
1. 卡片必须是 DRAFT（已确认/已驳回/已拆分的卡片禁止合并，409）；
2. 目标问题必须存在（404）；
3. 复用 `IssueService.linkFeedbacks` 幂等地把卡片关联的反馈挂到目标问题（feedback → LINKED）；
4. 卡片转 `MERGED`，记录处理人与合并说明，留档可追溯；
5. `MERGED` 卡片的反馈纳入「已占用」统计 → 不会回流到下次 AI 生成的待归类池。

**实测结果**：
- ✅ PM 合并候选 #68 → 问题 #8：卡片 MERGED、反馈 #81/#82 转 LINKED、`feedback_issue_link` 落库、留痕
- ✅ CS 调用 → 403；目标问题不存在 → 404；已处理卡片 → 409
- ✅ 合并后再次生成候选：待归类池为空，反馈不重复进入

---

## P2（准确性）：规则引擎跨模块误归组

**问题**：「报销单提交不了」（EXPENSE_FORM）被跨模块关键词规则误并进「发票上传失败」（INVOICE_UPLOAD）卡片，且置信度虚高、归类理由无任何提示。

**修复**（最小改动，在现有两条匹配轮次上追加标记，不改聚类结构）：

| 文件 | 改动 |
|---|---|
| `dto/AiExtractItem.java` | 新增 `crossModule` 字段（大模型实现不受影响，共用同一结构） |
| `service/impl/RuleAiServiceImpl.java` | ① `classifyClause`：仅当第二轮（其他模块）命中时置 `crossModule=true`；② `buildSuggestion`：组内存在跨模块命中 → 置信度 `-0.15`（新常量 `CROSS_MODULE_PENALTY`），`ai_reason` 追加「⚠ 存在跨模块误匹配……可用拆分修正」警示；③ 置信度口径注释同步更新 |

**实测结果**：
- ✅ 重新导入含跨模块陷阱的反馈并生成：误并组置信度 0.75 → **0.60**，归类理由明确提示跨模块风险，引导 PM 用「拆分」修正
- ✅ 纯同模块归组不受影响（不扣分、无警示）

---

## P3（权限）：候选列表接口无角色限制

**问题**：`GET /api/ai/candidates`、`GET /api/ai/candidates/{id}` 任何登录角色可访问，与确认/驳回/拆分仅 PM 的口径不一致。

**修复**：`AiController` 两个查询端点加 `@PreAuthorize`（PM/ADMIN），类头权限矩阵注释同步更新。

**实测结果**：✅ CS 403、DEV 403、PM 200。

---

## 遗留事项（有意未改）

- **ADMIN 建任务当前 403**：与「管理员拥有最高权限」的直觉不一致，属产品口径问题（建任务是产品决策还是管理动作），保持现状，待产品确认后再动。
- 规则引擎的关键词聚类本质局限仍在（本次只是加了标记与降分让风险可见），若要根治需语义级相似度（LLM provider 已具备该能力，切换 `ai.provider=llm` 即可）。

## 改动文件清单

```
backend/src/main/java/com/company/feedback/
├── dto/AiCandidateMergeRequest.java        （新增）
├── dto/AiExtractItem.java                  （+crossModule）
├── service/AiCandidateService.java         （+merge 声明）
├── service/impl/AiCandidateServiceImpl.java（+merge 实现）
├── service/impl/RuleAiServiceImpl.java     （跨模块标记/惩罚/警示）
└── controller/AiController.java            （+merge 端点、查询加权限）

frontend/src/
├── api/ai.js                               （+mergeCandidate）
└── views/ai/AiCandidateView.vue            （合并按钮 + 合并弹窗）
```

## 验证环境

后端离线编译通过并已重启（8081，原配置 rule + fallback 开启）；前端 5173 HMR 生效。验证产生的测试数据（反馈 #80–82、候选 #67–68 等）留在库中，可在前端复核。
