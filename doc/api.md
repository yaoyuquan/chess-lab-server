# 接口文档

AI 对弈服务（`chess-lab-server`）目前支持斗兽棋与国际象棋，后续会加围棋。

**路径里带棋种**：着法决策挂在 `/api/<棋种>/ai/` 下，斗兽棋是 `/api/jungle/ai/`，国际象棋是 `/api/chess/ai/`。
棋手清单与棋种无关，留在 `/api/ai/models`。

| | |
|---|---|
| Base URL | `http://localhost:8080`（端口见 `server.port`） |
| 请求 / 响应体 | `application/json`，UTF-8 |
| 鉴权 | 无。模型密钥在服务端配置，不经过调用方 |
| 跨域 | 只放行 `http://localhost:5173` 与 `http://127.0.0.1:5173`，方法限 `GET` / `POST`（`WebConfig`） |

## 先读这一段：调用方要理解的三件事

**1. 合法着法由调用方算，后端只做选择。** 后端不实现斗兽棋规则，不生成走法、不判吃子、不判胜负。
每次请求要把当前局面**和你自己算好的全部合法着法**一起发过来，后端把这些着法编号塞进提示词，
让模型返回其中一个编号。规则因此只有一份实现，在前端。

**2. 这个接口几乎不会失败。** 模型返回越界编号、调用超时失败、没配密钥、连接名写错——
全部落到启发式兜底，仍旧返回一条合法着法，只是响应里 `fallback: true`。
会返回错误码的只有调用方自己的问题：棋手 id 不存在、请求体不合法。

**3. 没有超时，一手棋可能要等 3 分钟以上。** 服务端调用模型时不设读超时、不重试。
带长思考的推理模型实测一手 **170–220s**；不带思考的对话模型约 12s。
**调用方务必把自己的 HTTP 超时放宽**，否则会在服务端还在等模型时先行断开。

## 坐标系约定

`board[行][列]`，9 行 × 7 列。

- 行 `0` 是红方底线，红巢在 `(0, 3)`；行 `8` 是蓝方底线，蓝巢在 `(8, 3)`
- **蓝方先行**
- 陷阱：红方 `(0,2)` `(0,4)` `(1,3)`，蓝方 `(8,2)` `(8,4)` `(7,3)`
- 水域：第 3~5 行的第 1、2、4、5 列，共两片
- 棋谱坐标另算：列用 `A`~`G`（左起），行自下而上记 `1`~`9`，所以 `(0, 3)` 写作 `D9`。
  棋谱坐标只出现在给模型看的提示词和 `text` 字段里，接口传参一律用 `[行, 列]`

兽力等级 `rank`：`1` 鼠、`2` 猫、`3` 狼、`4` 狗、`5` 豹、`6` 虎、`7` 狮、`8` 象。
阵营 `side`：`r` 红、`b` 蓝。

---

## GET /api/ai/models

取棋手清单，给前端的模型下拉框用。棋手在 `application.yml` 的 `chess.ai.players` 下配置，
**固定两个**——一场对局最多两个 AI。

无请求参数。

**200 响应**

