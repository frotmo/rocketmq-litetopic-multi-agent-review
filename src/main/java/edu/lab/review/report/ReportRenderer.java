package edu.lab.review.report;

import edu.lab.review.config.LabConfig;
import edu.lab.review.model.Dimension;
import edu.lab.review.model.Finding;
import edu.lab.review.model.ReviewResult;
import edu.lab.review.model.ReviewTask;
import edu.lab.review.model.Severity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReportRenderer {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault());
    private static final List<Dimension> DIMENSIONS = List.of(Dimension.STYLE, Dimension.LOGIC, Dimension.SECURITY);

    private ReportRenderer() {
    }

    public static int riskScore(List<Finding> findings) {
        int score = 0;
        for (Finding finding : findings) {
            score += finding.getSeverity().weight();
        }
        return Math.min(100, score);
    }

    public static String riskLevel(int score) {
        if (score >= 70) {
            return "CRITICAL";
        }
        if (score >= 40) {
            return "HIGH";
        }
        if (score >= 15) {
            return "MEDIUM";
        }
        if (score > 0) {
            return "LOW";
        }
        return "NONE";
    }

    public static String render(LabConfig config, ReviewTask summaryTask,
                                List<ReviewResult> dimensionResults, ReviewResult summary) {
        List<Finding> merged = summary.getFindings() == null ? List.of() : summary.getFindings();
        Map<String, Integer> stats = summary.getStats() == null ? ReviewResult.statsOf(merged) : summary.getStats();
        int score = riskScore(merged);
        String level = riskLevel(score);
        List<ReviewResult> ordered = orderResults(dimensionResults);

        StringBuilder md = new StringBuilder(16_384);
        md.append("# 多智能体代码协作审查报告\n\n");
        md.append("> 本报告由 Supervisor / Style / Logic / Security / Summary 五类 Agent 通过 ")
            .append("Apache RocketMQ Normal Topic + LiteTopic 异步协作生成。\n\n");

        md.append("## 1. 基本信息\n\n");
        md.append("| 项目 | 内容 |\n|---|---|\n");
        md.append("| 学号 | `").append(config.getStudentId()).append("` |\n");
        md.append("| Session / LiteTopic | `").append(summaryTask.getSessionId()).append("` |\n");
        md.append("| 审查对象 | ").append(cell(summaryTask.getSourceTitle())).append(" |\n");
        md.append("| 来源 | ").append(cell(summaryTask.getSourceType() + " · " + summaryTask.getSourceLocation())).append(" |\n");
        md.append("| 语言 | ").append(cell(summaryTask.getLanguage())).append(" |\n");
        md.append("| 代码规模 | ").append(lineCount(summaryTask.getCode())).append(" 行 / ")
            .append(summaryTask.getCode() == null ? 0 : summaryTask.getCode().length()).append(" 字符 |\n");
        md.append("| 派发时间 | ").append(ts(firstDispatch(summaryTask, dimensionResults))).append(" |\n");
        md.append("| 报告生成时间 | ").append(ts(summary.getFinishedAt())).append(" |\n");
        md.append("| 综合风险 | **").append(level).append("**（score=").append(score).append("/100） |\n\n");

        md.append("## 2. 执行摘要\n\n");
        md.append("三个审查 Agent 通过 `").append(config.taskTopic()).append("` 的不同 Tag 并行消费任务，")
            .append("将各自结果写入以 `").append(summaryTask.getSessionId()).append("` 为 LiteTopic 的会话通道；")
            .append("SummaryAgent 汇总后生成本报告。\n\n");
        md.append("共发现 **").append(merged.size()).append("** 项问题：")
            .append("Critical ").append(stats.getOrDefault("CRITICAL", 0)).append("，")
            .append("High ").append(stats.getOrDefault("HIGH", 0)).append("，")
            .append("Medium ").append(stats.getOrDefault("MEDIUM", 0)).append("，")
            .append("Low ").append(stats.getOrDefault("LOW", 0)).append("。\n\n");

        List<Finding> top = new ArrayList<>(merged);
        top.sort(Comparator.comparingInt((Finding f) -> -f.getSeverity().rank())
            .thenComparingInt(Finding::getLine));
        if (top.isEmpty()) {
            md.append("> 本次审查未发现规则命中项。\n\n");
        } else {
            md.append("**优先处理项（Top 5）**\n\n");
            int limit = Math.min(5, top.size());
            for (int i = 0; i < limit; i++) {
                Finding f = top.get(i);
                md.append(i + 1).append(". **[").append(f.getSeverity()).append("] ")
                    .append(f.getTitle()).append("**（L").append(f.getLine()).append("，")
                    .append(f.getRuleId()).append("）\n");
            }
            md.append('\n');
        }

        md.append("## 3. 并行执行时间线\n\n");
        md.append("| 维度 | Agent | 派发时间 | 开始时间 | 排队(ms) | 完成时间 | 处理耗时(ms) | 问题数 | 状态 |\n");
        md.append("|---|---|---|---|---|---|---|---|---|\n");
        for (ReviewResult result : ordered) {
            md.append("| ").append(result.getDimension()).append(" | ").append(result.getAgentId())
                .append(" | ").append(ts(result.getDispatchedAt()))
                .append(" | ").append(ts(result.getStartedAt()))
                .append(" | ").append(result.getStartedAt() > 0 && result.getDispatchedAt() > 0
                    ? Math.max(0, result.getStartedAt() - result.getDispatchedAt()) : "-")
                .append(" | ").append(ts(result.getFinishedAt()))
                .append(" | ").append(result.getDurationMs())
                .append(" | ").append(result.getFindings() == null ? 0 : result.getFindings().size())
                .append(" | ").append(result.getStatus()).append(" |\n");
        }
        md.append("| SUMMARY | ").append(summary.getAgentId())
            .append(" | ").append(ts(summary.getDispatchedAt()))
            .append(" | ").append(ts(summary.getStartedAt()))
            .append(" | ").append(summary.getStartedAt() > 0 && summary.getDispatchedAt() > 0
                ? Math.max(0, summary.getStartedAt() - summary.getDispatchedAt()) : "-")
            .append(" | ").append(ts(summary.getFinishedAt()))
            .append(" | ").append(summary.getDurationMs())
            .append(" | ").append(merged.size())
            .append(" | ").append(summary.getStatus()).append(" |\n\n");

        long window = overlapWindow(ordered);
        long wall = wallClock(ordered, summary);
        md.append("**并行性证据**：三个维度的任务由不同 Consumer Group 并发消费。");
        if (window > 0) {
            md.append("其处理时间窗口存在 **").append(window).append(" ms** 的重叠，")
                .append("说明 Style/Logic/Security 在同一时间段并行执行，而非串行排队。");
        } else {
            md.append("可通过各 Agent 的派发/开始时间与处理耗时观察任务并发处理。");
        }
        md.append("从首次派发到汇总完成的总墙钟时间约 **").append(wall).append(" ms**")
            .append("（各 Agent 模拟推理耗时 ").append(config.getAnalysisDelayMs()).append(" ms）。\n\n");

        md.append("## 4. 各维度审查结果\n\n");
        for (Dimension dimension : DIMENSIONS) {
            ReviewResult result = find(ordered, dimension.name());
            md.append("### 4.").append(DIMENSIONS.indexOf(dimension) + 1).append(' ')
                .append(dimension).append(" 维度\n\n");
            if (result == null) {
                md.append("> 未收到该维度的结果（可能超时或 Agent 未启动）。\n\n");
                continue;
            }
            if ("ERROR".equals(result.getStatus())) {
                md.append("> 该维度执行失败：").append(cell(result.getError())).append("\n\n");
                continue;
            }
            List<Finding> findings = new ArrayList<>(result.getFindings() == null ? List.of() : result.getFindings());
            findings.sort(Comparator.comparingInt((Finding f) -> -f.getSeverity().rank())
                .thenComparingInt(Finding::getLine));
            md.append("Agent：`").append(result.getAgentId()).append("`，耗时 ")
                .append(result.getDurationMs()).append(" ms，发现 ").append(findings.size()).append(" 项。\n\n");
            if (findings.isEmpty()) {
                md.append("本维度未发现问题。\n\n");
                continue;
            }
            for (Finding f : findings) {
                md.append("#### [").append(f.getSeverity()).append("] ")
                    .append(f.getRuleId()).append(" — ").append(f.getTitle()).append("\n\n");
                md.append("- 位置：第 **").append(f.getLine()).append("** 行：`")
                    .append(escapeInline(f.getLineText())).append("`\n");
                if (f.getCwe() != null && !f.getCwe().isBlank()) {
                    md.append("- 分类：").append(f.getCwe()).append('\n');
                }
                md.append("- 说明：").append(f.getDescription()).append('\n');
                md.append("- 建议：").append(f.getSuggestion()).append("\n\n");
            }
        }

        md.append("## 5. 汇总统计\n\n");
        md.append("| 维度 | Critical | High | Medium | Low | Info | 合计 |\n|---|---|---|---|---|---|---|\n");
        for (ReviewResult result : ordered) {
            Map<String, Integer> s = result.getStats() == null ? Map.of() : result.getStats();
            md.append("| ").append(result.getDimension())
                .append(" | ").append(s.getOrDefault("CRITICAL", 0))
                .append(" | ").append(s.getOrDefault("HIGH", 0))
                .append(" | ").append(s.getOrDefault("MEDIUM", 0))
                .append(" | ").append(s.getOrDefault("LOW", 0))
                .append(" | ").append(s.getOrDefault("INFO", 0))
                .append(" | ").append(result.getFindings() == null ? 0 : result.getFindings().size())
                .append(" |\n");
        }
        md.append("| **汇总（去重后）** | **").append(stats.getOrDefault("CRITICAL", 0))
            .append("** | **").append(stats.getOrDefault("HIGH", 0))
            .append("** | **").append(stats.getOrDefault("MEDIUM", 0))
            .append("** | **").append(stats.getOrDefault("LOW", 0))
            .append("** | **").append(stats.getOrDefault("INFO", 0))
            .append("** | **").append(merged.size()).append("** |\n\n");

        int rawCount = ordered.stream()
            .mapToInt(r -> r.getFindings() == null ? 0 : r.getFindings().size()).sum();
        md.append("跨维度聚合时按 `dimension + ruleId + line` 去重，原始命中 ")
            .append(rawCount).append(" 条，去重后 ").append(merged.size()).append(" 条。\n\n");

        md.append("## 6. Topic 规划与消息轨迹索引\n\n");
        md.append("| 资源 | 名称 | 用途 |\n|---|---|---|\n");
        md.append("| Normal Topic | `").append(config.taskTopic()).append("` | 任务分发（Tag: STYLE/LOGIC/SECURITY/SUMMARY） |\n");
        md.append("| Consumer Group | `").append(config.groupOf(Dimension.STYLE)).append("` | StyleAgent 消费 STYLE 任务 |\n");
        md.append("| Consumer Group | `").append(config.groupOf(Dimension.LOGIC)).append("` | LogicAgent 消费 LOGIC 任务 |\n");
        md.append("| Consumer Group | `").append(config.groupOf(Dimension.SECURITY)).append("` | SecurityAgent 消费 SECURITY 任务 |\n");
        md.append("| Consumer Group | `").append(config.groupOf(Dimension.SUMMARY)).append("` | SummaryAgent 消费 SUMMARY 任务 |\n");
        md.append("| LITE Parent Topic | `").append(config.resultTopic()).append("` | 会话级结果回传（message.type=LITE） |\n");
        md.append("| LiteTopic / Session | `").append(summaryTask.getSessionId()).append("` | 单次审查会话的轻量通道 |\n");
        md.append("| Consumer Group | `").append(config.supervisorGroup()).append("` | Supervisor 订阅该 LiteTopic |\n\n");

        md.append("| 维度 | Task Key | Task MsgId | Result Key | Result MsgId |\n|---|---|---|---|---|\n");
        for (ReviewResult result : ordered) {
            Dimension dimension = Dimension.valueOf(result.getDimension());
            md.append("| ").append(result.getDimension())
                .append(" | `").append(config.key(summaryTask.getSessionId(), dimension, "REQ")).append("`")
                .append(" | `").append(nullToDash(result.getTaskMessageId())).append("`")
                .append(" | `").append(config.key(summaryTask.getSessionId(), dimension, "RES")).append("`")
                .append(" | `").append(nullToDash(result.getMessageId())).append("` |\n");
        }
        md.append("| SUMMARY | `").append(config.key(summaryTask.getSessionId(), Dimension.SUMMARY, "REQ"))
            .append("` | `").append(nullToDash(summaryTask.getMessageId())).append('`')
            .append(" | `").append(config.key(summaryTask.getSessionId(), Dimension.SUMMARY, "RES")).append('`')
            .append(" | `").append(nullToDash(summary.getMessageId())).append("` |\n\n");

        md.append("本地核验命令（阿里云控制台可在 Topic → 消息查询 → Key 中按同样 Key 检索）：\n\n");
        md.append("```bash\n");
        md.append("mqadmin queryMsgByKey -n 127.0.0.1:9876 -t ").append(config.taskTopic())
            .append(" -k ").append(config.key(summaryTask.getSessionId(), Dimension.STYLE, "REQ")).append('\n');
        md.append("mqadmin queryMsgByKey -n 127.0.0.1:9876 -t ").append(config.resultTopic())
            .append(" -k ").append(config.key(summaryTask.getSessionId(), Dimension.SUMMARY, "RES")).append('\n');
        md.append("```\n\n");

        md.append("## 7. 结论与建议\n\n");
        if (score >= 40) {
            md.append("本次审查存在 **高危及以上** 问题，建议在合并前完成修复，重点处理 SQL 注入、")
                .append("命令执行、硬编码凭据等高风险项，并补充针对边界条件与异常路径的单元测试。\n");
        } else if (score > 0) {
            md.append("本次审查发现若干中低风险问题，建议按优先级修复命名/可读性问题，")
                .append("并对边界条件与异常处理补充测试。\n");
        } else {
            md.append("本次审查未发现规则命中项，仍建议结合人工评审与动态测试验证业务逻辑。\n");
        }
        md.append("\n---\n\n");
        md.append("报告由 SummaryAgent 生成；各维度问题由对应 Agent 独立产出，Supervisor 仅负责编排与落盘。\n");
        return md.toString();
    }

    private static List<ReviewResult> orderResults(List<ReviewResult> results) {
        List<ReviewResult> ordered = new ArrayList<>();
        for (Dimension dimension : DIMENSIONS) {
            ReviewResult result = find(results, dimension.name());
            if (result != null) {
                ordered.add(result);
            }
        }
        return ordered;
    }

    private static ReviewResult find(List<ReviewResult> results, String dimension) {
        for (ReviewResult result : results) {
            if (dimension.equals(result.getDimension())) {
                return result;
            }
        }
        return null;
    }

    private static long firstDispatch(ReviewTask summaryTask, List<ReviewResult> results) {
        long first = summaryTask.getDispatchedAt();
        for (ReviewResult result : results) {
            if (result.getDispatchedAt() > 0 && (first <= 0 || result.getDispatchedAt() < first)) {
                first = result.getDispatchedAt();
            }
        }
        return first;
    }

    private static long overlapWindow(List<ReviewResult> results) {
        long maxStart = Long.MIN_VALUE;
        long minEnd = Long.MAX_VALUE;
        for (ReviewResult result : results) {
            if (result.getStartedAt() <= 0 || result.getFinishedAt() <= 0) {
                continue;
            }
            maxStart = Math.max(maxStart, result.getStartedAt());
            minEnd = Math.min(minEnd, result.getFinishedAt());
        }
        return maxStart == Long.MIN_VALUE ? 0 : Math.max(0, minEnd - maxStart);
    }

    private static long wallClock(List<ReviewResult> results, ReviewResult summary) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (ReviewResult result : results) {
            if (result.getDispatchedAt() > 0) {
                min = Math.min(min, result.getDispatchedAt());
            }
            if (result.getFinishedAt() > 0) {
                max = Math.max(max, result.getFinishedAt());
            }
        }
        if (summary.getDispatchedAt() > 0) {
            min = Math.min(min, summary.getDispatchedAt());
        }
        if (summary.getFinishedAt() > 0) {
            max = Math.max(max, summary.getFinishedAt());
        }
        return min == Long.MAX_VALUE || max == Long.MIN_VALUE ? 0 : Math.max(0, max - min);
    }

    private static int lineCount(String code) {
        return code == null || code.isEmpty() ? 0 : code.split("\\R", -1).length;
    }

    private static String ts(long millis) {
        return millis <= 0 ? "-" : TS.format(Instant.ofEpochMilli(millis));
    }

    private static String cell(String value) {
        return value == null ? "-" : value.replace("|", "\\|").replace("\n", " ");
    }

    private static String escapeInline(String value) {
        return value == null ? "" : value.replace("`", "'").replace("\n", " ");
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public static Map<String, Integer> emptyStats() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (Severity severity : Severity.values()) {
            stats.put(severity.name(), 0);
        }
        return stats;
    }
}