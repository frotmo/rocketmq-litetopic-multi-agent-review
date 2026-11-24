package edu.lab.review.analysis;

import edu.lab.review.model.Dimension;
import edu.lab.review.model.Finding;
import edu.lab.review.model.Severity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StyleAnalyzer implements Analyzer {
    private static final Pattern CLASS_DECL = Pattern.compile(
        "\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern METHOD_DECL = Pattern.compile(
        "^\\s*(?:public|private|protected)\\s+(?:(?:static|final|synchronized)\\s+)*"
            + "[\\w$<>\\[\\],.\\s?]+\\s+([A-Za-z_$][\\w$]*)\\s*\\([^;]*\\)\\s*(?:throws\\s+[^{]+)?\\{?\\s*$");
    private static final Pattern VAR_DECL = Pattern.compile(
        "^\\s*(?:final\\s+)?(?:[A-Za-z_$][\\w$]*(?:<[^>]*>)?(?:\\[\\])?)\\s+"
            + "([a-z][A-Za-z0-9]*_[A-Za-z0-9_]*)\\s*(?:=|;|,|\\))");
    private static final Pattern CONST_DECL = Pattern.compile(
        "\\bstatic\\s+final\\s+[\\w$<>\\[\\]]+\\s+([a-z][\\w$]*)\\s*=");
    private static final Pattern IMPORT_LINE = Pattern.compile("^\\s*import\\s+(?!static\\s)([\\w.]+);");
    private static final Pattern IF_HEAD = Pattern.compile(
        "^\\s*(?:}\\s*else\\s+)?(if|for|while)\\s*\\(.*\\)\\s*$");
    private static final Pattern INLINE_IF = Pattern.compile(
        "^\\s*(?:}\\s*else\\s+)?(if|for|while)\\s*\\(.*\\)\\s+[^{\\s].*;\\s*$");
    private static final Pattern TODO = Pattern.compile("(?i)\\b(TODO|FIXME)\\b");
    private static final Pattern SYSTEM_OUT = Pattern.compile("\\bSystem\\.(out|err)\\.(print|println|printf)\\s*\\(");

    @Override
    public Dimension dimension() {
        return Dimension.STYLE;
    }

    @Override
    public List<Finding> analyze(String code, String language) {
        List<Finding> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> lines = RuleSupport.lines(code);

        int longLineCount = 0;
        boolean tabReported = false;
        boolean systemOutReported = false;
        int todoCount = 0;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNo = i + 1;
            if (!RuleSupport.isCode(line)) {
                continue;
            }

            Matcher classMatcher = CLASS_DECL.matcher(line);
            if (classMatcher.find()) {
                String name = classMatcher.group(1);
                if (!name.matches("[A-Z][A-Za-z0-9]*")) {
                    RuleSupport.add(findings, seen, new Finding("STYLE-NAMING-CLASS", "STYLE", Severity.LOW,
                        "类型命名不符合 PascalCase",
                        "类型名 `" + name + "` 未使用大驼峰命名，降低可读性。",
                        lineNo, RuleSupport.trimSnippet(line),
                        "改为 PascalCase，例如 `OrderService`。", null));
                }
            }

            Matcher methodMatcher = METHOD_DECL.matcher(line);
            if (methodMatcher.find()) {
                String name = methodMatcher.group(1);
                if (!isControlKeyword(name) && !name.matches("[a-z][a-zA-Z0-9]*")) {
                    RuleSupport.add(findings, seen, new Finding("STYLE-NAMING-METHOD", "STYLE", Severity.LOW,
                        "方法命名不符合 camelCase",
                        "方法名 `" + name + "` 未使用小驼峰命名。",
                        lineNo, RuleSupport.trimSnippet(line),
                        "改为 camelCase，例如 `calculateTotal`。", null));
                }
                int methodLength = methodLength(lines, i);
                if (methodLength > 30) {
                    RuleSupport.add(findings, seen, new Finding("STYLE-LONG-METHOD", "STYLE", Severity.MEDIUM,
                        "方法过长",
                        "方法体约 " + methodLength + " 行，超过 30 行阈值，职责可能过多。",
                        lineNo, RuleSupport.trimSnippet(line),
                        "按单一职责拆分方法，提取私有辅助方法。", null));
                }
            }

            Matcher varMatcher = VAR_DECL.matcher(line);
            if (varMatcher.find()) {
                String name = varMatcher.group(1);
                RuleSupport.add(findings, seen, new Finding("STYLE-NAMING-VAR", "STYLE", Severity.LOW,
                    "变量命名包含下划线",
                    "变量/参数 `" + name + "` 使用了下划线命名，不符合 Java 小驼峰规范。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "改为 camelCase，例如 `userName`。", null));
            }

            Matcher constMatcher = CONST_DECL.matcher(line);
            if (constMatcher.find()) {
                RuleSupport.add(findings, seen, new Finding("STYLE-NAMING-CONST", "STYLE", Severity.LOW,
                    "常量命名未使用全大写",
                    "static final 常量 `" + constMatcher.group(1) + "` 建议使用 UPPER_SNAKE_CASE。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "改为全大写下划线，例如 `MAX_RETRY`。", null));
            }

            if (line.length() > 120 && longLineCount < 3) {
                longLineCount++;
                RuleSupport.add(findings, seen, new Finding("STYLE-LONG-LINE", "STYLE", Severity.LOW,
                    "代码行超过 120 字符",
                    "当前行长度为 " + line.length() + " 字符，影响可读性与代码评审。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "拆分表达式或使用局部变量，保持每行不超过 120 字符。", null));
            }

            if (line.contains("\t") && !tabReported) {
                tabReported = true;
                RuleSupport.add(findings, seen, new Finding("STYLE-TABS", "STYLE", Severity.INFO,
                    "使用 Tab 缩进",
                    "检测到 Tab 缩进，团队规范通常要求 4 个空格。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "统一替换为 4 个空格缩进。", null));
            }

            Matcher todoMatcher = TODO.matcher(line);
            if (todoMatcher.find() && todoCount < 3) {
                todoCount++;
                RuleSupport.add(findings, seen, new Finding("STYLE-TODO", "STYLE", Severity.LOW,
                    "遗留 TODO/FIXME 标记",
                    "代码中保留了 `" + todoMatcher.group(1) + "`，可能代表未完成实现。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "在合并前完成或登记为可跟踪的 issue。", null));
            }

            if (SYSTEM_OUT.matcher(line).find() && !systemOutReported) {
                systemOutReported = true;
                RuleSupport.add(findings, seen, new Finding("STYLE-SYSTEM-OUT", "STYLE", Severity.LOW,
                    "直接使用 System.out/err",
                    "生产代码应通过日志框架输出，避免不可控的 stdout 污染。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "改用 SLF4J/Logback 等日志组件。", null));
            }

            if (INLINE_IF.matcher(line).find()) {
                RuleSupport.add(findings, seen, new Finding("STYLE-MISSING-BRACES", "STYLE", Severity.LOW,
                    "条件/循环语句缺少大括号",
                    "单行 if/for/while 语句省略大括号，后续扩展时容易引入逻辑错误。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "始终为条件与循环语句添加大括号。", null));
            }

            Matcher headMatcher = IF_HEAD.matcher(line);
            if (headMatcher.find() && i + 1 < lines.size()) {
                String next = lines.get(i + 1).strip();
                if (!next.isEmpty() && !next.startsWith("{") && !next.startsWith("//")) {
                    RuleSupport.add(findings, seen, new Finding("STYLE-MISSING-BRACES", "STYLE", Severity.LOW,
                        "条件/循环语句缺少大括号",
                        "`" + headMatcher.group(1) + "` 语句的下一行不是 `{`，建议补充大括号。",
                        lineNo, RuleSupport.trimSnippet(line),
                        "为语句体添加 `{ ... }`。", null));
                }
            }
        }

        checkUnusedImports(lines, findings, seen);
        return findings;
    }

    private static boolean isControlKeyword(String name) {
        return Set.of("if", "for", "while", "switch", "catch", "return", "new", "throw", "synchronized")
            .contains(name);
    }

    private static int methodLength(List<String> lines, int startIndex) {
        int depth = 0;
        boolean opened = false;
        for (int i = startIndex; i < lines.size(); i++) {
            String line = stripStringsAndComments(lines.get(i));
            for (char c : line.toCharArray()) {
                if (c == '{') {
                    depth++;
                    opened = true;
                } else if (c == '}') {
                    depth--;
                }
            }
            if (opened && depth <= 0) {
                return i - startIndex + 1;
            }
        }
        return 0;
    }

    private static String stripStringsAndComments(String line) {
        String trimmed = line.strip();
        if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
            return "";
        }
        return line.replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"")
            .replaceAll("//.*$", "");
    }

    private static void checkUnusedImports(List<String> lines, List<Finding> findings, Set<String> seen) {
        StringBuilder body = new StringBuilder();
        List<MapEntry> imports = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher matcher = IMPORT_LINE.matcher(line);
            if (matcher.find()) {
                String fqcn = matcher.group(1);
                if (!fqcn.endsWith("*")) {
                    imports.add(new MapEntry(fqcn, i + 1));
                }
            } else {
                body.append(line).append('\n');
            }
        }
        String code = body.toString();
        int reported = 0;
        for (MapEntry entry : imports) {
            if (reported >= 5) {
                break;
            }
            String simpleName = entry.fqcn.substring(entry.fqcn.lastIndexOf('.') + 1);
            if (!Pattern.compile("\\b" + Pattern.quote(simpleName) + "\\b").matcher(code).find()) {
                reported++;
                RuleSupport.add(findings, seen, new Finding("STYLE-UNUSED-IMPORT", "STYLE", Severity.LOW,
                    "未使用的 import",
                    "`" + entry.fqcn + "` 未被代码引用。",
                    entry.line, "import " + entry.fqcn + ";",
                    "删除未使用的 import，保持依赖清晰。", null));
            }
        }
    }

    private record MapEntry(String fqcn, int line) {
    }
}