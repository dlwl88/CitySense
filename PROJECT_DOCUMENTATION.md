# 基于 Spring Boot 与 Spring AI 的同城生活服务平台 — 项目详细说明文档

## 1. 项目概述

“基于 Spring Boot 与 Spring AI 的同城生活服务平台”是一个以商家、优惠券、探店笔记和用户社交为核心的同城生活服务平台，业务形态类似大众点评。项目采用前后端分离开发、后端一体化部署的方式：Spring Boot 提供业务接口并托管生产环境的前端静态资源；`frontend-vue` 目录保存 Vue 3 单页应用。

项目不止实现了基础 CRUD，还围绕高并发、本地生活智能助手和可用性做了工程化实践：

- Redis 缓存、缓存穿透防护、逻辑过期、GEO 附近商家查询、分布式锁；
- Redisson 布隆过滤器，预先拦截明显不存在的店铺和优惠券 ID；
- Redis Lua 脚本与 RocketMQ，实现秒杀资格校验、异步下单和超时关单；
- 注解 + AOP + Redis ZSet + Lua 的滑动窗口限流；
- Redis Lua 实时点赞计数与 XXL-JOB 合并批量落库，减少热门笔记的数据库写入；
- 基于 Spring AI、Milvus 与 SSE 的多轮本地生活 AI Agent；
- 会话、消息、工具调用日志持久化，以及平台知识、店铺画像、探店笔记三类 RAG 索引。

支付接口目前用于模拟支付及回调流程，不对接真实微信支付或支付宝，适合学习、演示和二次开发。

## 2. 技术栈

| 领域 | 技术 | 作用 |
| --- | --- | --- |
| 后端 | Java 17、Spring Boot 3.2.4 | REST API、应用生命周期、静态资源托管 |
| 数据库 | MySQL 8、MyBatis-Plus 3.5.5 | 核心业务数据与 AI 会话数据持久化 |
| 缓存与协调 | Redis、Spring Data Redis、Redisson 3.27.2 | 缓存、会话、GEO、Lua、布隆过滤器、锁、限流 |
| 消息队列 | RocketMQ Spring 2.3.3 | 秒杀订单异步落库、延迟关单 |
| 任务调度 | XXL-JOB 2.4.1 | 定时合并点赞快照，批量同步 MySQL |
| AI 与向量检索 | Spring AI 1.0.3、OpenAI 兼容接口、Milvus | 对话、Embedding、向量检索和 RAG |
| 前端 | Vue 3、Vite 4、Vue Router、Pinia、Element Plus、Axios、Sass | 同城生活服务与 AI 助手界面 |

## 3. 项目目录

```text
./
|- src/main/java/com/hmdp/
|  |- controller/           # 用户、店铺、优惠券、笔记、支付接口
|  |- service/              # 业务服务接口和实现
|  |- mapper/               # MyBatis-Plus Mapper
|  |- entity/               # MySQL 实体映射
|  |- config/               # MVC、Redis、Redisson、MQ、异常处理配置
|  |- component/            # 启动预热、异步执行协调器
|  |- mq/                   # RocketMQ 消费者
|  |- job/                  # XXL-JOB 点赞同步任务
|  |- utils/                # 缓存、ID、拦截器、锁、限流工具
|  `- ai/                   # Agent、记忆、RAG、向量索引、MCP
|- src/main/resources/
|  |- application.yaml      # 默认运行与 AI 配置
|  |- db/                   # 初始化 SQL 与点赞版本字段迁移
|  |- knowledge/            # RAG 本地知识文档
|  |- *.lua                 # 秒杀、解锁、限流、点赞及落库确认脚本
|  `- static/               # Vite 构建后的生产前端资源
|- frontend-vue/
|  |- src/api/              # Axios 客户端和接口模块
|  |- src/pages/            # 首页、分类、店铺、个人中心、AI 页面
|  |- src/components/       # 通用、布局、登录、首页、AI 组件
|  |- src/store/            # Pinia 用户和 AI 状态
|  `- vite.config.cjs       # 开发代理和构建输出配置
|- pom.xml                  # Maven 依赖与构建配置
`- PROJECT_DOCUMENTATION.md # 本文档
```

## 4. 功能与实现说明

### 4.1 用户、登录与会话

用户通过手机号和验证码登录。后端将登录态写入 Redis 并返回 token；前端将 token 保存在浏览器 `localStorage` 的 `hm-dianping-token` 中，并在每次请求的 `authorization` 请求头中携带。

拦截器链分为两个职责：

1. `RefreshTokenInterceptor` 作用于全部路径：从 Redis 恢复用户上下文，并刷新 token 的有效期。
2. `LoginInterceptor` 作用于受保护业务：未登录用户无法操作需要身份的接口。静态资源、店铺浏览、优惠券浏览、热门笔记和登录接口被排除。

用户模块还实现了个人信息、签到与连续签到统计；连续签到使用 Redis Bitmap 记录。

