[English](README.md) | **简体中文**

# 基于 RocketMQ LiteTopic 的多智能体代码审查

一个基于 **Apache RocketMQ 5.x LiteTopic** 与
[`rocketmq-a2a`](https://github.com/apache/rocketmq-a2a) SDK 实现的五 Agent 代码审查系统。

Supervisor 将一次审查拆分为风格、逻辑和安全三个维度。三个 Worker Agent
并行消费任务，通过会话级 LiteTopic 回传结果，最后由 Summary Agent 生成结构化的
Markdown/JSON 报告。

## Agent 角色

| Agent | 职责 |
|---|---|
| SupervisorAgent | 拆分审查任务、派发任务、订阅会话 LiteTopic、收集结果、落盘报告 |
| StyleAgent | 命名、格式、import、方法长度、可读性检查 |
| LogicAgent | 空值/边界条件、条件赋值、越界、资源泄漏、异常处理等问题 |
| SecurityAgent | SQL 注入、XSS、命令注入、硬编码密钥、SSRF、XXE、反序列化等模式 |
| SummaryAgent | 去重、风险评分、生成最终报告 |

## 架构

- **Normal Topic** `rmqai-{studentId}-review-task`：任务分发，Tag 为
  `STYLE`、`LOGIC`、`SECURITY`、`SUMMARY`。
- **Lite 父 Topic** `rmqai-{studentId}-review-result`：结果回传。
- 每次审查使用一个独立 **LiteTopic**：
  `{studentId}-review-{timestamp}-{suffix}`。
- 四个普通 Consumer Group，加一个 Supervisor 的 Lite Consumer Group。
- 所有资源名和消息 Key 都以 `{studentId}` 为前缀。

拓扑、消息流转和命名规则见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 环境要求

- JDK 17+
- Maven 3.9+
- 支持 LiteTopic 的 RocketMQ 5.x：
  - 开源自建：RocketMQ 5.5+ Broker，开启 `enableLmq=true` 和
    `enableMultiDispatch=true`
  - 云上：实例具备 LiteTopic 能力，并提供 gRPC 接入点
- 能访问 Broker/Proxy 的 gRPC 端口。应用使用 `rocketmq-client-java` 5.x。

## 快速开始

### 1. 配置

```bash
cp .env.example .env
```

编辑 `.env`：

```properties
STUDENT_ID=<你的资源前缀>
RMQ_ENDPOINT=<host>:8080
RMQ_NAMESPACE=
RMQ_ACCESS_KEY=<用户名>
RMQ_SECRET_KEY=<密码>
RMQ_SSL=false
```

`STUDENT_ID` 用于资源名和消息 Key。代码里有一个占位默认值 `000000`，
正式运行时应设置成你自己的前缀。

不要提交 `.env`，它已被 `.gitignore` 忽略。

### 2. 创建 RocketMQ 资源

应用依赖以下资源：

| 资源 | 名称 |
|---|---|
| 普通 Topic | `rmqai-{studentId}-review-task` |
| Lite 父 Topic | `rmqai-{studentId}-review-result` |
| 普通 Consumer Group | `GID_{studentId}_style`、`_logic`、`_security`、`_summary` |
| Lite Consumer Group | `GID_{studentId}_supervisor`（绑定 Lite 父 Topic） |

可以在 RocketMQ 控制台手动创建，或在自建集群上执行：

```bash
export STUDENT_ID=<你的资源前缀>
export ROCKETMQ_HOME=/path/to/rocketmq
export NAMESRV=127.0.0.1:9876
./scripts/setup-resources.sh
```

Supervisor Group 必须是 Lite 消费组并绑定
`rmqai-{studentId}-review-result`。四个 Worker Group 是普通消费组；
Topic 和 Tag 由客户端订阅时指定。

### 3. 构建

```bash
./scripts/build.sh
```

如果脚本没有自动识别到 JDK 17：

```bash
APP_JAVA_HOME=/path/to/jdk17 ./scripts/build.sh
```

### 4. 运行

五类 Agent 运行在同一个 JVM：

```bash
./scripts/run-all.sh --env-file .env --file samples/InsecureOrderService.java
```

独立进程运行：

```bash
./scripts/run-worker.sh style --env-file .env
./scripts/run-worker.sh logic --env-file .env
./scripts/run-worker.sh security --env-file .env
./scripts/run-worker.sh summary --env-file .env
./scripts/run-supervisor.sh --env-file .env --file samples/InsecureOrderService.java
```

支持的输入：

```bash
./scripts/run-all.sh --env-file .env --file path/to/File.java
./scripts/run-all.sh --env-file .env --pr https://github.com/owner/repo/pull/123
./scripts/run-all.sh --env-file .env --code 'class A { void f() { if (x = 1) {} } }'
```

报告输出到 `reports/{sessionId}.md` 和 `reports/{sessionId}.json`。

## 配置项

| 变量 | 默认值 | 说明 |
|---|---|---|
| `STUDENT_ID` | `000000` | 资源/Session/Key 前缀 |
| `RMQ_ENDPOINT` | `127.0.0.1:8081` | RocketMQ gRPC 接入点 |
| `RMQ_NAMESPACE` | 空 | 部署需要的命名空间 |
| `RMQ_ACCESS_KEY` | 空 | 用户名/Access Key |
| `RMQ_SECRET_KEY` | 空 | 密码/Secret Key |
| `RMQ_SSL` | `false` | 是否启用 gRPC TLS |
| `REVIEW_TIMEOUT_SECONDS` | `120` | 结果等待超时 |
| `ANALYZE_DELAY_MS` | `600` | 模拟 Agent 推理耗时 |
| `REPORT_DIR` | `reports` | 报告输出目录 |

分析引擎在本地运行，不调用外部大模型。`ANALYZE_DELAY_MS` 只模拟推理耗时，
让报告中的并行执行窗口更明显。

## 说明

- LiteTopic 需要服务端支持；只创建 `LITE` 类型的 Topic 并不足以让不支持的实例可用。
- 云上部署可能需要公网白名单或 VPC 接入点。
- 凭据只放在 `.env`，不要提交到代码仓库。
- `samples/InsecureOrderService.java` 是故意包含漏洞的测试样例。

## 许可证

依赖使用 Apache License 2.0 兼容许可证。发布仓库前如有需要，请补充仓库级 LICENSE。