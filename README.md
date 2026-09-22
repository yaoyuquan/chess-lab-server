# 斗獸棋 Jungle Chess Lab · Server

斗兽棋（Jungle Chess）对弈平台的**后端服务**。前端（React + TypeScript）在另一个仓库。

## 架构

本仓库是纯后端工程：Java 25 + Spring Boot 4 + Maven，对外只暴露两个 HTTP 接口，
不含任何前端资源。技术坐标：

| | |
|---|---|
| 构建 | Maven，`com.github.chess:chess-lab-server` |
| 基础包 | `com.github.chess` |
| 运行 | 内嵌 Tomcat，默认 8080 |
| 形态 | **无状态**：不存棋局、不接数据库，每次请求自带完整局面 |

职责只有一件事：**棋手人设 / 提示词配置 + 调用模型在候选着法里选一步**，
棋手可分属不同服务商（Claude / OpenAI 兼容 / Jev）。

规则与 AI 分居两端：每一手 AI 回合，前端把当前棋盘和自己算好的**合法着法编号列表**发给后端，
后端把它们塞进提示词让模型返回一个编号。后端不生成着法、不判胜负，因此规则永远只有一份实现。

**对局永不卡死**：模型返回越界编号或调用失败时直接用启发式兜底
（入巢 > 吃高价值子 > 向对方兽巢推进），响应里 `fallback: true`，前端在对局记录里标「兜底」角标。
**没有配置 API Key 也能完整游玩**，此时全部着法走兜底。

## 快速开始

需要 **JDK 25**。

```bash
# 默认 8080
ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run
```

不设 `ANTHROPIC_API_KEY` 也能启动，AI 会走兜底着法。前端默认跑在 5173 并代理 `/api` 到这里。

> 本机全局 `JAVA_HOME` 若指向低版本 JDK，用 `JAVA_HOME=/path/to/jdk-25 mvn ...` 覆盖。

## 配置

棋手写在 `src/main/resources/application.yml` 的 `chess.ai.players` 下，
**正好两个**——一场对局最多就两个 AI：人机对战挑其中一个当对手，
观战模式就是这两个互下。

| 棋手 | id |
|---|---|
| 青云 | `qingyun` |
| 玄机 | `xuanji` |

**两个席位完全对等**：没有难度分级，也没有棋风之分，`system-prompt` 一字不差
（yml 里用 YAML 锚点 `&ai-system-prompt` / `*ai-system-prompt` 绑在一起，改一处两边都变）。
唯一的区别是各自指向哪条连接、哪个模型——把两个席位的 `provider` 指到不同家，
观战模式就成了两个模型的对局。每个席位一套 `provider / model / effort / temperature`。

### 切换服务商

`chess.ai.providers` 是一条条**具名连接**，棋手用 `provider` 字段引用其中一条。
不同棋手可以分属不同服务商，同一连接下的棋手共享一个客户端实例：

```yaml
chess:
  ai:
    providers:
      claude:
        type: anthropic
        base-url: ${ANTHROPIC_BASE_URL:}      # 留空走官方地址，填上可走中转
        api-key: ${ANTHROPIC_API_KEY:}
      gpt:
        type: openai
        base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
        api-key: ${OPENAI_API_KEY:}
      jev:
        type: jev                             # TypeSafe System One，判断型模型
        base-url: ${TYPESAFE_BASE_URL:https://api.typesafe.ai/v1}
        api-key: ${TYPESAFE_API_KEY:}
    players:
      - id: qingyun
        provider: claude                      # 必填，漏了不让起服务
        model: claude-opus-5
        effort: xhigh
      - id: xuanji
        provider: gpt
        model: gpt-4o
        temperature: 0.6                      # temperature 仅 OpenAI 兼容接口生效
```

