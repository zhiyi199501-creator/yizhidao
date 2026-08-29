# AI 问答

本文件说明结果页「问答」怎么拼材料、出卡、App 怎么用。接口路径与 JSON 字段以 `[backend-min-spec.md](backend-min-spec.md)` 为准；提示词原文以 `backend/app/services/ai.py` 为准。改解卦通则先改 App 的 `ReadingGuide` 并补测，再对这里的焦点表。

**状态（2026-08-28）**：扩卡、黄庭进 prompt、按爻裁案例、主看卦辞时附彖辞已合 `main`（[PR #12](https://github.com/zhiyi199501-creator/yizhidao/pull/12)）。生产 `api.yiwanjia.work` 仍是发版前的三字段解读（`summary` / `focus` / `advice` + 追问单段 `reply`）；须重建镜像（含 `ImaExplanations.json`）并发 App 后才现役。

这不是对话 agent，也不是多跳 RAG：本地起卦算完卦象，后端一次 Chat Completions，强制 JSON。密钥只在服务端。

## 用户路径

结果页悬浮 **问**：该占已有问答则直接打开；没有则自动 `POST /v1/ai/analyze`（需登录）。页标题「问答」。卡片：事情背景 / 当下 / 方向 / 建议 / 可以接着问。须防只一条，并入建议（条目前加「须防：」），接口仍单独返回 `risks`。长文由 App `AIAnswerFormatter` 在展示层按句分段，不改存盘。问答详情右上角「同类」与结果页相同（进历史同卦明细）。App 起卦所问必填；接口仍接受空所问（旧记录），空时「可以接着问」固定为「我的事业会如何？」「我的感情会如何？」。点短问即发出（不填入输入框），或自写后发 → `POST /v1/ai/followup` → 回复 + 这一轮建议；最新一轮仍给「可以接着问」（须是用户口吻，用「我」）。初次的短问在已有追问后藏掉。问答自动保存到本地「问答」Tab（一占一条）；追问成功会更新该条。

旧客户端只读 `summary` / `focus` / `advice` 和追问 `reply`，多出的字段可忽略。

## 配置

`AI_MODE=mock|openai`。`openai` 表示 **OpenAI 兼容** Chat Completions，不表示必须用 OpenAI。本机常用 DeepSeek：`OPENAI_BASE_URL` / `OPENAI_MODEL` / `OPENAI_API_KEY` 以 `backend/.env` 为准（勿提交）。默认超时 90 秒。`mock` 不耗 token，只保证字段形状。

经文 / 讲解 / 案例默认读 `ios/Yizhidao/Resources/` 下同名 JSON。Docker：`Hexagrams.json` 在 data 卷；`cases.json` 与 `ImaExplanations.json` 默认在镜像 `/app/app/data/`，若 data 卷有同名文件则优先。可用 `HEXAGRAMS_PATH` / `CASES_PATH` / `IMA_EXPLANATIONS_PATH` 覆盖。

## 解读框架


| 材料                     | 卡片                     |
| ---------------------- | ---------------------- |
| 卦辞（彖助理解格局）             | 事情背景 `summary`         |
| 大象辞                    | 方向 `direction`         |
| 动爻爻辞 / 小象（无动爻则卦辞 + 大象） | 当下 `focus`             |
| 戒惧与可做                  | 建议 `advice`（展示时并入 `risks`） |
| 用户可直接发出的短句             | 可以接着问 `askNext`        |


黄庭讲解用来读懂辞义，禁止整段照抄。讲习案例只作取象，禁止把案例原事或结论套到用户身上。彖与卦辞讲解勿写成两套背景。正文不要重复卡片标题。

动爻位 1=初 … 6=上。二动「上动」= 两动爻中位次较高者；四动「下静」= 之卦两静爻中位次较低者。与结果页「主看」一致：


| 动爻数 | 主看            | App 是否标「主看」 |
| --- | ------------- | ----------- |
| 0   | 本卦卦辞          | 标           |
| 1   | 本卦该爻          | 不标          |
| 2   | 本卦两动爻，以上动为主   | 标上动         |
| 3   | 本卦、之卦卦辞，以本卦为主 | 标           |
| 4   | 之卦两静爻，以下静为主   | 标           |
| 5   | 之卦静爻          | 标           |
| 6   | 之卦卦辞          | 标           |




## 初次 prompt 拼什么

`_build_prompt` 顺序：起卦方式、所问、本卦经文、之卦经文（若有）、动爻位、解卦焦点、黄庭讲解、讲习案例、框架提醒。

**经文块**（本卦、之卦各一块，《易经证释》所引）：卦名、卦辞、彖辞、大象、六爻辞、六小象。不附文言、用九、用六。

**黄庭讲解**（`explanation_slots` → `ImaExplanations.json`，清洗与 App `ImaAnswerFormatter` 相同：去「思考过程」、出处脚注）：


| 动爻数 | 必给                                           | 另给                         |
| --- | -------------------------------------------- | -------------------------- |
| 任意  | 本卦卦辞 `{nn}-guaci`（事情大背景）、本卦大象 `{nn}-daxiang` |                            |
| 0   |                                              | 本卦彖 `{nn}-tuanci`          |
| 1   |                                              | 本卦该爻 `{nn}-yao-{0…5}`（初=0） |
| 2   |                                              | 本卦两动爻，上动标「主」               |
| 3   |                                              | 本卦彖；之卦卦辞                   |
| 4   |                                              | 之卦两静爻，下静标「主」               |
| 5   |                                              | 之卦静爻                       |
| 6   |                                              | 之卦卦辞、之卦彖、之卦大象              |


不注入：文言、用九用六、非焦点爻的讲解。1/2/4/5 动不灌黄庭彖辞（经文块里已有彖原文）。

**讲习案例**（`cases_for_ai_prompt`，最多 3 则，主爻优先；`三爻、四爻` 这类标签两边都算命中）：


| 动爻数   | 案例                   |
| ----- | -------------------- |
| 0 / 3 | 不附（主看本卦卦辞，爻位案例会套错应事） |
| 1 / 2 | 本卦对应爻位；二动以上动为主       |
| 4 / 5 | 之卦静爻；四动以下静为主         |
| 6     | 不附（主看之卦卦辞）           |




## 追问

请求带同一套卦象字段 + `previousAnalysis` + `conversation`（最多用最近 10 轮）+ `message`。追问 **不再重喂** 初次 prompt：只给卦名、动爻、解卦焦点、焦点经文原文（1/2 动为对应爻辞+小象，0/3/6 动为所主看的卦辞+彖，4/5 动为之卦静爻；不附其余爻、黄庭、案例）。再追加此前解读与对话。输出 `reply` + `advice` + `askNext`；缺任一段则 502。

## App

- iOS：`ResultView` + `AuthAPI`（`YizhidaoApp.swift`）；本地 `SavedAIAnalysis.swift`（一占一条；旧 JSON 缺 `readingRecordID` 时按卦象指纹对上）
- Android：`AIAnalysisScreen.kt` + `AuthApi.kt`；本地 `SavedAIAnalysis.kt`（`readingRecordId`）
- 展示层卡片标题是中文「事情背景」等，不把 JSON 键名秀给用户。须防不单独成卡，只一条并入建议（条目前加「须防：」）
- 点「可以接着问」直接发出，不填入输入框。所问为空时这两项固定为「我的事业会如何？」「我的感情会如何？」（服务端覆盖，不靠模型）；有所问时模型须用「我」的口吻拟短问

## 限流与失败

按登录用户，进程内计数（单容器；重启清零）。`analyze` 与 `followup` 共用：两次最短间隔 8 秒；同一用户同时只跑 1 个；自然日 UTC+8 合计 40 次。超限 HTTP 429、`code` 4290。间隔/并发文案「请稍后再试」；当天次数用尽「今天的解读次数用完了，明天再来」。界面不展示剩余次数。`AI_RATE_INTERVAL_SEC` / `AI_RATE_DAILY_LIMIT` 可改。模型失败对外只说「解读没有完成，请稍后重试」或「模型服务暂时不可用，请稍后重试」，不回上游原文。不自动重试、不降级 mock。App 已有错误行。



## 代码与测试


| 路径                                                                                                       | 职责                      |
| -------------------------------------------------------------------------------------------------------- | ----------------------- |
| `backend/app/services/ai.py`                                                                             | 拼 prompt、调模型、校验 JSON    |
| `backend/app/services/ima_store.py` / `ima_format.py`                                                    | 读讲解、清洗                  |
| `backend/app/services/case_store.py`                                                                     | 案例热更新列表 + AI 筛选         |
| `backend/app/services/hexagram_store.py`                                                                 | 经文                      |
| `backend/app/routes/ai.py`                                                                               | 需登录的 analyze / followup；限流 |
| `backend/app/services/ai_rate_limit.py`                                                                  | 按用户间隔 / 当日次数 / 并发       |
| `backend/tests/test_ai_content.py` / `test_ima_prompt.py` / `test_ai_cases.py` / `test_eval_fixtures.py` | 字段、槽位、案例筛选、抽检样本         |


```bash
cd backend && .venv/bin/python -m unittest
# 本机对真实模型抽检（不进 CI；需 AI_MODE=openai 与 key）
.venv/bin/python scripts/eval_ai_reading.py          # 8 条解读 + 1 轮追问
.venv/bin/python scripts/eval_ai_reading.py --dry-run
```

样本在 `backend/tests/eval_fixtures.py`（假问题，不是用户数据）。后台「抽检」跑同一套槽位检查，可选按现役 `AI_MODE` 出卡，不写 `ai_usage_events`。CLI live 结果写到 `backend/.eval/`（gitignore，勿提交）。看：JSON 是否完整、是否扣所问、有没有整段抄黄庭或套案例原事、主看卦辞时有没有大讲某爻、4 动案例是否来自之卦、追问有没有这一轮建议。

**2026-08-28 本机抽检**（`deepseek-v4-flash`，约 2 分钟、9 次调用）：卡片齐全，兑上案例封顶 3 则，0/3/6 动未附爻位案例，跳槽所问与追问（对方反对／挽留）都落到具体建议，未见讲座人名或「思考过程」。轻问题：0 动须防仍引用未动之爻（来兑／孚于剥），因经文块仍附全六爻；3 动当下仍写初九／九三，卦辞主看未贯彻到「当下」卡。当时追问 prompt 约 4k token（全文重喂）。同日追问改为短上下文后再打 career 一条：followup prompt **776** token（初次仍约 3800），答复仍扣挽留／反对并带建议。

## 未做（有意留下）

- 已合 `main`（PR #12）；生产镜像未重建，解读仍三字段。镜像须带上 `ImaExplanations.json`
- 0 动经文块仍给六爻，模型有时把未动爻写进须防；3 动「当下」有时仍落在动爻而非卦辞
- 繁体系统语言下模型仍出简体
- 不是多轮 agent，没有工具调用