```json
[
  { "id": "qingyun", "name": "青云", "code": "QINGYUN" },
  { "id": "xuanji",  "name": "玄机", "code": "XUANJI" }
]
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 棋手标识，调用 `/move` 时作为 `playerId` 传回 |
| `name` | string | 中文名 |
| `code` | string | 英文代号 |

提示词、模型 id、所属连接、密钥状态都属于服务端细节，**不下发**（`AiPlayerView`）。
所以清单里出现某个棋手，不代表它背后的模型此刻可用——不可用时 `/move` 照常返回着法，只是走兜底。

---

## POST /api/jungle/ai/move

在调用方给出的候选着法里选一条。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `playerId` | string | 是 | 棋手 id，取自 `/models` |
| `side` | string | 是 | AI 执哪一方，只能是 `r` 或 `b` |
| `board` | array | 是 | 当前棋盘，9 行 × 7 列的二维数组，空格为 `null` |
| `board[r][c]` | object \| null | — | `{ "rank": 1-8, "side": "r"\|"b" }` |
| `moveNumber` | int | 否 | 当前第几手，只用于在提示词里给模型一点节奏感 |
| `legalMoves` | array | 是 | 全部合法着法，非空 |
| `legalMoves[].i` | int | 是 | 着法编号，响应的 `index` 就是这个值 |
| `legalMoves[].from` | [int, int] | 是 | 起点 `[行, 列]` |
| `legalMoves[].to` | [int, int] | 是 | 终点 `[行, 列]` |
| `legalMoves[].text` | string | 否 | 中文描述，如 `豹 C7→D7 吃狼`。**强烈建议带上**，它会原样进提示词；缺失时后端退回 `C7→D7` 这样的纯坐标，模型能读到的信息变少 |

关于 `i`：响应里的 `index` 是**某条着法的 `i` 值**，不是数组下标。按 0 起连续编号时两者恰好一致，
但后端只校验「返回的编号在 `i` 的集合里」，不要求连续。

`board` 只校验非空，不强制 9×7。尺寸不对不会报错，越界的格子一律当空格读，
但棋盘渲染和兜底评分都会跟着失真，请按 9×7 传。

**200 响应**

```json
{ "index": 0, "reason": "先让鼠下水封住对方通路。", "fallback": false }
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `index` | int | 选中的着法编号，必定是请求里某条 `legalMoves[].i` |
| `reason` | string | 选择理由，一句中文 |
| `fallback` | boolean | `true` 表示这一手不是模型选的，是启发式兜底 |

### fallback 为 true 时

`reason` 会说明是哪一种，可以直接展示给用户：

| `reason` | 起因 |
|---|---|
| `AI 未给出有效着法，已按子力价值兜底。` | 模型返回的编号不在候选里，或调用失败（网络、鉴权、响应解析不了） |
| `未配置模型密钥，已按子力价值兜底。` | 该棋手所属的连接没配 api-key |
| `棋手所属的模型连接不可用，已按子力价值兜底。` | 棋手的 `provider` 引用了不存在的连接名 |

兜底用的是启发式打分（`FallbackPicker`）：入巢 > 吃高价值子 > 向对方兽巢推进，并避开对方陷阱，
同分随机打散。它和模型走的是同一份候选列表，返回的着法一样合法。

**模型只问一次，不重试。** 重试就是再等一整轮推理，答案多半还是同样的，不如直接兜底。

### 完整示例

```jsonc
// POST /api/jungle/ai/move
{
  "playerId": "xuanji",
  "side": "r",
  "board": [
    [{"rank":7,"side":"r"}, null, null, null, null, null, {"rank":6,"side":"r"}],
    // … 共 9 行，每行 7 列，空格为 null
    [{"rank":6,"side":"b"}, null, null, null, null, null, {"rank":7,"side":"b"}]
  ],
  "moveNumber": 12,
  "legalMoves": [
    { "i": 0, "from": [2, 0], "to": [3, 0], "text": "鼠 A7→A6 入水" },
    { "i": 1, "from": [0, 0], "to": [1, 0], "text": "狮 A9→A8" }
  ]
}
```

```json
{ "index": 0, "reason": "先让鼠下水封住对方通路。", "fallback": false }
```

---

## POST /api/chess/ai/move

