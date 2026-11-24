package edu.lab.review.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReviewResult {
    private int schemaVersion = 1;
    private String studentId;
    private String sessionId;
    private String taskId;
    private String requestId;
    private String messageId;
    private String taskMessageId;
    private String dimension;
    private String stage;
    private String agentId;
    private String status = "OK";
    private String summary = "";
    private String error;
    private long dispatchedAt;
    private long startedAt;
    private long finishedAt;
    private long durationMs;
    private List<Finding> findings = List.of();
    private Map<String, Integer> stats = new LinkedHashMap<>();
    private String reportMarkdown;
    private String reportFile;

    public static ReviewResult error(ReviewTask task, String agentId, String error, long startedAt) {
        ReviewResult result = new ReviewResult();
        result.setStudentId(task.getStudentId());
        result.setSessionId(task.getSessionId());
        result.setTaskId(task.getTaskId());
        result.setRequestId(task.getRequestId());
        result.setTaskMessageId(task.getMessageId());
        result.setDimension(task.getDimension());
        result.setStage(task.getStage());
        result.setAgentId(agentId);
        result.setStatus("ERROR");
        result.setError(error);
        result.setStartedAt(startedAt);
        result.setFinishedAt(System.currentTimeMillis());
        result.setDurationMs(result.getFinishedAt() - startedAt);
        return result;
    }

    public static Map<String, Integer> statsOf(List<Finding> findings) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (Severity severity : Severity.values()) {
            stats.put(severity.name(), 0);
        }
        for (Finding finding : findings) {
            stats.merge(finding.getSeverity().name(), 1, Integer::sum);
        }
        return stats;
    }

    public int count(Severity severity) {
        return stats.getOrDefault(severity.name(), 0);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getTaskMessageId() {
        return taskMessageId;
    }

    public void setTaskMessageId(String taskMessageId) {
        this.taskMessageId = taskMessageId;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(long dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(long finishedAt) {
        this.finishedAt = finishedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public List<Finding> getFindings() {
        return findings;
    }

    public void setFindings(List<Finding> findings) {
        this.findings = findings;
    }

    public Map<String, Integer> getStats() {
        return stats;
    }

    public void setStats(Map<String, Integer> stats) {
        this.stats = stats;
    }

    public String getReportMarkdown() {
        return reportMarkdown;
    }

    public void setReportMarkdown(String reportMarkdown) {
        this.reportMarkdown = reportMarkdown;
    }

    public String getReportFile() {
        return reportFile;
    }

    public void setReportFile(String reportFile) {
        this.reportFile = reportFile;
    }
}