### 4.2 店铺、分类与缓存

店铺模块支持分类列表、店铺详情、店铺新增/更新、按分类分页、名称搜索，以及传入坐标后的附近店铺查询。附近查询使用 Redis GEO 能力。

店铺详情读取链路具备多层保护：

- 先由 Redisson 布隆过滤器拦截明显不存在的 ID，减少无效请求穿透到缓存和数据库；
- `CacheClient.queryWithPassThrough` 在查询不到数据时写入短 TTL 空值，处理布隆过滤器误判后的缓存穿透；
- `CacheClient` 支持逻辑过期：热点缓存到期时先返回旧值，由抢到锁的线程异步重建，避免缓存击穿；
- 合理使用随机 TTL，降低大量 key 同时失效造成的缓存雪崩风险。

### 4.3 探店笔记与社交关系

笔记模块支持发布、热门分页、详情、点赞、点赞用户排行、作者笔记和关注流。点赞关系使用 Redis ZSet：既能判断/切换用户是否点赞，也能按时间或分值获取点赞用户 TopN。

点赞采用“Redis 实时处理、XXL-JOB 合并落库”的最终一致性链路，保留切换式 `PUT /blog/like/{id}` 和 `Result.ok()` 响应。前端沿用接口，增加同一笔记的请求锁与操作后回查。正常点击不更新 MySQL；首次缺少计数时才按需读取数据库初始化。

```text
点赞请求 -> blog_like.lua：切换 ZSet + 更新 count/version + 标记 dirty
         -> 返回成功
XXL-JOB -> HSCAN 待同步笔记 -> 单篇 HGETALL 读取一致快照
        -> 每批最多 500 个快照 -> 数据库事务中的 JDBC batch
        -> 提交成功 -> blog_like_ack.lua 按版本确认
```

| Redis Key | 类型 | 职责 |
| --- | --- | --- |
| `blog:liked:笔记ID` | ZSet | 用户 ID 与点赞时间，沿用原有状态及 TopN 查询 |
| `blog:like:meta:笔记ID` | Hash | `count` 实时总数、`version` 递增版本 |
| `blog:like:dirty` | Hash | `笔记ID → 最近变化版本`，合并同一笔记多次变化 |

**初始化与 Lua。** `initializeLikeMeta()` 只在冷初始化时获取 `lock:blog:like:init:笔记ID`。加锁后再次检查 Redis，只查 MySQL 的 `id/liked/like_version`；历史总数以数据库为基线，不能用缺少历史用户的 ZSet 数量覆盖。计数缺失但 dirty 仍在时直接报错，保留变化并等待恢复，不用滞后的数据库值重新初始化。Lua 保证状态、计数、版本与 dirty 的更新不被其他请求穿插；不提供脚本出错自动回滚，也不包含 MySQL 事务。当前为单机 Redis；迁移到 Cluster 时需将脚本涉及的 key 按同一槽分组，并设计分片 dirty 集合，不能直接沿用全局 Hash。

**合并落库。** `BlogLikeSyncJob` 使用 `lock:blog:like:sync` 避免多实例重复扫描和写库。HSCAN 遍历一轮，不一次读出整个 dirty；单篇笔记一次 HGETALL 读取 count/version。每批最多 500 个快照，数值型 ID 只解析一次，`TransactionTemplate` 初始化后复用。执行 SQL：

```sql
UPDATE tb_blog SET liked = ?, like_version = ?
WHERE id = ? AND like_version < ?;
```

**失败重试与确认。** 版本条件阻止重复快照和旧任务覆盖新结果。HSCAN 可能重复返回字段，正确性依靠版本保护。数据库事务提交后，确认 Lua 仅在 dirty 版本仍等于本次快照时删除标记；同步期间的新点击保留到下一轮。写库失败不清标记；提交后 Redis 确认失败也向调度器报告失败，后续调度可以重复同步。这里保留的是最新快照，不是每次点击的审计日志。

**实时查询与收益。** `fillLikeCounts()` 在详情、热榜、关注流、我的笔记和作者笔记中用 Redis 总数补齐，列表通过 pipeline 减少往返，缺少计数时保留数据库值。热榜仍按 MySQL `liked` 排序，延迟包含调度周期、运行时间和故障积压，不能保证严格在 5 秒内同步。同一笔记两次同步间的多次点击通常合并为一次计数更新；请求分散到大量不同笔记时，合并收益较小。

**恢复边界。** 计数、版本、dirty 不设 TTL，点赞关系仍只存在 Redis，需要持久化、备份并防止内存淘汰。完整 Redis 丢失或故障切换丢写没有自动无损恢复，需要恢复与对账；计数和版本必须一起恢复，不能单独重置版本。接口继续沿用“每次请求切换状态”，网络重试会再次切换；请求幂等需后续改成明确目标状态或增加请求去重。


