package edu.lab.review.analysis;

import edu.lab.review.model.Dimension;
import edu.lab.review.model.Finding;
import edu.lab.review.model.Severity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogicAnalyzer implements Analyzer {
    private static final Pattern NULL_ASSIGN = Pattern.compile(
        "\\b([A-Za-z_$][\\w$]*)\\s*=\\s*null\\s*;");
    private static final Pattern NON_NULL_ASSIGN = Pattern.compile(
        "\\b([A-Za-z_$][\\w$]*)\\s*=\\s*(?!null\\b)(?!\\s*null\\b)(?!=)([^;]+);");
    private static final Pattern GET_PARAM_CHAIN = Pattern.compile(
        "\\b(getParameter|getHeader|getAttribute)\\s*\\([^)]*\\)\\s*\\.\\s*[A-Za-z_$]");
    private static final Pattern STRING_EQ = Pattern.compile(
        "([A-Za-z_$][\\w$.]*|\"[^\"]*\")\\s*(==|!=)\\s*([A-Za-z_$][\\w$.]*|\"[^\"]*\")");
    private static final Pattern ASSIGN_IN_COND = Pattern.compile(
        "\\b(if|while)\\s*\\([^)]*(?<![=!<>])=(?!=)[^)]*\\)");
    private static final Pattern OFF_BY_ONE = Pattern.compile(
        "for\\s*\\([^;]*;[^;]*<=\\s*[\\w.]+\\.(?:length|size\\(\\))\\s*;");
    private static final Pattern EMPTY_CATCH = Pattern.compile(
        "catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}", Pattern.DOTALL);
    private static final Pattern SWALLOWED_CATCH = Pattern.compile(
        "catch\\s*\\(([^)]*)\\)\\s*\\{[^}]*printStackTrace\\s*\\(", Pattern.DOTALL);
    private static final Pattern RETURN_IN_FINALLY = Pattern.compile(
        "finally\\s*\\{[^}]*\\breturn\\b", Pattern.DOTALL);
    private static final Pattern NULL_EQUALS = Pattern.compile(
        "\\bnull\\s*\\.\\s*equals\\s*\\(");
    private static final Pattern RESOURCE_NEW = Pattern.compile(
        "\\bnew\\s+(FileInputStream|FileOutputStream|FileReader|FileWriter|BufferedReader|BufferedWriter|"
            + "Socket|ServerSocket|Connection|PreparedStatement|Statement|ResultSet)\\s*\\(");
    private static final Pattern CONNECTION_OPEN = Pattern.compile(
        "\\bDriverManager\\s*\\.\\s*getConnection\\s*\\(");

    @Override
    public Dimension dimension() {
        return Dimension.LOGIC;
    }

    @Override
    public List<Finding> analyze(String code, String language) {
        List<Finding> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> lines = RuleSupport.lines(code);

        Map<String, Integer> nullVars = new HashMap<>();
        Set<String> reportedNullDeref = new HashSet<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNo = i + 1;
            if (!RuleSupport.isCode(line)) {
                continue;
            }

            Matcher stringEq = STRING_EQ.matcher(line);
            while (stringEq.find()) {
                String left = stringEq.group(1);
                String right = stringEq.group(3);
                if (left.contains("\"") || right.contains("\"")) {
                    RuleSupport.add(findings, seen, new Finding("LOGIC-STRING-EQ", "LOGIC", Severity.HIGH,
                        "使用 == 比较字符串内容",
                        "`==` 比较的是引用地址而非字符串内容，可能产生错误判断。",
                        lineNo, RuleSupport.trimSnippet(line),
                        "使用 `\"literal\".equals(value)` 或 `Objects.equals(a, b)`。", null));
                    break;
                }
            }

            Matcher assignCond = ASSIGN_IN_COND.matcher(line);
            if (assignCond.find()) {
                RuleSupport.add(findings, seen, new Finding("LOGIC-ASSIGN-IN-COND", "LOGIC", Severity.HIGH,
                    "条件表达式中使用赋值",
                    "`if/while` 条件中出现单个 `=`，容易把比较误写成赋值。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "确认是否应为 `==`，并将赋值移出条件表达式。", null));
            }

            if (OFF_BY_ONE.matcher(line).find()) {
                RuleSupport.add(findings, seen, new Finding("LOGIC-OFF-BY-ONE", "LOGIC", Severity.HIGH,
                    "循环边界疑似越界",
                    "循环条件使用 `<= length/size()`，最后一次迭代可能访问越界下标。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "使用 `< length/size()`，或明确处理边界。", null));
            }

            Matcher nullEq = NULL_EQUALS.matcher(line);
            if (nullEq.find()) {
                RuleSupport.add(findings, seen, new Finding("LOGIC-NULL-EQUALS", "LOGIC", Severity.MEDIUM,
                    "在 null 上调用 equals",
                    "`null.equals(...)` 会直接抛出 NullPointerException。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "改为 `Objects.equals(left, right)` 或先用常量调用 equals。", null));
            }

            Matcher chain = GET_PARAM_CHAIN.matcher(line);
            if (chain.find()) {
                RuleSupport.add(findings, seen, new Finding("LOGIC-NPE-CHAIN", "LOGIC", Severity.HIGH,
                    "可能对可空返回值直接解引用",
                    "`" + chain.group(1) + "(...)` 可能返回 null，随后直接调用方法存在 NPE 风险。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "先判空或使用 `Optional`，再调用后续方法。", null));
            }

            Matcher nullAssign = NULL_ASSIGN.matcher(line);
            if (nullAssign.find()) {
                nullVars.put(nullAssign.group(1), lineNo);
            }
            Matcher nonNullAssign = NON_NULL_ASSIGN.matcher(line);
            if (nonNullAssign.find()) {
                nullVars.remove(nonNullAssign.group(1));
            }
            for (Map.Entry<String, Integer> entry : new ArrayList<>(nullVars.entrySet())) {
                String var = entry.getKey();
                if (reportedNullDeref.contains(var) || !line.contains(var + ".")) {
                    continue;
                }
                if (lineNo <= entry.getValue()) {
                    continue;
                }
                if (hasNullGuard(lines, i, var)) {
                    continue;
                }
                reportedNullDeref.add(var);
                RuleSupport.add(findings, seen, new Finding("LOGIC-NULL-DEREF", "LOGIC", Severity.HIGH,
                    "可空变量未判空即解引用",
                    "变量 `" + var + "` 在第 " + entry.getValue() + " 行被赋值为 null，此处直接解引用可能导致 NPE。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "在解引用前增加 `if (" + var + " != null)` 判断或使用 Optional。", null));
            }

            if (isResourceLeak(lines, i)) {
                RuleSupport.add(findings, seen, new Finding("LOGIC-RESOURCE-LEAK", "LOGIC", Severity.HIGH,
                    "资源未使用 try-with-resources 关闭",
                    "创建了需要显式关闭的资源，但未使用 try-with-resources 保证释放。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "使用 try-with-resources，或在 finally 中关闭资源。", null));
            }

            }

        Matcher returnFinally = RETURN_IN_FINALLY.matcher(code);
        while (returnFinally.find()) {
            int returnIndex = code.indexOf("return", returnFinally.start());
            int returnLine = lineNumberOf(code,
                returnIndex >= 0 && returnIndex < returnFinally.end() ? returnIndex : returnFinally.start());
            RuleSupport.add(findings, seen, new Finding("LOGIC-RETURN-IN-FINALLY", "LOGIC", Severity.HIGH,
                "finally 块中包含 return",
                "finally 中的 return 会覆盖 try/catch 的返回值并吞掉异常。",
                returnLine, lineAt(lines, returnLine),
                "移除 finally 中的 return，让异常与返回值正常传播。", null));
        }

        addMultilineRule(code, lines, findings, seen, EMPTY_CATCH, "LOGIC-EMPTY-CATCH", Severity.MEDIUM,
            "空的 catch 块", "异常被静默吞掉，故障将难以定位。",
            "至少记录日志或转换为业务异常后抛出。");
        addMultilineRule(code, lines, findings, seen, SWALLOWED_CATCH, "LOGIC-SWALLOWED-EXCEPTION", Severity.MEDIUM,
            "异常仅 printStackTrace", "仅打印堆栈会丢失上下文且不受日志系统管理。",
            "使用日志框架记录上下文，并根据业务决定是否继续抛出。");

        return findings;
    }

    private static boolean hasNullGuard(List<String> lines, int index, String var) {
        int from = Math.max(0, index - 4);
        for (int i = from; i <= index; i++) {
            String line = lines.get(i);
            if (line.contains(var + " != null") || line.contains(var + " == null")
                || line.contains("Objects.requireNonNull(" + var)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isResourceLeak(List<String> lines, int index) {
        String line = lines.get(index);
        boolean resource = RESOURCE_NEW.matcher(line).find() || CONNECTION_OPEN.matcher(line).find();
        if (!resource || line.strip().startsWith("//")) {
            return false;
        }
        if (line.contains("try (") || line.contains("try(")) {
            return false;
        }
        int from = Math.max(0, index - 2);
        for (int i = from; i <= index; i++) {
            if (lines.get(i).contains("try (")) {
                return false;
            }
        }
        return true;
    }

    private static void addMultilineRule(String code, List<String> lines, List<Finding> findings,
                                         Set<String> seen, Pattern pattern, String ruleId, Severity severity,
                                         String title, String description, String suggestion) {
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            int lineNo = lineNumberOf(code, matcher.start());
            RuleSupport.add(findings, seen, new Finding(ruleId, "LOGIC", severity, title, description,
                lineNo, lineAt(lines, lineNo), suggestion, null));
        }
    }

    private static int lineNumberOf(String code, int index) {
        int line = 1;
        for (int i = 0; i < index && i < code.length(); i++) {
            if (code.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String lineAt(List<String> lines, int lineNo) {
        return lineNo >= 1 && lineNo <= lines.size() ? RuleSupport.trimSnippet(lines.get(lineNo - 1)) : "";
    }
}