于是观战模式可以直接让 **Claude 对 GPT**、**Claude 对 Jev**、**GPT 对 Jev**。
同一服务商下想走两条不同中转，也只要多定义一条连接。

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export OPENAI_API_KEY=sk-...
export TYPESAFE_API_KEY=...
export OPENAI_BASE_URL=https://your-endpoint/v1   # 可选
```

#### 接一家新的服务商

没有专门的「自定义」档位：直接在 `providers` 下添一条**有名字的**连接就行。
名字要能说明它是什么，因为棋手那边看到的就是这个名字：

```yaml
    providers:
      kimi:
        type: openai
        base-url: ${KIMI_BASE_URL:https://api.moonshot.cn/v1}
        api-key: ${KIMI_API_KEY:}
    players:
      - id: xuanji
        provider: kimi
        model: kimi-k2
```

`type` 就是接口格式。同一个地址换个 `type` 就换一套协议，两者的报文完全不同：

| 格式 | 实际请求 | 鉴权头 | base-url 写法 |
|---|---|---|---|
| `openai` | `POST {base-url}/chat/completions` | `Authorization: Bearer <key>` | **要带** `/v1` |
| `anthropic` | `POST {base-url}/v1/messages` | `x-api-key: <key>` | **不带** `/v1`，SDK 自己补 |

两个容易踩的坑，启动日志都会明说：

- `type` 拼错（比如写成 `openai-compatible`）**直接启动失败**并列出可选值。
  几种格式的报文完全不同，若默默退回某一种，表现出来只是个莫名其妙的 404。
- `openai` 格式配了密钥却没配 `base-url` 时会 WARN 一句——那样请求会发往
  OpenAI 官方地址，等于把第三方密钥送错了门。（`anthropic` 格式留空是官方地址，属正常用法。）

走中转也是同一回事：另开一条连接指向中转地址即可，
同一家服务商的两条不同线路可以共存。

#### Jev：判断型模型，不是对话模型

`type: jev` 走 TypeSafe 的 System One 接口（`POST /systemone`）。它和前两者是**两种东西**：
Claude / GPT 这类对话模型生成一段文本，我们再从中解析出编号；
Jev 不生成文本，而是在给定的候选项里做一个**带概率的判断**。

所以同一个局面要摆成两种形状，由 `PromptBuilder` 分别产出：

| | 对话型模型 | Jev |
|---|---|---|
| 输入 | system + user 提示词 | `state`（局面）+ `instructions`（任务与规则）+ `criteria`（候选着法） |
| 原语 | 结构化输出 `{index, reason}` | `choice`，criteria 上限 255 项（着法最多约 40 条，够用） |
| 输出 | 一段 JSON 文本 | `choice` + `probabilities` + `confidence` |
| 会不会答非所问 | 会，可能编出不存在的编号，所以要校验，越界就兜底 | **不会**，只能在 criteria 里选 |

候选着法的 key 是 `move_<编号>`，`JevPrompt.optionKey / indexOf` 负责两边还原。
Jev 没有文字理由，对局记录里的理由由概率分布合成：

```
Jev 判断，置信度 0.64，本手概率 0.71，次选 豹 E3→D3 0.22
```

`effort`、`temperature` 对 Jev 都不适用，配了也会被忽略；`model` 留空则用 `jev-latest`。

#### openai 这条路按官方字段来

`type: openai` 的连接一律发官方那套：`response_format` 是完整的 `json_schema` + `strict: true`，
思考深度是 `reasoning_effort`（取棋手的 `effort`，没配就整个字段不带）。这两个字段不做成可配的：
对端不认，说明接错了地方，该当场报出来，而不是留个开关让人一档档试。

接中转时若碰上只认 `json_object` 的服务，改 `OpenAiCompatibleLlmClient.buildBody` 即可——
提示词里已经写死了输出约定，解析器也兼容模型裹的 Markdown 围栏，降档不影响能不能用。

#### 超时与重试：都没有，一直等

调模型不设超时，也不重试——不超时、不重问，一直等到模型把话说完：

| 模型类型 | 实测一步棋耗时 | 备注 |
|---|---|---|
| 不带思考的对话模型 | ~12s | |
| 带长思考的推理模型 | **~170–220s** | 一步棋烧掉 9000+ reasoning token |

推理型模型一手棋想几分钟是常态，砍断只会白等一场再落到兜底；重问一次同样是再等一整轮推理，
换来的多半还是同样的答案。代价是卡住的请求会一直占着 Tomcat 线程，
建连仍有 30s 上限（地址写错或对端不可达要能立刻报出来）。
观战模式用推理型模型会非常慢，建议配不带思考的模型。

模型返回越界编号或调用失败，这一手直接走启发式兜底。

#### 排查：打印请求与响应

发给模型的**完整请求体**与模型返回的**原始响应体**一律打进日志（没有开关），前缀是连接名：

```
[gpt] → 请求 https://api.openai.com/v1/chat/completions
{"model":"gpt-4o","temperature":1.0,"messages":[...]}
[gpt] ← 响应 HTTP 200
{"id":"...","choices":[{"message":{"content":"{\"index\": 3, ...}"}}]}
```

请求头里的密钥打印前只留头尾（`Bearer` 这类方案前缀保留），另有每次调用的耗时与 token 用量。

Anthropic 走官方 Java SDK，OpenAI 兼容走 `RestClient` 直连 `/chat/completions`，
两者都用结构化输出约束模型只返回 `{index, reason}`。

**配置写错不会中断对局**：某条连接缺密钥、或棋手引用了不存在的连接名，
该棋手直接走启发式兜底，启动日志里会把每条连接的状况和拼错的引用打出来。

## 接口

完整的字段说明、错误码与调用约定见 **[doc/api.md](doc/api.md)**。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/ai/models` | 棋手清单，与棋种无关 |
| POST | `/api/jungle/ai/move` | 斗兽棋：在候选着法里选一条 |

```jsonc
// POST /api/jungle/ai/move
{
  "playerId": "xuanji",
  "side": "r",
  "board": [[{"rank":7,"side":"r"}, null, "…"], "…"],   // 9 行 × 7 列，空格为 null
  "moveNumber": 12,
  "legalMoves": [{ "i": 0, "from": [2,0], "to": [3,0], "text": "鼠 A7→A6 入水" }]
}
// → { "index": 0, "reason": "先让鼠下水封住对方通路。", "fallback": false }
```

## 棋盘坐标

`board[行][列]`，行 0 是红方底线（红巢 `(0,3)`），行 8 是蓝方底线（蓝巢 `(8,3)`），**蓝方先行**。
棋谱坐标列用 A~G、行自下而上记 1~9，故 `(0,3)` 记作 `D9`。

## 测试

```bash
mvn test     # 45 个用例：连接池路由、必填校验、Jev 请求形状、兜底选择、提示词构造、降级、响应解析
```

## 目录

基础包 `com.github.chess`：

```
src/main/java/com/github/chess/
├── config/   棋手与服务商配置绑定
├── llm/      LlmClient 接口、具名连接池 LlmClientRegistry，以及三家共用的件；
│             每家服务商一个子包
│   ├── claude/  AnthropicLlmClient
│   ├── gpt/     OpenAiCompatibleLlmClient
│   └── jev/     JevLlmClient、JevPrompt
├── web/      与棋种无关的接口层与异常处理
└── jungle/   斗兽棋一整条链路：棋盘知识、渲染、提示词、兜底、决策服务、
              自己的控制器与 DTO。加围棋就在旁边新建 go/
```