关注关系持久化到 MySQL，同时同步到 Redis Set，便于查询共同关注。用户发布笔记后，笔记 ID 可投递到粉丝收件箱，前端通过滚动分页读取关注流。

### 4.4 优惠券、秒杀与订单

普通优惠券和秒杀券采用不同的下单链路：

- **普通券**：校验券状态后，同步创建未支付的 `tb_voucher_order` 订单。
- **秒杀券**：由 `seckill.lua` 原子完成库存检查、扣减和一人一单校验，成功后发送 RocketMQ 消息，消费者异步写入 MySQL。

秒杀处理流程如下：

```text
客户端 -> Redis Lua 校验与预扣库存 -> RocketMQ 创建订单消息
       -> SeckillVoucherListener -> MySQL 写订单并扣减数据库库存
       -> RocketMQ 延迟消息 -> OrderTimeoutListener
       -> 关闭未支付订单、回滚库存和用户秒杀资格
```

`SeckillStockInitializer` 会在应用启动时将 MySQL 中的秒杀库存预热到 Redis。若个别库存 key 缺失，业务服务还会尝试按需预热。Redis 的 pending 标记使用户可以在“消息已受理但订单尚未落库”期间查询到等待状态。

### 4.5 模拟支付与超时关单

支付模块支持设置订单支付方式、模拟支付回调、模拟微信/支付宝回调以及订单状态查询。秒杀订单受理后发送 15 分钟延迟消息，由 `OrderTimeoutListener` 调用 `VoucherOrderServiceImpl.closeTimeoutOrder()` 关闭仍未支付的订单。普通券订单当前不发送该延迟消息。

创建支付、支付回调和超时关单统一按订单 ID 加锁，避免同一订单被同时支付和取消。当前实现如下：

1. **共用锁与自动续期**：三种操作都使用 `ORDER_LOCK_PREFIX + orderId`，实际 key 为 `lock:order:订单ID`。调用 Redisson `RLock.tryLock(2, TimeUnit.SECONDS)`，最多等待 2 秒，不指定固定租期，使用 watchdog 自动续期。
2. **事务提交后解锁**：业务服务获取锁后，在 `TransactionTemplate` 的 `REQUIRES_NEW` 事务中重新读取订单、校验状态并更新数据。事务提交或回滚后才释放锁；只释放本次成功获取且当前线程仍持有的锁。
3. **数据库状态约束**：支付与取消更新均附带 `id = 订单ID AND status = 1`，分别将状态改为 `2`（已支付）或 `4`（已取消），并检查更新结果。创建支付只修改 `pay_type`，避免旧订单对象覆盖新状态；控制器会透传创建支付失败结果。
4. **关单与数据库库存同事务**：只有订单成功从未支付变为已取消，才恢复 MySQL 秒杀库存。重复关单消息不会再次执行数据库库存增加。
5. **提交后补偿 Redis**：使用 `VoucherOrderServiceImpl` 内嵌的 Lua 脚本，检查 `seckill:rollback:订单ID` 是否已存在；若未补偿且用户仍在该券的预约集合中，则恢复 Redis 库存、移除用户占用，再记录补偿标记。已取消订单收到重试消息时，可继续完成 Redis 补偿，避免重复恢复库存。
6. **保留失败重试**：支付抢锁失败返回“订单处理中，请稍后重试”；关单抢锁失败、事务异常或 Redis 补偿失败会抛出异常，由超时消费者继续向 RocketMQ 报告失败，进入消息重试流程。

```text
创建支付 / 支付回调 / 超时关单
  -> 获取同一订单的 Redisson 锁
  -> 开启新事务，重新读取并校验订单
  -> 按未支付状态条件更新，关单同时恢复 MySQL 库存
  -> 提交或回滚数据库事务
  -> 关单成功后执行可重试、按订单去重的 Redis 补偿
  -> 当前线程仍持锁时释放锁
```

| 先完成的操作 | 后续操作的结果 | 库存处理 |
| --- | --- | --- |
| 支付成功 | 关单读取到已支付状态，直接结束 | 不恢复库存 |
| 超时关单成功 | 支付读取到已取消状态，拒绝支付 | 恢复秒杀库存和用户预约资格 |
| 重复支付回调 | 服务识别已支付状态，返回已支付 | 不修改库存 |
| 重复超时消息 | 跳过数据库库存更新，检查 Redis 补偿标记 | 避免重复恢复库存 |

相关实现位于 `PaymentServiceImpl.withOrderLock()`、`VoucherOrderServiceImpl.closeTimeoutOrder()`、`OrderTimeoutListener`、`RedisConstants.ORDER_LOCK_PREFIX` 和 `PaymentController`。MySQL 与 Redis 采用“数据库提交后补偿并重试”，不是跨存储原子事务；重试耗尽后的死信处理仍需运维跟进。Redis 补偿标记当前不设 TTL，后续应结合订单保留周期管理其清理。

### 4.6 滑动窗口限流

