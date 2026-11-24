package edu.lab.review.analysis;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InputLoader {
    private static final Pattern GITHUB_PR = Pattern.compile(
        "^https?://github\\.com/([^/]+)/([^/]+)/pull/(\\d+)(?:/.*)?$");

    private InputLoader() {
    }

    public record LoadedInput(String type, String location, String title, String language, String code) {
    }

    public static LoadedInput load(String file, String pr, String inlineCode, String language) throws IOException {
        if (inlineCode != null && !inlineCode.isBlank()) {
            return new LoadedInput("snippet", "inline", "Inline code snippet",
                languageOr(language, "java"), inlineCode);
        }
        if (pr != null && !pr.isBlank()) {
            return loadPullRequest(pr, languageOr(language, "java"));
        }
        if (file != null && !file.isBlank()) {
            Path path = Path.of(file);
            return new LoadedInput("file", path.toAbsolutePath().toString(), path.getFileName().toString(),
                languageOr(language, inferLanguage(path)), Files.readString(path, StandardCharsets.UTF_8));
        }
        Path sample = Path.of("samples", "InsecureOrderService.java");
        if (Files.isRegularFile(sample)) {
            return new LoadedInput("file", sample.toAbsolutePath().toString(), sample.getFileName().toString(),
                languageOr(language, "java"), Files.readString(sample, StandardCharsets.UTF_8));
        }
        throw new IllegalArgumentException("No input. Use --file, --pr or --code.");
    }

    private static LoadedInput loadPullRequest(String url, String language) throws IOException {
        Matcher matcher = GITHUB_PR.matcher(url.strip());
        String diffUrl;
        String title;
        if (matcher.matches()) {
            String owner = matcher.group(1);
            String repo = matcher.group(2);
            String number = matcher.group(3);
            diffUrl = "https://patch-diff.githubusercontent.com/raw/" + owner + "/" + repo
                + "/pull/" + number + ".diff";
            title = "PR " + owner + "/" + repo + "#" + number + "（行号相对新增行）";
        } else if (url.endsWith(".diff")) {
            diffUrl = url;
            title = "PR diff: " + url;
        } else {
            throw new IllegalArgumentException("Unsupported PR URL: " + url
                + " (expected https://github.com/{owner}/{repo}/pull/{number})");
        }

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(diffUrl))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/vnd.github.v3.diff")
            .header("User-Agent", "rocketmq-multiagent-review-lab");
        String token = System.getenv("GITHUB_TOKEN");
        if (token != null && !token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response;
        try {
            response = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching PR diff", e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch PR diff: HTTP " + response.statusCode()
                + " (private repo requires GITHUB_TOKEN)");
        }
        String extracted = extractAddedLines(response.body());
        if (extracted.isBlank()) {
            throw new IOException("PR diff contains no added lines");
        }
        return new LoadedInput("pr", url, title, language,
            "// PR diff: added lines only. Finding line numbers are relative to this extracted patch.\n"
                + extracted);
    }

    private static String extractAddedLines(String diff) {
        StringBuilder code = new StringBuilder();
        for (String line : diff.split("\\R", -1)) {
            if (line.startsWith("+++ ")) {
                String path = line.substring(4).strip();
                if (path.startsWith("b/")) {
                    path = path.substring(2);
                }
                code.append("\n// FILE: ").append(path).append('\n');
            } else if (line.startsWith("@@")) {
                code.append("// HUNK: ").append(line).append('\n');
            } else if (line.startsWith("+") && !line.startsWith("+++")) {
                code.append(line.substring(1)).append('\n');
            }
        }
        return code.toString();
    }

    private static String languageOr(String language, String fallback) {
        return language == null || language.isBlank() ? fallback : language;
    }

    private static String inferLanguage(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "text";
    }
}