# CLAUDE.md

AI 对弈平台的后端服务。目前只支持斗兽棋（Jungle Chess），后续要加围棋与国际象棋。
前端（React + TypeScript）在另一个仓库。

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
  `jungle/JungleBoard` / `jungle/FallbackPicker` 里的棋子价值、兽巢位置这类常量
  只服务于兜底启发式，不是规则引擎。新棋种也守这条。
- **后端永远返回一条合法着法**。模型返回越界编号、调用失败、没配密钥、配置写错——全部落到
  `FallbackPicker` 的启发式兜底，响应里 `fallback: true`。对局不会因为模型抽风而卡死。

## 代码地图

```
src/main/java/com/github/chess/
├── config/   AiProperties（chess.ai.* 配置绑定）、AiPlayer、ProviderConfig
│             UnknownPlayerException / UnknownProviderException
├── llm/      LlmClient 接口、LlmClientRegistry（具名连接池）、MoveQuery / ChatPrompt
│   │         / JsonHttpClient / LlmPayloadLogger 等三家共用的件
│   ├── claude/  AnthropicLlmClient
│   ├── gpt/     OpenAiCompatibleLlmClient
│   └── jev/     JevLlmClient、JevPrompt
├── web/      与棋种无关的部分：AiController（GET /api/ai/models 棋手清单）、
│             GlobalExceptionHandler、dto/（AiPlayerView、ApiError）
└── jungle/   斗兽棋一整条链路
    ├── JungleBoard         水域 / 兽巢 / 陷阱 / 子力价值
    ├── BoardRenderer       9×7 网格图 + 子力清单
    ├── PromptBuilder       斗兽棋规则文本与人设
    ├── FallbackPicker      入巢 > 吃子 > 推进 的启发式
    ├── AiMoveService       decide() 决策主流程
    └── web/   AiController（POST /api/jungle/ai/move）、dto/
```

斗兽棋主流程读 `jungle/AiMoveService.decide()` 一个方法就够，它串起了上面所有东西。

## 加一个新棋种

`llm` / `config` 是通用的，`web` 只放与棋种无关的东西（棋手清单、异常处理、ApiError）。
**一个棋种一个顶层包**：照着 `jungle/` 新建 `go/` 或 `chess/`，把棋盘知识、渲染、提示词、
兜底、决策服务、控制器与请求 DTO 全放进去，路径挂 `/api/<棋种>/ai/move`。

现在**没有**棋种抽象层，也先别急着抽：围棋没有 from/to，国际象棋有易位和升变，
候选着法的形状差别很大。等第二条链路写完，看两边真重复的部分是什么，再决定抽什么。

一个必须守的细节：**同名的 `@Component` / `@RestController` 要显式写 bean 名**。
默认 bean 名取类的短名，`jungle.web.AiController` 与将来的 `go.web.AiController`
会在启动时撞成 `ConflictingBeanDefinitionException`。斗兽棋那个已经写成
`@RestController("jungleAiController")`，新棋种照此办理，`BoardRenderer`、`PromptBuilder`、
`FallbackPicker`、`AiMoveService` 这几个重名的也一样。

## 三类模型客户端

`chess.ai.providers` 下每条**具名连接**建一个客户端实例，棋手用 `provider` 字段引用，
多个棋手共享同一实例。`ProviderConfig.type` 决定建哪个：

| type | 实现 | 请求 | 形态 |
|---|---|---|---|
| `anthropic` | `llm.claude.AnthropicLlmClient` | 官方 Java SDK，`POST {base-url}/v1/messages` | 对话型，读 `ChatPrompt` |
| `openai` | `llm.gpt.OpenAiCompatibleLlmClient` | `RestClient` 直连 `POST {base-url}/chat/completions` | 对话型，读 `ChatPrompt` |
| `jev` | `llm.jev.JevLlmClient` | TypeSafe System One，`POST /systemone` | **判断型**，读 `JevPrompt` |

Jev 不生成文本，而是在给定候选项里做带概率的判断，因此同一个局面要摆成两种形状——
`PromptBuilder` 同时产出 `ChatPrompt` 和 `JevPrompt`，`MoveQuery` 一起带下去，由客户端各取所需。
加新的服务商时这条路要一起铺。

**一家服务商一个子包**：`llm` 根包只放接口与三家共用的件，某一家独有的东西（客户端、
它自己的报文形状）收进 `llm/<家名>/`。加一家就新建一个子包，别往根包里堆。
只有一处例外：`JevPrompt` 在 `llm/jev/` 却被根包的 `MoveQuery` 引用，因为 `MoveQuery`
要把两种形状一起带下去；这是 Jev 判断型接口特有的，别照着它把别的实现细节也提到根包。

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