项目通过 `@SlidingWindowLimit` 对重点接口限流，AI 接口已使用该注解。AOP 切面配合 Redis ZSet 与 `RateLimit.lua`，原子完成以下工作：

1. 删除当前时间窗口外的请求时间戳；
2. 统计当前窗口内请求数量；
3. 未超过阈值时记录本次请求，超过时拒绝请求。

相较固定窗口，滑动窗口对跨窗口边界的突发流量控制更准确。每个接口可单独设置窗口大小、限额、时间单位和限流 key 表达式。

### 4.7 AI 同城生活助手

AI 模块是事件驱动的多轮 Agent，不是单次同步问答接口：

- 会话和消息分别持久化在 `tb_ai_conversation`、`tb_ai_message`；
- 用户提交消息后产生 `ChatMessageCreatedEvent`，由异步协调器执行 Agent；
- 客户端先连接会话 SSE 接口，再接收状态、增量文本和完成事件；
- 记忆服务仅加载最近的上下文窗口，默认值为 8 条消息，以控制 Token 成本和响应延迟；
- 手动 Think-Execute 循环负责工具注册、工具执行日志、最大步数限制和最终答案生成；默认最多执行 20 步；
- 可围绕店铺推荐、单店分析、知识问答、评论检索、路线和天气开展工具调用；路线和天气可接入远程 MCP，也具备本地降级能力。

前端使用 `fetch` 手动解析 SSE 分段，目的是在流式 GET 请求中携带登录 token；AI 页面支持新建会话、切换历史会话、快捷提问和流式增量展示。

### 4.8 RAG 与向量索引

项目没有把所有文本混入一个向量库，而是维护三类 Milvus Collection：

| 索引 | 默认集合名 | 数据来源 | 主要用途 |
| --- | --- | --- | --- |
| 平台知识库 | `knowledge_vector` | `knowledge/*.txt` | 平台规则、常见问题回答 |
| 店铺画像 | `shop_profile_vector` | 结构化店铺字段转换后的描述文本 | 语义化店铺推荐 |
| 探店笔记/评论 | `blog_review_vector` | 笔记和店铺关联内容 | 评论总结、单店分析 |

平台知识检索先进行向量召回，然后使用轻量级关键词扫描本地知识文本作为 fallback，接着合并、去重和排序。这能提升平台规则等高确定性问题的命中率。店铺画像与探店笔记主要走向量检索，评论检索支持按店铺 ID 过滤。

`KnowledgeBaseInitializer` 默认在启动时检查/初始化向量索引。默认 `fail-fast: false`：Milvus 或 Embedding 服务不可用时只记录警告，不阻止整个 Spring Boot 服务启动；但 AI 的检索能力会不可用，直到依赖恢复并完成索引构建。

## 5. 数据库设计

数据库脚本位于 `src/main/resources/db/`，应按以下顺序导入：

| 脚本 | 内容 |
| --- | --- |
| `hmdp.sql` | 店铺、分类、用户、用户详情、优惠券、秒杀库存、订单、笔记、评论、关注关系及演示数据 |
| `ai-agent.sql` | AI 会话和 AI 消息表 |
| `add_blog_like_version.sql` | 一次性给 `tb_blog` 增加非空、默认 0 的 `like_version` 字段 |

核心业务表以 `tb_` 开头。`tb_voucher_order` 的订单状态同时被支付回调和延迟关单逻辑使用，测试异步链路时不建议手动修改其状态。

已有数据库只执行新增字段迁移，不能重新运行包含 DROP TABLE 的初始化脚本。切换异步点赞前停止旧版点赞写入，避免旧服务同步加减与新版快照相互覆盖。`Blog.likeVersion` 通过 `@JsonIgnore` 保持 JSON 接口字段不变。

## 6. 运行依赖

完整运行项目需要以下环境：

| 依赖 | 默认地址或要求 | 用途 |
| --- | --- | --- |
| JDK | Java 17 | 后端编译和运行 |
| Maven | 推荐 3.8+ | 后端依赖管理和启动 |
| Node.js | 推荐 18+ | 前端开发和构建 |
| MySQL | `localhost:3306`，数据库 `hmdp` | 业务数据、AI 历史数据 |
| Redis | `localhost:6379` | 登录、缓存、锁、限流、秒杀 |
| RocketMQ | NameServer `localhost:9876` | 异步秒杀和超时订单 |
| XXL-JOB | 管理端 2.4.1 默认 `localhost:8081/xxl-job-admin`，执行器端口 9999 | 点赞定时批量同步，管理端需单独部署 |
| Milvus | `localhost:19530` | AI 向量检索 |
| OpenAI 兼容 Chat/Embedding 服务 | 由环境变量配置 | 大模型对话和向量生成 |

`application.yaml` 中的 MySQL 默认账号为 `root` / `1234`，仅适合本地开发。正式环境必须使用环境变量、密钥管理系统或外部配置文件管理数据库密码和 API Key。

## 7. 本地启动指南

