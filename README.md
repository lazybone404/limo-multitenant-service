# LiMo · 多租户内容处理服务

把「文档 / 视频 → 结构化笔记」这个能力，做成多用户、订阅制、按消耗计费的服务。

Java 侧管业务：账号、套餐、积分、配额、任务状态机。Python 侧管内容：提取、理解、生成。两侧通过一个 HTTP 契约通信，各自演进。

> 这个服务端的核心能力（内容提取 + 笔记生成）来自同一个作者做的桌面端 [LiMo](https://github.com/lazybone404/LiMo)。这里给它补的是「从一个人用，到很多个人用」缺的那一层。

## 为什么分成两个服务

因为两侧的变更速度差一个数量级。

| | 多久变一次 | 所以在哪一侧 |
|---|---|---|
| 计费规则、套餐、配额、状态流转 | 慢，改一次要回归 | Java，和数据库在同一个事务里 |
| 模型、提取引擎、提示词风格 | 一个月一换，换完还要 A/B | Python，注册表 + 配置切换 |

如果揉在一起，每次换模型都要重新回归一遍计费逻辑。分开之后，两侧的契约只剩一件事：**`/convert` 返回一段 Markdown，外加它自己报这次用了多少 token**。

关键取舍在这里：**Java 只记账，不核算**。消耗由 Python 自己报上来，Java 拿到多少记多少。反过来的做法（Java 拿文本长度估 token）在换模型、换模态之后一定会算错，而且算错了没人发现。

## 怎么跑

三种方式，任选一种。

**一键脚本**（会检查端口、起三个进程、健康检查、失败时温和关闭）：

```bash
bash start.sh      # python-core:8001 + java-service:8082 + frontend:5173
bash stop.sh
```

**Docker Compose**（MySQL + 后端 + 核心 + 前端，四个容器）：

```bash
docker compose up -d --build
# 前端 http://localhost:5174  后端 8082  核心 8001
```

**手动分步**：

```bash
# 1. 内容处理核心
cd python-core && pip install -r requirements.txt
python -m uvicorn app:app --host 0.0.0.0 --port 8001

# 2. 业务端（需要 MySQL，默认连 localhost:3306/limo）
cd java-service && ./mvnw spring-boot:run     # Windows: mvnw.cmd spring-boot:run

# 3. 前端
cd frontend && npm install && npm run dev     # http://localhost:5173，/api 代理到 8082
```

配置全部走环境变量，本地默认值只为开箱能跑（见 `java-service/src/main/resources/application.properties`）。生产必须注入 `DB_PASSWORD` / `JWT_SECRET`。

## 接口

除注册登录外都要求 `Authorization: Bearer <token>`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册，自动送 free 套餐 |
| POST | `/api/auth/login` | 登录换 token |
| POST | `/api/tasks` | 提交转笔记任务（`{type, url?, text?}`），先查配额 |
| GET | `/api/tasks/{id}` | 查状态和结果 |
| POST | `/api/subscribe` | 换套餐（送积分 + 切上限） |
| GET/PUT/DELETE | `/api/notes/{id}` | 笔记的读、改、删 |

## 四件最在意的事

**1. 同一份内容同时提交，不能扣两次费**

只在提交前「查有没有在途任务」是不够的——十个请求同时进来，十个都查到「没有」，于是建出十个任务。实测确实如此（`ConcurrencyEvidenceTest`）。

现在去重键（用户 + 类型 + 来源 的 SHA-256）落在数据库唯一索引上：并发的请求只允许一个插进去，其余撞索引后回查那一条，拿到同一个 taskId。任务到终态时把去重键置空——去重窗口只该覆盖「处理中」，处理完了用户想换风格重跑是合理需求。

**2. 失败不扣积分**

扣积分、写笔记、改状态在一个事务里。任何一步抛异常整体回滚。原先是「先扣再写」，写失败时异常被吞掉、任务标成 FAILED，但**积分已经扣了不退**——那样「失败不扣」只是一句话。

**3. 失败原因按「用户能做的事」分，不按异常类型分**

`NO_POINTS`（去充值）/ `CORE_DOWN`（稍后重试）/ `BAD_INPUT`（换个文件）/ `UNKNOWN`（联系我们，这条一定有堆栈）。把配置缺失报成「核心不可用，请重试」，用户重试一百次也不会好。

**4. 认领是条件更新，不是先读再写**

任务从 PENDING 进 PROCESSING 靠一条 `update ... where status = 'PENDING'`，返回受影响行数。先 `findById` 再 `save` 的写法，两个线程能同时认领同一个任务，同一份内容处理两遍。认领的同时记下开始时刻——回收超时任务要靠它，不能靠创建时间（排队时间不算处理时间）。

**5. 处理进程挂了，任务不能永远卡着**

任务被线程认领后进程崩掉，它会一直停在 PROCESSING，而且去重键还挂在它身上：用户之后再提交同一份内容会被去重拦下、拿到这条死任务，从此这个来源再也提交不了。

`TaskRecovery` 每 60 秒扫一遍「进入 PROCESSING 超过 10 分钟」的任务，标成 `FAILED/TIMEOUT` 并释放去重键。判据用的是 `processingStartedAt` 而不是 `createdAt`——高峰期排了十分钟队才开始处理的任务，用创建时间判断会被误杀。

## 测试

```bash
cd java-service && ./mvnw test     # 21 个用例，H2 内存库，不依赖 MySQL 和 python-core
```

覆盖全链路（注册 → 订阅 → 提交 → 核心自报 → 扣积分 → 落笔记 → DONE）、配额拒绝、结算事务回滚、并发扣费、并发认领、并发提交去重、去重键的正确性、超时回收、失败原因分类。

拿数字说话的几条：100 个线程抢 10 点积分，恰好 10 次成功、余额不为负；10 个线程同时提交同一来源，只建出 1 个任务；两段前 100 字相同、后半段不同的文本，必须建出 2 个任务。

## 已知缺口

写出来是因为它们确实没有，不想让读代码的人以为有：

- **异步用的是线程池，不是消息队列**。`TaskProcessor` 是个接口，换成 MQ 只需要换实现，但现在是进程内线程池，重启会丢在途任务。有了 `TaskRecovery` 之后丢掉的会被判超时、让用户重试，但那只兜底，不算解决。
- **超时回收是定时轮询，不是队列的可见性超时**。换成 MQ 之后这一段可以整个删掉，交给队列的 ack 超时机制。
- **前端是从桌面端 fork 过来的**，只改了数据层（接 Java 的 task/note/auth）和登录门，C 端那套多面板布局的代码还在，没接新接口。
- **python-core 没有单元测试**，只有端到端的跑通验证。

## 目录

```
java-service/     Spring Boot 3.5 + JPA：账号 / 订阅 / 积分 / 配额 / 任务状态机 / 笔记版本
python-core/      FastAPI：文档与视频提取、LLM 生成；引擎和厂商走注册表，可替换
frontend/         React + Tailwind（fork 自桌面端），只把数据层接到 Java
docs/             设计文档
```

## 技术栈

Java 21 · Spring Boot 3.5.6 · Spring Data JPA · MySQL 8 / H2 · JJWT · BCrypt · FastAPI · React · Docker Compose
