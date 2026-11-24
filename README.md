**English** | [简体中文](README.zh-CN.md)

# RocketMQ LiteTopic Multi-Agent Code Review

A five-agent code review system built on **Apache RocketMQ 5.x LiteTopic** and the
[`rocketmq-a2a`](https://github.com/apache/rocketmq-a2a) SDK.

The Supervisor splits a review into style, logic and security dimensions. Three
Worker Agents consume the tasks in parallel, return session-scoped results over a
LiteTopic, and a Summary Agent generates a structured Markdown/JSON report.

## Agents

| Agent | Responsibility |
|---|---|
| SupervisorAgent | Splits the review, dispatches tasks, subscribes to the session LiteTopic, collects results, writes the report |
| StyleAgent | Naming, formatting, imports, method length, readability checks |
| LogicAgent | Null/edge cases, assignment-in-condition, off-by-one, resource leaks, exception handling |
| SecurityAgent | SQL injection, XSS, command injection, hardcoded secrets, SSRF, XXE, deserialization and related patterns |
| SummaryAgent | Deduplicates findings, computes risk, renders the report |

## Architecture

- **Normal Topic** `rmqai-{studentId}-review-task`: task distribution with tags
  `STYLE`, `LOGIC`, `SECURITY`, `SUMMARY`.
- **Lite parent Topic** `rmqai-{studentId}-review-result`: result collection.
- One **LiteTopic per review session** (`{studentId}-review-{timestamp}-{suffix}`).
- Four normal Consumer Groups plus one Lite Consumer Group for the Supervisor.
- All resource names and message keys use `{studentId}` as the prefix.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the topology, message flow and naming rules.

## Requirements

- JDK 17+
- Maven 3.9+
- RocketMQ 5.x with LiteTopic support:
  - Open source: RocketMQ 5.5+ Broker with `enableLmq=true` and `enableMultiDispatch=true`
  - Cloud: an instance with LiteTopic capability and a gRPC endpoint
- gRPC access to the Broker/Proxy. The application uses `rocketmq-client-java` 5.x.

## Quick start

### 1. Configure

```bash
cp .env.example .env
```

Edit `.env`:

```properties
STUDENT_ID=<your-id>
RMQ_ENDPOINT=<host>:8080
RMQ_NAMESPACE=
RMQ_ACCESS_KEY=<username>
RMQ_SECRET_KEY=<password>
RMQ_SSL=false
```

`STUDENT_ID` is required for resource and message-key naming. The code has a
placeholder default (`000000`) so it can start, but use your own prefix for a real
deployment.

Do not commit `.env`; it is ignored by `.gitignore`.

### 2. Create RocketMQ resources

The application expects these resources:

| Resource | Name |
|---|---|
| Normal Topic | `rmqai-{studentId}-review-task` |
| Lite parent Topic | `rmqai-{studentId}-review-result` |
| Consumer Groups | `GID_{studentId}_style`, `_logic`, `_security`, `_summary` |
| Lite Consumer Group | `GID_{studentId}_supervisor` (bound to the Lite parent Topic) |

Create them in your RocketMQ console, or on a self-hosted cluster:

```bash
export STUDENT_ID=<your-id>
export ROCKETMQ_HOME=/path/to/rocketmq
export NAMESRV=127.0.0.1:9876
./scripts/setup-resources.sh
```

The Supervisor group must be a Lite group bound to
`rmqai-{studentId}-review-result`. The four Worker groups are normal groups; the
topic and tag are supplied by the client at subscription time.

### 3. Build

```bash
./scripts/build.sh
```

If JDK 17 is not auto-detected:

```bash
APP_JAVA_HOME=/path/to/jdk17 ./scripts/build.sh
```

### 4. Run

All five agents in one JVM:

```bash
./scripts/run-all.sh --env-file .env --file samples/InsecureOrderService.java
```

Separate processes:

```bash
./scripts/run-worker.sh style --env-file .env
./scripts/run-worker.sh logic --env-file .env
./scripts/run-worker.sh security --env-file .env
./scripts/run-worker.sh summary --env-file .env
./scripts/run-supervisor.sh --env-file .env --file samples/InsecureOrderService.java
```

Reviewed inputs:

```bash
./scripts/run-all.sh --env-file .env --file path/to/File.java
./scripts/run-all.sh --env-file .env --pr https://github.com/owner/repo/pull/123
./scripts/run-all.sh --env-file .env --code 'class A { void f() { if (x = 1) {} } }'
```

Reports are written to `reports/{sessionId}.md` and `reports/{sessionId}.json`.

## Configuration

| Variable | Default | Description |
|---|---|---|
| `STUDENT_ID` | `000000` | Resource/session/key prefix |
| `RMQ_ENDPOINT` | `127.0.0.1:8081` | RocketMQ gRPC endpoint |
| `RMQ_NAMESPACE` | empty | Namespace if required by the deployment |
| `RMQ_ACCESS_KEY` | empty | Username / access key |
| `RMQ_SECRET_KEY` | empty | Password / secret key |
| `RMQ_SSL` | `false` | Enable gRPC TLS |
| `REVIEW_TIMEOUT_SECONDS` | `120` | Result wait timeout |
| `ANALYZE_DELAY_MS` | `600` | Simulated agent reasoning latency |
| `REPORT_DIR` | `reports` | Report output directory |

The rules engine runs locally and makes no external model calls. `ANALYZE_DELAY_MS`
only simulates reasoning latency so parallel execution is visible in the report.

## Notes

- LiteTopic requires server-side support; creating a Topic with a `LITE` message
  type is not enough on an unsupported instance.
- Cloud deployments may require a public-access whitelist or a VPC endpoint.
- Keep credentials in `.env`, never in source control.
- `samples/InsecureOrderService.java` is intentionally vulnerable test data.

## License

Apache License 2.0-compatible dependencies are used. Add a repository license
before publishing if needed.