package edu.lab.review.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import edu.lab.review.model.Dimension;

public final class LabConfig {
    public static final String DEFAULT_STUDENT_ID = "000000";
    private static final DateTimeFormatter SESSION_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault());

    private final String studentId;
    private final String endpoint;
    private final String namespace;
    private final String accessKey;
    private final String secretKey;
    private final boolean ssl;
    private final Path reportDir;
    private final int timeoutSeconds;
    private final long analysisDelayMs;
    private final String envFile;

    private LabConfig(Map<String, String> values, String envFile) {
        this.studentId = require(values, "STUDENT_ID", DEFAULT_STUDENT_ID);
        this.endpoint = require(values, "RMQ_ENDPOINT", "127.0.0.1:8081");
        this.namespace = values.getOrDefault("RMQ_NAMESPACE", "");
        this.accessKey = values.getOrDefault("RMQ_ACCESS_KEY", "");
        this.secretKey = values.getOrDefault("RMQ_SECRET_KEY", "");
        this.ssl = Boolean.parseBoolean(values.getOrDefault("RMQ_SSL", "false"));
        this.reportDir = Path.of(values.getOrDefault("REPORT_DIR", "reports"));
        this.timeoutSeconds = Integer.parseInt(values.getOrDefault("REVIEW_TIMEOUT_SECONDS", "120"));
        this.analysisDelayMs = Long.parseLong(values.getOrDefault("ANALYZE_DELAY_MS", "600"));
        this.envFile = envFile;
    }

    public static LabConfig load(String envFile) {
        Map<String, String> values = new HashMap<>();
        Path file = envFile == null || envFile.isBlank() ? null : Path.of(envFile);
        if (file != null && Files.isRegularFile(file)) {
            values.putAll(readEnvFile(file));
        }
        for (String key : new String[]{"STUDENT_ID", "RMQ_ENDPOINT", "RMQ_NAMESPACE", "RMQ_ACCESS_KEY",
            "RMQ_SECRET_KEY", "RMQ_SSL", "REPORT_DIR", "REVIEW_TIMEOUT_SECONDS", "ANALYZE_DELAY_MS"}) {
            String env = System.getenv(key);
            if (env != null && !env.isBlank()) {
                values.put(key, env.trim());
            }
        }
        values.putIfAbsent("STUDENT_ID", System.getProperty("student.id", DEFAULT_STUDENT_ID));
        values.putIfAbsent("RMQ_ENDPOINT", System.getProperty("rmq.endpoint", "127.0.0.1:8081"));
        values.putIfAbsent("RMQ_NAMESPACE", System.getProperty("rmq.namespace", ""));
        values.putIfAbsent("RMQ_ACCESS_KEY", System.getProperty("rmq.accessKey", ""));
        values.putIfAbsent("RMQ_SECRET_KEY", System.getProperty("rmq.secretKey", ""));
        values.putIfAbsent("RMQ_SSL", System.getProperty("rmq.ssl", "false"));
        values.putIfAbsent("REPORT_DIR", System.getProperty("report.dir", "reports"));
        values.putIfAbsent("REVIEW_TIMEOUT_SECONDS", System.getProperty("review.timeoutSeconds", "120"));
        values.putIfAbsent("ANALYZE_DELAY_MS", System.getProperty("analysis.delayMs", "600"));
        return new LabConfig(values, envFile);
    }

    private static String require(Map<String, String> values, String key, String defaultValue) {
        String value = values.getOrDefault(key, defaultValue);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required config: " + key);
        }
        return value.trim();
    }

    private static Map<String, String> readEnvFile(Path file) {
        Map<String, String> values = new HashMap<>();
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                int idx = line.indexOf('=');
                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
            return values;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read env file: " + file, e);
        }
    }

    public String taskTopic() {
        return "rmqai-" + studentId + "-review-task";
    }

    public String resultTopic() {
        return "rmqai-" + studentId + "-review-result";
    }

    public String groupOf(Dimension dimension) {
        return "GID_" + studentId + "_" + dimension.name().toLowerCase(Locale.ROOT);
    }

    public String supervisorGroup() {
        return "GID_" + studentId + "_supervisor";
    }

    public String newSessionId() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return studentId + "-review-" + SESSION_TIME.format(java.time.Instant.now()) + "-" + suffix;
    }

    public String key(String sessionId, Dimension dimension, String stage) {
        return studentId + "_" + sessionId + "_" + dimension.name() + "_" + stage;
    }

    public String maskedEndpoint() {
        return endpoint;
    }

    public String credentialSummary() {
        if (accessKey == null || accessKey.isBlank()) {
            return "no-credential(local)";
        }
        String masked = accessKey.length() <= 4 ? "****" : accessKey.substring(0, 3) + "****"
            + accessKey.substring(accessKey.length() - 2);
        return masked;
    }

    public boolean hasCredentials() {
        return accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank();
    }

    public String getStudentId() {
        return studentId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public boolean isSsl() {
        return ssl;
    }

    public Path getReportDir() {
        return reportDir;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public long getAnalysisDelayMs() {
        return analysisDelayMs;
    }

    public String getEnvFile() {
        return envFile;
    }

    @Override
    public String toString() {
        return "LabConfig{" +
            "studentId='" + studentId + '\'' +
            ", endpoint='" + endpoint + '\'' +
            ", namespace='" + namespace + '\'' +
            ", credential=" + credentialSummary() +
            ", ssl=" + ssl +
            ", taskTopic='" + taskTopic() + '\'' +
            ", resultTopic='" + resultTopic() + '\'' +
            ", reportDir=" + reportDir +
            ", timeoutSeconds=" + timeoutSeconds +
            ", analysisDelayMs=" + analysisDelayMs +
            '}';
    }
}