国际象棋：在调用方给出的候选着法里选一条。调用约定与斗兽棋完全一样——合法着法由调用方算、
几乎不会失败、没有超时，只是局面和着法换成了国际象棋的标准记法。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `playerId` | string | 是 | 棋手 id，取自 `/models` |
| `side` | string | 是 | AI 执哪一方，只能是 `w`（白）或 `b`（黑） |
| `fen` | string | 是 | 当前局面的完整 FEN，六个字段都带上：易位权、吃过路兵格、五十步计数都从这里读 |
| `history` | string[] | 否 | 开局至今的 SAN 着法序列，如 `["e4", "e5", "Nf3"]`。**从标准初始局面、白方先走开始记**，后端按这个假设排成 `1. e4 e5 2. Nf3` 给模型看；模型靠它看来路、留意三次重复 |
| `legalMoves` | array | 是 | 全部合法着法，非空 |
| `legalMoves[].i` | int | 是 | 着法编号，响应的 `index` 就是这个值 |
| `legalMoves[].uci` | string | 是 | UCI 记法，如 `e2e4`、`e7e8q`（升变带第五位）、`e1g1`（易位写王的起落格） |
| `legalMoves[].san` | string | 是 | SAN 记法，如 `Nf3`、`exd8=Q+`、`O-O`、`Qxf7#`。**`x` `+` `#` 标记必须带全**：提示词让模型直接采信这些标记，兜底也靠它们识别吃子、将军与将杀 |

`chess.js` 的 `moves({ verbose: true })` 同时给出 `lan`（即 UCI）和 `san`，可以直接拿来填。

`fen` 只校验非空。写坏了不会报错，看不懂的部分当空格读，但渲染和兜底评分会跟着失真。

**200 响应**：与斗兽棋相同，`{ "index", "reason", "fallback" }`，`fallback: true` 时的三种 `reason` 也一样。

兜底启发式（`chess.FallbackPicker`）：将杀 > 升变 > 吃子（先吃价值高的，同样吃用价值低的子去吃）
> 将军 > 易位 > 往中心走，并避免无故走王，同分随机打散。它不算「吃完会不会被吃回」——那要算攻击关系，
等于在后端再写一份规则。

### 完整示例

```json
{
  "playerId": "xuanji",
  "side": "b",
  "fen": "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
  "history": ["e4", "e5", "Nf3"],
  "legalMoves": [
    { "i": 0, "uci": "b8c6", "san": "Nc6" },
    { "i": 1, "uci": "d7d6", "san": "d6" }
  ]
}
```

```json
{ "index": 0, "reason": "出马保护 e5 兵。", "fallback": false }
```

---

## 错误响应

非 2xx 一律是这个结构（`ApiError`）：

```json
{ "code": "UNKNOWN_PLAYER", "message": "未知的棋手：wukong" }
```

| HTTP | `code` | 触发条件 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 字段校验没过。`message` 是第一条错误，形如 `side 必须匹配"[rb]"`、`fen 不能为空` |
| 400 | `MALFORMED_BODY` | 请求体不是合法 JSON |
| 404 | `UNKNOWN_PLAYER` | `playerId` 在配置里找不到 |
| 404 | `NOT_FOUND` | 路径不存在 |
| 405 | `METHOD_NOT_ALLOWED` | 方法不对，比如对 `/api/jungle/ai/move` 发 GET |
| 4xx | HTTP 状态名 | 其余由 Spring MVC 判定的请求错误，`code` 取状态名，如 `UNSUPPORTED_MEDIA_TYPE` |
| 500 | `INTERNAL_ERROR` | 没预料到的服务端故障。`message` 固定是 `服务内部错误`，细节只进日志 |

再强调一次：**模型侧的任何问题都不会走到这里**，它们表现为 200 + `fallback: true`。

## 排查

服务端把发给模型的**完整请求体**和模型的**原始响应体**全量打进日志，没有开关，
前缀是连接名（`LlmPayloadLogger`），另有每次调用的耗时与 token 用量。请求头里的密钥只留头尾几位。

```
[gpt] → 请求 https://api.openai.com/v1/chat/completions
  Authorization: Bearer sk-abc***7890
{"model":"mimo-v2.6-pro","messages":[…]}
[gpt] ← 响应 HTTP 200
{"id":"…","choices":[{"message":{"content":"{\"index\": 3, …}"}}]}
```

某一手拿到了 `fallback: true`，去日志里按棋手 id 和连接名找这一轮的报文，能直接看出是哪一环断的。