### 7.1 初始化 MySQL

创建 `hmdp` 数据库并导入脚本：

```powershell
mysql -u root -p -e "CREATE DATABASE hmdp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
mysql -u root -p hmdp < src/main/resources/db/hmdp.sql
mysql -u root -p hmdp < src/main/resources/db/ai-agent.sql
mysql -u root -p hmdp < src/main/resources/db/add_blog_like_version.sql
```

上述重定向命令按支持输入重定向的终端使用；PowerShell 用户可使用 MySQL 客户端的 SOURCE 命令，或在 Workbench、Navicat 中顺序执行三个脚本。版本字段迁移只执行一次。

### 7.2 配置并启动后端

检查 `src/main/resources/application.yaml` 中的服务地址：

- 服务端口：`8083`
- MySQL：`localhost:3306/hmdp`
- Redis：`localhost:6379`
- RocketMQ NameServer：`localhost:9876`
- Milvus：`localhost:19530`

AI 需要在启动前提供 API Key。默认使用阿里云 DashScope 的 OpenAI 兼容地址，也可替换为任何兼容服务：

```powershell
$env:SPRING_AI_OPENAI_API_KEY = "your-api-key"
# 可选覆盖项
$env:SPRING_AI_OPENAI_BASE_URL = "https://your-compatible-endpoint/v1"
$env:SPRING_AI_OPENAI_CHAT_MODEL = "your-chat-model"
$env:SPRING_AI_OPENAI_EMBEDDING_MODEL = "your-embedding-model"
```

默认 Milvus 向量维度是 `1024`，必须与所选 Embedding 模型的输出维度一致。若更换模型且维度不同，请设置 `SPRING_AI_MILVUS_EMBEDDING_DIMENSION`，并重建不兼容的 Collection。

启动后端：

```powershell
mvn spring-boot:run
```

服务正常启动后访问地址为 `http://localhost:8083`。

### 7.3 前端开发模式

```powershell
Set-Location frontend-vue
npm install
npm run dev
```

访问 `http://localhost:5173`。Vite 将以 `/api` 开头的请求转发到 `http://localhost:8083`，并去掉 `/api` 前缀。

### 7.4 前后端一体化构建

```powershell
Set-Location frontend-vue
npm run build:backend
Set-Location ..
mvn package
java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar
```

Vite 构建目录是 `src/main/resources/static`，因此 Spring Boot 可在同源下同时提供页面和 API。前端配置启用了 `emptyOutDir: true`，每次生产构建会清空并重新生成该静态资源目录。

### 7.5 配置 XXL-JOB 点赞同步

1. 执行 `add_blog_like_version.sql`，单独部署 XXL-JOB 2.4.1 管理端；客户端版本由 `pom.xml` 的 `xxl-job.version` 管理。
2. 设置管理端地址和访问令牌，与管理端配置保持一致。执行器默认 appname 为 `cityaihub-like-executor`，端口为 9999。
3. 在管理端添加该 appname 的执行器，使用自动注册或手动地址。容器或多网卡环境配置管理端能够回调的 IP 或完整执行器地址。
4. 创建 Bean 任务，JobHandler 为 `blogLikeSyncJob`；建议每 5 秒触发，路由“第一个”，阻塞策略“丢弃后续调度”，配置失败重试和告警，然后启动任务。
5. 检查 Redis 计数与数据库快照趋于一致，并监控 dirty 积压。仅启动后端或注册执行器不会自动创建、触发任务。

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `XXL_JOB_ENABLED` | `true` | 是否启动执行器；关闭后仍写 Redis，但数据库不再由调度同步 |
| `XXL_JOB_ADMIN_ADDRESSES` | `http://127.0.0.1:8081/xxl-job-admin` | 调度中心地址 |
| `XXL_JOB_ACCESS_TOKEN` | 空 | 与管理端一致的访问令牌 |
| `XXL_JOB_APPNAME` | `cityaihub-like-executor` | 执行器注册名称 |
| `XXL_JOB_EXECUTOR_ADDRESS` | 空 | 完整的可回调执行器地址，留空则自动生成 |
| `XXL_JOB_EXECUTOR_IP` | 空 | 注册 IP，留空则自动选择 |
| `XXL_JOB_EXECUTOR_PORT` | `9999` | 执行器监听端口 |
| `XXL_JOB_LOG_PATH` | `logs/xxl-job` | 调度日志目录，保留 7 天 |

