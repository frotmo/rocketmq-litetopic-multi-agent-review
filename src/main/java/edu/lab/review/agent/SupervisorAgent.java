package edu.lab.review.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import edu.lab.review.analysis.InputLoader;
import edu.lab.review.config.LabConfig;
import edu.lab.review.config.RocketMqFacade;
import edu.lab.review.messaging.LiteResultCollector;
import edu.lab.review.messaging.TaskPublisher;
import edu.lab.review.model.Dimension;
import edu.lab.review.model.Finding;
import edu.lab.review.model.ReviewResult;
import edu.lab.review.model.ReviewTask;
import edu.lab.review.report.ReportRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SupervisorAgent {
    private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);
    private static final Set<String> REVIEW_DIMENSIONS = Set.of("STYLE", "LOGIC", "SECURITY");

    private final LabConfig config;
    private final RocketMqFacade facade;

    public SupervisorAgent(LabConfig config, RocketMqFacade facade) {
        this.config = config;
        this.facade = facade;
    }

    public SupervisorReport run(InputLoader.LoadedInput input, String sessionOverride) throws Exception {
        String sessionId = normalizeSession(sessionOverride);
        log.info("[supervisor] start session={} source={} endpoint={} taskTopic={} resultTopic={}",
            sessionId, input.location(), config.getEndpoint(), config.taskTopic(), config.resultTopic());

        TaskPublisher publisher = new TaskPublisher(config, facade.producer(), facade.provider());
        List<ReviewTask> tasks = new ArrayList<>();
        List<ReviewResult> dimensionResults;

        try (LiteResultCollector collector = LiteResultCollector.open(config, facade, sessionId)) {
            for (Dimension dimension : List.of(Dimension.STYLE, Dimension.LOGIC, Dimension.SECURITY)) {
                ReviewTask task = baseTask(input, sessionId, dimension, "DIMENSION");
                publisher.dispatch(task);
                tasks.add(task);
            }

            Duration timeout = Duration.ofSeconds(config.getTimeoutSeconds());
            dimensionResults = collector.awaitDimensions(REVIEW_DIMENSIONS, timeout);
            for (ReviewResult result : dimensionResults) {
                for (ReviewTask task : tasks) {
                    if (task.getTaskId().equals(result.getTaskId())) {
                        result.setTaskMessageId(task.getMessageId());
                        result.setRequestId(task.getRequestId());
                        break;
                    }
                }
            }
            Set<String> found = new LinkedHashSet<>();
            for (ReviewResult result : dimensionResults) {
                found.add(result.getDimension());
            }
            for (String dimension : REVIEW_DIMENSIONS) {
                if (!found.contains(dimension)) {
                    log.warn("[supervisor] dimension result missing or timed out: {}", dimension);
                }
            }
            log.info("[supervisor] collected {}/{} dimension results", dimensionResults.size(),
                REVIEW_DIMENSIONS.size());

            ReviewTask summaryTask = baseTask(input, sessionId, Dimension.SUMMARY, "SUMMARY");
            summaryTask.setResults(dimensionResults);
            publisher.dispatch(summaryTask);
            tasks.add(summaryTask);

            ReviewResult summary = collector.awaitSummary(timeout);
            if (summary == null) {
                log.warn("[supervisor] SummaryAgent result timeout, rendering fallback report");
                summary = fallbackSummary(summaryTask, dimensionResults);
            }
            summary.setTaskMessageId(summaryTask.getMessageId());
            summary.setRequestId(summaryTask.getRequestId());
            // Re-render after the supervisor knows both message ids, so the trace appendix is complete.
            summary.setReportMarkdown(ReportRenderer.render(config, summaryTask, dimensionResults, summary));

            Path markdownPath = writeMarkdown(sessionId, summary.getReportMarkdown());
            summary.setReportFile(markdownPath.toString());
            Path jsonPath = writeJson(sessionId, input, tasks, dimensionResults, summary, markdownPath);

            System.out.println();
            System.out.println("==== Review completed ====");
            System.out.println("session      : " + sessionId);
            System.out.println("risk         : " + ReportRenderer.riskLevel(ReportRenderer.riskScore(summary.getFindings()))
                + " (score=" + ReportRenderer.riskScore(summary.getFindings()) + "/100)");
            System.out.println("findings     : " + summary.getFindings().size());
            System.out.println("report (md)  : " + markdownPath.toAbsolutePath());
            System.out.println("report (json): " + jsonPath.toAbsolutePath());
            System.out.println("==========================");
            return new SupervisorReport(sessionId, summary, dimensionResults, tasks, markdownPath, jsonPath);
        }
    }

    private ReviewTask baseTask(InputLoader.LoadedInput input, String sessionId, Dimension dimension, String stage) {
        ReviewTask task = new ReviewTask();
        task.setStudentId(config.getStudentId());
        task.setSessionId(sessionId);
        task.setTaskId(config.getStudentId() + "-" + dimension.name().toLowerCase() + "-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        task.setDimension(dimension.name());
        task.setStage(stage);
        task.setSourceType(input.type());
        task.setSourceLocation(input.location());
        task.setSourceTitle(input.title());
        task.setLanguage(input.language());
        task.setCode(input.code());
        return task;
    }

    private String normalizeSession(String sessionOverride) {
        if (sessionOverride == null || sessionOverride.isBlank()) {
            return config.newSessionId();
        }
        String trimmed = sessionOverride.strip();
        return trimmed.startsWith(config.getStudentId() + "-") ? trimmed
            : config.getStudentId() + "-" + trimmed;
    }

    private ReviewResult fallbackSummary(ReviewTask summaryTask, List<ReviewResult> dimensions) {
        List<Finding> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ReviewResult result : dimensions) {
            if (result.getFindings() == null) {
                continue;
            }
            for (Finding finding : result.getFindings()) {
                String key = finding.getDimension() + ":" + finding.getRuleId() + ":" + finding.getLine();
                if (seen.add(key)) {
                    merged.add(finding);
                }
            }
        }
        merged.sort(Comparator.comparingInt((Finding f) -> -f.getSeverity().rank())
            .thenComparing(Finding::getDimension)
            .thenComparingInt(Finding::getLine));
        long now = System.currentTimeMillis();
        ReviewResult fallback = new ReviewResult();
        fallback.setStudentId(config.getStudentId());
        fallback.setSessionId(summaryTask.getSessionId());
        fallback.setTaskId(summaryTask.getTaskId());
        fallback.setRequestId(summaryTask.getRequestId());
        fallback.setTaskMessageId(summaryTask.getMessageId());
        fallback.setDimension(Dimension.SUMMARY.name());
        fallback.setStage("SUMMARY");
        fallback.setAgentId("SupervisorFallback");
        fallback.setStatus("TIMEOUT");
        fallback.setError("SummaryAgent result timeout; supervisor rendered fallback report");
        fallback.setDispatchedAt(summaryTask.getDispatchedAt());
        fallback.setStartedAt(now);
        fallback.setFinishedAt(now);
        fallback.setDurationMs(0);
        fallback.setFindings(merged);
        fallback.setStats(ReviewResult.statsOf(merged));
        return fallback;
    }

    private Path writeMarkdown(String sessionId, String markdown) throws IOException {
        Files.createDirectories(config.getReportDir());
        Path path = config.getReportDir().resolve(sessionId + ".md");
        Files.writeString(path, markdown, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeJson(String sessionId, InputLoader.LoadedInput input, List<ReviewTask> tasks,
                           List<ReviewResult> dimensionResults, ReviewResult summary, Path markdownPath)
        throws IOException {
        Files.createDirectories(config.getReportDir());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("studentId", config.getStudentId());
        payload.put("sessionId", sessionId);
        payload.put("source", Map.of(
            "type", input.type(),
            "location", input.location(),
            "title", input.title(),
            "language", input.language()));
        payload.put("taskTopic", config.taskTopic());
        payload.put("resultTopic", config.resultTopic());
        payload.put("groups", Map.of(
            "style", config.groupOf(Dimension.STYLE),
            "logic", config.groupOf(Dimension.LOGIC),
            "security", config.groupOf(Dimension.SECURITY),
            "summary", config.groupOf(Dimension.SUMMARY),
            "supervisor", config.supervisorGroup()));
        List<Map<String, Object>> taskRefs = new ArrayList<>();
        for (ReviewTask task : tasks) {
            Dimension dimension = Dimension.valueOf(task.getDimension());
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("dimension", task.getDimension());
            ref.put("stage", task.getStage());
            ref.put("taskId", task.getTaskId());
            ref.put("requestId", task.getRequestId());
            ref.put("messageId", task.getMessageId());
            ref.put("key", config.key(sessionId, dimension, "REQ"));
            ref.put("dispatchedAt", task.getDispatchedAt());
            taskRefs.add(ref);
        }
        payload.put("tasks", taskRefs);
        payload.put("dimensionResults", dimensionResults);
        payload.put("summary", summary);
        payload.put("reportFile", markdownPath.toAbsolutePath().toString());
        Path path = config.getReportDir().resolve(sessionId + ".json");
        Files.writeString(path, JSON.toJSONString(payload, SerializerFeature.PrettyFormat,
            SerializerFeature.WriteMapNullValue), StandardCharsets.UTF_8);
        return path;
    }

    public record SupervisorReport(String sessionId, ReviewResult summary, List<ReviewResult> dimensionResults,
                                   List<ReviewTask> tasks, Path markdownPath, Path jsonPath) {
    }
}