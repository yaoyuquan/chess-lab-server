# CLAUDE.md

斗兽棋（Jungle Chess）对弈平台的后端服务。前端（React + TypeScript）在另一个仓库。

## 常用命令

```bash
mvn test                                        # 45 个单测，全部离线，不打真实模型
mvn spring-boot:run                             # 起在 8080
ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run
mvn test -Dtest=LlmClientRegistryTest           # 跑单个测试类
```

需要 **JDK 25**。本机全局 `JAVA_HOME` 若指向低版本，用 `JAVA_HOME=/path/to/jdk-25 mvn ...` 覆盖。

## 这个服务是什么

**无状态 AI 服务**。它不实现斗兽棋规则，也不判胜负——规则引擎完整地长在前端。

每个 AI 回合，前端把当前棋盘和**它自己算好的合法着法列表**发过来，后端把这些着法塞进提示词，
让模型返回一个编号。所以：

- **不要在后端补规则逻辑**（走法生成、吃子判定、胜负判定）。规则只有一份实现，在前端。
  `JungleBoard` / `FallbackPicker` 里的棋子价值、兽巢位置这类常量只服务于兜底启发式，不是规则引擎。
- **后端永远返回一条合法着法**。模型返回越界编号、调用失败、没配密钥、配置写错——全部落到
  `FallbackPicker` 的启发式兜底，响应里 `fallback: true`。对局不会因为模型抽风而卡死。

## 代码地图

```
src/main/java/com/yaoyuquan/jungle/
├── config/   AiProperties（jungle.ai.* 配置绑定）、AiPlayer、ProviderConfig
├── ai/       AiMoveService（决策主流程）、PromptBuilder、BoardRenderer、FallbackPicker
├── llm/      LlmClient 接口 + 三个实现 + LlmClientRegistry（具名连接池）
└── web/      AiController、GlobalExceptionHandler、dto/
```

主流程读 `AiMoveService.decide()` 一个方法就够，它串起了上面所有东西。

## 三类模型客户端

`jungle.ai.providers` 下每条**具名连接**建一个客户端实例，棋手用 `provider` 字段引用，
多个棋手共享同一实例。`ProviderConfig.type` 决定建哪个：

| type | 实现 | 请求 | 形态 |
|---|---|---|---|
| `anthropic` | `AnthropicLlmClient` | 官方 Java SDK，`POST {base-url}/v1/messages` | 对话型，读 `ChatPrompt` |
| `openai` | `OpenAiCompatibleLlmClient` | `RestClient` 直连 `POST {base-url}/chat/completions` | 对话型，读 `ChatPrompt` |
| `jev` | `JevLlmClient` | TypeSafe System One，`POST /systemone` | **判断型**，读 `JevPrompt` |

Jev 不生成文本，而是在给定候选项里做带概率的判断，因此同一个局面要摆成两种形状——
`PromptBuilder` 同时产出 `ChatPrompt` 和 `JevPrompt`，`MoveQuery` 一起带下去，由客户端各取所需。
加新的服务商时这条路要一起铺。

## 改动时要守的几条

- **配置出错要当场炸，别静默降级。** `type` 拼错、棋手漏配 `provider` → 启动失败并列出可选值
  （`LlmClientRegistry` 构造函数与 `create()`）。理由是几种格式报文完全不同，默默退回一种
  只会表现为一个莫名其妙的 404。但**运行期**出错（缺密钥、引用了不存在的连接名）不中断对局，走兜底。
- **不设超时、不重试。** 推理型模型一手棋 170–220s 是常态，砍断只会白等一场再兜底；
  重问一次是再等一整轮推理。只有建连保留 30s 上限。别"顺手"加 retry 或 readTimeout。
- **请求体与原始响应全量打日志，没有开关**（`LlmPayloadLogger`，前缀是连接名），
  另有每次调用的耗时与 token 用量。密钥类请求头打印前只留头尾（`Bearer` 这类前缀保留）。
  这是实验项目，排查时看到原始报文比日志干净重要。
- **`effort` / `temperature` 不是通用字段**：`effort` 只对 `openai`（映射成 `reasoning_effort`）
  和 anthropic 生效，两者对 Jev 都不适用，配了会被忽略。

## 代码规约

- Java 25 + Spring Boot 4。JSON 用 **Jackson 3**：`tools.jackson.databind.ObjectMapper`，
  不是 `com.fasterxml.jackson`。
- DTO 一律用 `record`，放 `web/dto/`。
- 构造器注入，不用 `@Autowired` 字段注入。
- **每个类都要有类注释，且带 `@author yaoyuquan`。**
- **注释必须独占一行**，不允许和代码同行。类注释里多写"为什么这么定"，别复述代码在做什么。
- 日志、异常消息、注释一律中文。
- 测试用 JUnit 5 + AssertJ，每个 `@Test` 配一句中文 `@DisplayName`。
  单测必须离线：模型调用用本地假服务或直接构造客户端，不打真实接口。
