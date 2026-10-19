# AI 大模型接口校验报告

- 校验日期：2026-10-19
- 校验环境：dev（crane-mysql-dev，后端 7777）
- 校验方式：直连 curl 三连发（连通性/稳定性/参数传递）+ 后端 `/ai/diagnose` 诊断接口 + E2E 第 19 节全链路（`scripts/e2e-smoke.sh`）
- 结论：**接口连通、参数传递正确、响应稳定，已具备生产使用条件**

## 一、当前接入配置（sys_config / ai 组，后台「系统配置-AI 服务配置」可编辑）

| 配置键 | 当前值 | 说明 |
|---|---|---|
| ai.enabled | true | **发现并已修复拼写错误**：原值为 `ture`，导致总开关实际关闭，已修正为 `true` |
| ai.api_base_url | https://api.deepseek.com | DeepSeek 官方 OpenAI 兼容入口，客户端自动拼接 `/chat/completions` |
| ai.api_key | sk-4bd5…（已配置，仅后台可见） | 有效，鉴权通过 |
| ai.model | deepseek-flash | 有效；注意该模型为**推理模型**（响应含 reasoning_content 思考过程 + content 正文），后端仅取 content |
| ai.timeout_seconds | 30 | 单次调用超时 |
| ai.mock | false | 演示模式开关，开启后不调外部接口返回固定文本 |
| ai.report_prompt | 默认日报模板（521 字） | **提示词后台可编辑**（见第四节） |

## 二、连通性验证（真实调用 DeepSeek）

| 项 | 结果 |
|---|---|
| HTTP 状态 | 200 |
| 首包延迟 | 0.72s（max_tokens=10 截断测试） |
| 完整生成延迟 | 1.00s / 1.15s / 1.40s（3 连发） |
| 成功率 | 3/3 |
| finish_reason | stop（正常收束，无截断） |
| 响应结构 | OpenAI 兼容：`choices[0].message.content` ✓、`usage.total_tokens` ✓ |

## 三、端到端生成验证（E2E 第 19 节，109/109 通过）

1. **开关语义**：`ai.enabled=false` 时手动生成被拒，返回 50005「AI 服务未开启，请在后台打开 ai.enabled」✓，同时落 FAILED 简报记录（可追溯）✓
2. **mock 通道**：`ai.mock=true` 时不调外部接口返回固定演示文本，SUCCESS 落库 ✓（无 Key 环境全流程演示用）
3. **真实生成**：恢复真实调用后生成成功，`call_status=SUCCESS`，正文 599 字，结构与提示词要求完全一致（今日概览/工单执行/报警与风险/异常与需求/明日建议五节，数据全部引用输入值）✓
4. **诊断接口**：`GET /ai/diagnose` 返回配置检查 + 连通测试 + 近 5 次生成状态序列 ✓

真实生成样例（节选）：

> ## 今日概览
> - 今日新增工单 49 单；当前未处理报警 40 条；异常上报 29 条；待处理需求单 5 条。
> ## 明日关注建议
> 1. 早班优先集中派单：针对 33 单待接单和 40 条未处理报警，按超时分钟数从高到低排序处置…

## 四、接口清单（前后端字段已对齐）

| 接口 | 方法 | 权限 | 说明 |
|---|---|---|---|
| /api/v1/ai/reports/generate | POST | ADMIN | 手动生成今日日报，出参 `{report, promptDigest}`；promptDigest 为输入数据快照，供核对参数传递 |
| /api/v1/ai/reports/latest | GET | ADMIN | 最近一条报告，出参 `{latest: ReportVO \| null}` |
| /api/v1/ai/diagnose | GET | ADMIN | 连通性诊断，出参 `{enabled, mock, baseUrlConfigured, apiKeyConfigured, modelConfigured, model, ok, latencyMs, reason, recentStatuses}` |

ReportVO 字段：`id / briefingNo / type / periodStart / periodEnd / contentMarkdown / callStatus(SUCCESS|FAILED|SKIPPED) / failReason / generatedAt / expiresAt`。前端封装：`frontend/packages/shared/src/api/ai.ts`，管理端工作台与大屏共用。

## 五、已知注意事项

1. **deepseek-flash 为推理模型**：思考过程在 `reasoning_content`，正文在 `content`；客户端只取 content，无需前端处理思考文本。若后续换成非推理模型（如 deepseek-chat）同样兼容。
2. **超时保护**：单次调用超时 ai.timeout_seconds（默认 30s）；生成期间前端按钮 loading，失败会落 FAILED 记录并可重试。
3. **幂等与频次**：手动生成未限频（每次点击都会真实调用并产生费用）；如需限频可在二期加 daily_call_limit 控制。
4. **api_key 安全**：仅存 sys_config 且不下发前端；系统配置页以密码框脱敏展示。