任务停用时 dirty 保留并增长，不能把关闭执行器作为长期正常运行方式。重点监控任务失败、耗时、dirty 数量、数据库写 QPS 和行锁等待。接入 API 参考 [XXL-JOB 2.4.1 官方示例](https://github.com/xuxueli/xxl-job/blob/2.4.1/xxl-job-executor-samples/xxl-job-executor-sample-springboot/src/main/java/com/xxl/job/executor/core/config/XxlJobConfig.java)。

## 8. AI 配置说明

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `SPRING_AI_OPENAI_BASE_URL` | DashScope 兼容地址 | OpenAI 兼容 API 地址 |
| `SPRING_AI_OPENAI_API_KEY` | 空 | AI 提供方密钥 |
| `SPRING_AI_OPENAI_CHAT_MODEL` | `qwen3.7-plus` | 对话模型 |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | `text-embedding-v4` | 向量模型 |
| `SPRING_AI_MILVUS_HOST` / `PORT` | `localhost` / `19530` | Milvus 地址 |
| `SPRING_AI_MILVUS_EMBEDDING_DIMENSION` | `1024` | 向量维度 |
| `AI_AGENT_BOOTSTRAP_ENABLED` | `true` | 是否启动时初始化/检查向量索引 |
| `AI_AGENT_BOOTSTRAP_FAIL_FAST` | `false` | 向量初始化失败是否阻止应用启动 |
| `AI_AGENT_MEMORY_WINDOW_SIZE` | `8` | 每次调用携带的最近消息数 |
| `AI_AGENT_MULTI_AGENT_MAX_STEPS` | `20` | Agent 最大推理/工具步骤 |
| `AI_AGENT_MCP_ENABLED` | `false` | 是否启用远程 MCP |

路线和天气 MCP 默认关闭。若要启用，需要同时配置相应服务的启用开关、Base URL、调用路径、API Key 和工具名称。

## 9. API 概览

除 AI DTO/SSE 接口外，大部分接口使用项目统一的 `Result` 包装。受保护接口需要在 `authorization` 头中携带登录 token。

| 模块 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 用户 | POST | `/user/code` | 发送登录验证码 |
| 用户 | POST | `/user/login` | 登录并获取 token |
| 用户 | POST | `/user/logout` | 退出登录 |
| 用户 | GET | `/user/me` | 当前用户 |
| 用户 | POST / GET | `/user/sign`、`/user/sign/count` | 签到、连续签到数 |
| 店铺 | GET | `/shop/{id}` | 店铺详情 |
| 店铺 | GET | `/shop/of/type` | 按类型分页；传坐标可查附近商家 |
| 店铺 | GET | `/shop/of/name` | 按名称分页搜索 |
| 店铺 | POST / PUT | `/shop` | 新增、修改店铺 |
| 分类 | GET | `/shop-type/list` | 店铺分类 |
| 优惠券 | GET | `/voucher/list/{shopId}` | 店铺优惠券 |
| 优惠券 | POST | `/voucher`、`/voucher/seckill` | 创建普通券、秒杀券 |
| 订单 | POST | `/voucher-order/buy/{id}` | 购买普通券 |
| 订单 | POST | `/voucher-order/seckill/{id}` | 秒杀下单 |
| 支付 | POST | `/payment/create`、`/payment/simulate/{id}` | 创建支付、模拟支付成功 |
| 支付 | POST | `/payment/wechat/{id}`、`/payment/alipay/{id}` | 设置支付方式、生成模拟支付链接；创建失败时返回错误 |
| 支付 | POST | `/payment/callback/wechat/{id}`、`/payment/callback/alipay/{id}` | 模拟渠道回调，统一走订单锁和状态校验 |
| 支付 | GET | `/payment/status/{id}` | 查询订单状态 |
| 笔记 | POST | `/blog` | 发布笔记 |
| 笔记 | PUT | `/blog/like/{id}` | 点赞/取消点赞 |
| 笔记 | GET | `/blog/hot`、`/blog/{id}`、`/blog/likes/{id}` | 热门、详情、点赞用户 |
| 笔记 | GET | `/blog/of/me`、`/blog/of/user`、`/blog/of/follow` | 我的笔记、作者笔记、关注流 |
| 关注 | PUT | `/follow/{id}/{isFollow}` | 关注/取关 |
| 关注 | GET | `/follow/or/not/{id}`、`/follow/common/{id}` | 是否关注、共同关注 |
| 上传 | POST / GET | `/upload/blog`、`/upload/blog/delete` | 上传/删除笔记图片 |
| AI | POST / GET | `/ai/agent/conversations` | 创建、获取会话列表 |
| AI | GET | `/ai/agent/conversations/{id}/messages` | 获取会话消息 |
| AI | GET（SSE） | `/ai/agent/conversations/{id}/stream` | 接收流式事件 |
| AI | POST | `/ai/agent/messages`、`/ai/agent/chat` | 创建消息、简化聊天入口 |
| AI | POST | `/ai/agent/recommend` | Agent 推荐 |
| AI | GET | `/ai/agent/analyze/shop/{shopId}` | 单店 AI 分析 |
| AI | POST | `/ai/agent/index/rebuild/all`、`/ai/agent/index/rebuild/shop/{shopId}` | 重建向量索引 |

请求字段和响应字段请以 Controller 方法、`src/main/java/com/hmdp/ai/dto/` 下的 DTO，以及 `frontend-vue/src/api/modules/` 中实际调用为准。

## 10. 前端说明

前端在生产模式下直接请求后端接口；开发模式下通过 Vite 代理请求后端。主要路由如下：

| 路由 | 页面 |
| --- | --- |
| `#/` | 首页：分类、店铺列表、搜索 |
| `#/category/:id?` | 分类结果 |
| `#/shop/:id` | 店铺详情与优惠券操作 |
| `#/profile` | 个人中心和签到状态 |
| `#/ai-assistant` | AI 会话与流式助手 |

`frontend-vue/src/mock/data.js` 仍提供部分展示资源、城市选项和快捷问题。这不代表前端整体是 mock 模式；现有 API 模块会调用真实 Spring Boot 接口。

前端已同步支付与超时关单的状态处理：详情页按后端状态区分创建中、待支付、已支付和已取消，仅待支付订单可提交支付；历史订单重新进入页面时会刷新状态。支付前查询状态，操作后同步结果，抢锁失败、状态变化或请求超时后回查后端并更新本地记录，不自动重发支付请求。查询失败时显示状态待确认并禁用支付，待用户查状态成功后恢复。创建中状态 `0` 同时用于详情页和个人中心展示。

### 10.1 点赞交互与实时计数

首页的点赞接口成功响应不包含计数，因此提交后查询 `GET /blog/{id}`，将后端返回的 `liked`、`isLike` 同时写入热榜卡片和详情弹窗，替代原先本地计数加减。后端从 Redis 补齐计数，页面无需等待 XXL-JOB 落库；这会增加一次详情读取，但不会增加点赞写库，也没有新增定时轮询。

`frontend-vue/src/composables/useBlogLikes.js` 统一处理同一笔记的请求互斥与结果待确认状态：提交及查询期间锁住列表与弹窗按钮，不同笔记可并行操作。明确的 `Result.fail` 由 Axios 标记为业务错误并展示；超时、传输错误或提交成功后的查询失败无法确认最终状态，按钮改为“刷新状态”，再次点击仅发 GET，查询成功后恢复切换操作。旧计数在回查成功前仅作为最近一次已知值展示。该保护不等于后端幂等，不能解决跨设备重复请求。

登录、退出或切换账号时清理当前笔记状态并重新查询，丢弃旧会话响应；列表与详情查询也防止旧列表覆盖已更新的计数。首页支持“刷新热榜”，个人中心支持“刷新笔记”，打开个人笔记详情时重新查询后端而非直接展示列表缓存。刷新失败保留已有列表。热门排序仍按数据库计数，可能落后于 Redis 实时计数；前端不自行重排当前页。

## 11. 测试与验证

后端测试位于 `src/test/java`，包含基础工具、AI Agent、RAG、种子数据和 Agent 循环相关测试。

```powershell
mvn test

Set-Location frontend-vue
npm test
npm run build
```

部分 Spring 上下文测试可能依赖 Redis、MySQL、RocketMQ、Milvus 或 AI 配置。若只验证前端编译，`npm run build` 不依赖这些后端服务。

支付与超时关单的回归测试补充在原有 `NormalTest.OrderConcurrencyTests` 中，没有新增测试文件。可单独执行：

```powershell
mvn '-Dtest=NormalTest' test
```

新增 9 个场景验证：支付先完成、关单先完成、重复支付回调、重复超时消息、抢锁失败后的支付拒绝及消息重试、Redis 失败后的补偿重试、数据库失败时先回滚再解锁、提交失败不返回支付成功、创建支付只更新支付方式并拒绝已取消订单。

2026-10-08 本次改进验证中，上述 9 个场景、原有 1 个基础测试及 10 个 AI 单元测试通过，共 20 项；订单回归套件也已复测通过。订单回归采用模拟存储、事务管理器及内存共享锁，未验证真实 Redis Lua 执行、Redisson 续期、数据库事务和 RocketMQ 投递，不等同于完整中间件联调。

点赞回归测试位于 `BlogLikeSyncJobTest` 和 `BlogLikeLuaIntegrationTest`。前者模拟数据库及事务，验证提交后才确认、写库/提交失败不确认、确认失败可重试、抢锁失败不写库、缺失快照保留标记和 500 条分批。后者显式启用后连接本机 Redis，只操作随机命名的测试 key 并清理，验证真实 Lua 的并发切换、历史计数基线、旧确认保留新变化、64 位版本精度和非法扣减保护。

```powershell
mvn '-Dtest=BlogLikeSyncJobTest,BlogLikeLuaIntegrationTest,NormalTest' '-Dlike.redis.integration=true' test
```

2026-10-08 验证：后端编译成功，7 个点赞任务测试、6 个真实 Redis Lua 测试及 10 个原有基础/订单回归测试通过，共 23 项。未执行业务数据库迁移，也未验证真实 MySQL 落库和 XXL-JOB 管理端远程调度；这些需在部署环境联调。Redis 集成测试默认跳过，只有显式传入上述属性且本机 Redis 可用时执行。

前端点赞测试位于 `frontend-vue/test/blog-likes.test.js`，通过模拟 API 验证真实交互逻辑：成功后回查实时计数、同一笔记重复点击和读写互斥、不同笔记并行、超时及回查失败后的只读恢复、明确业务拒绝、账号切换丢弃在途响应及无效详情保护。2026-10-08 执行 `npm test`，11 项通过；生产构建通过并更新 `src/main/resources/static`。尚未进行真实浏览器与部署后端的端到端联调。

建议手工验证顺序：

1. 启动 MySQL、Redis、RocketMQ 与后端，验证店铺和分类浏览。
2. 登录后执行需要身份的操作，验证 token 传递和拦截器行为。
3. 创建或使用秒杀券，观察订单从“等待落库”到订单状态可查的过程。
4. 验证支付与关单的两种执行顺序：支付成功后等待超时消息，状态应保持已支付且不恢复库存；超时关单后再支付，状态应保持已取消且支付被拒绝。再重投同一超时消息，确认 MySQL 和 Redis 库存不重复增加；模拟锁竞争或 Redis 补偿失败，确认 MQ 重试后完成处理。需要验证 watchdog 时，可在隔离测试环境中使业务执行超过原先的 5 秒固定租期，确认同订单操作仍互斥。
5. 完成点赞迁移并启动 XXL-JOB 任务，验证多个用户点赞后的 Redis 实时计数、数据库版本同步、失败重试和任务暂停后的积压恢复。
6. 配置 Milvus 与 AI 服务后，在 AI 页面新建会话并验证 SSE 增量输出。

## 12. 部署与安全建议

- 将数据库密码、AI Key、MCP Key 从源码默认配置中移出，改为外部密钥管理。
- 生产环境使用 HTTPS；前后端分离部署时限制 CORS 来源。
- 为 MySQL、Redis、RocketMQ、Milvus 配置鉴权、持久化、备份和监控。
- 向量索引重建接口属于管理能力，上线前应增加角色权限控制。
- 对接真实支付前，必须补充验签、幂等记录、对账、可靠重试与补偿机制。
- 重点监控 Redis 内存、RocketMQ 重试/死信队列、数据库连接池及 AI 提供方延迟和错误率。

## 13. 常见问题排查

| 现象 | 常见原因 | 处理方式 |
| --- | --- | --- |
| 后端无法连接 MySQL | 数据库、账号或表结构未准备好 | 创建 `hmdp`，导入两个 SQL 脚本，检查数据源配置 |
| 登录、缓存、限流异常 | Redis 未启动或地址不匹配 | 启动 Redis，检查 host/port |
| 点赞实时显示但 MySQL 不更新 | XXL-JOB 未创建或未启动任务、执行器注册失败、写库失败 | 检查 blogLikeSyncJob 调度日志、地址/令牌与 dirty 积压 |
| Unknown column like_version | 未执行点赞字段迁移 | 对已有数据库执行 add_blog_like_version.sql，不重跑初始化脚本 |
| 秒杀成功但没有订单 | RocketMQ Broker/消费者不可用 | 检查 NameServer、Broker、Topic、生产者与消费者日志 |
| 支付返回“订单处理中，请稍后重试” | 同一订单的创建支付、支付回调或关单正在持锁 | 稍后查询订单状态再重试，检查 `lock:order:订单ID` 对应操作耗时 |
| 订单已取消但 Redis 库存尚未恢复 | 数据库已提交，Redis 补偿失败或消息正在重试 | 检查消费者重试/死信、Redis 连通性及 `seckill:rollback:订单ID` 标记，避免手工重复增加库存 |
| 启动时出现向量初始化警告 | Milvus 或 Embedding 服务不可达 | 检查地址、Key、模型、向量维度；默认不会阻止应用启动 |
| AI 未引用知识库内容 | 索引未完成或数据陈旧 | 修复 AI 依赖后调用索引重建接口 |
| 开发环境前端接口报错 | 8083 后端未运行 | 启动后端或修改 Vite 代理目标 |
| 生产页面未更新 | 没有重新构建前端资源 | 执行 `npm run build:backend` 后再打包部署 |

## 14. 可继续扩展的方向

1. 增加 OpenAPI/Swagger 文档与标准接口示例。
2. 提供 Docker Compose，一键编排 MySQL、Redis、RocketMQ、Milvus、后端和前端。
3. 增加异步订单失败补偿、重试观测和死信处理。
4. 为后台管理、上传、索引重建加入 RBAC 权限体系。
5. 建立 AI 检索评测集、召回质量指标、会话摘要和成本监控。
6. 将前端 mock 中逐渐成为业务数据的内容下沉为可配置后端接口。

---

本文档依据当前仓库的源码、配置和前端调用关系编写。后续如修改服务地址、API 契约、RocketMQ Topic、AI 模型或部署结构，应同步更新本文档。
