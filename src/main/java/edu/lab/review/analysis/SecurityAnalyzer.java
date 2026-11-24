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

public class SecurityAnalyzer implements Analyzer {
    private static final Pattern SQL_KEYWORD = Pattern.compile("(?i)\\b(select|insert|update|delete)\\b");
    private static final Pattern HARD_CODED_SECRET = Pattern.compile(
        "(?i)([A-Za-z0-9_]*(?:password|passwd|pwd|secret|token|api[_-]?key))\\s*=\\s*\"([^\"]{3,})\"");
    private static final Pattern WEAK_HASH = Pattern.compile(
        "(?i)MessageDigest\\s*\\.\\s*getInstance\\s*\\(\\s*\"(MD5|SHA-1)\"");
    private static final Pattern RANDOM = Pattern.compile("\\bnew\\s+Random\\s*\\(");
    private static final Pattern RANDOM_CONTEXT = Pattern.compile(
        "(?i)token|password|secret|session|otp|salt|nonce|verify");
    private static final Pattern DESERIALIZE = Pattern.compile("\\bnew\\s+ObjectInputStream\\s*\\(");
    private static final Pattern XML_FACTORY = Pattern.compile(
        "\\bDocumentBuilderFactory\\s*\\.\\s*newInstance\\s*\\(");
    private static final Pattern INSECURE_TLS = Pattern.compile(
        "(?i)TrustAllCerts|ALLOW_ALL_HOSTNAME_VERIFIER|NoopHostnameVerifier");
    private static final Pattern LDAP_SEARCH = Pattern.compile(
        "\\b(search|createSubcontext|modifyAttributes)\\s*\\(");
    private static final Pattern URL_NEW = Pattern.compile("\\bnew\\s+(?:[\\w.]+\\.)?URL\\s*\\(");
    private static final Pattern REDIRECT = Pattern.compile("\\b(sendRedirect|redirect)\\s*\\(");
    private static final Pattern USER_INPUT = Pattern.compile(
        "(?i)getParameter|getHeader|getQueryString|request\\.|userInput|filename|path\\s*\\+|\\+\\s*path");

    @Override
    public Dimension dimension() {
        return Dimension.SECURITY;
    }

    @Override
    public List<Finding> analyze(String code, String language) {
        List<Finding> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> lines = RuleSupport.lines(code);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNo = i + 1;
            if (!RuleSupport.isCode(line)) {
                continue;
            }

            if (SQL_KEYWORD.matcher(line).find() && line.contains("+") && line.contains("\"")) {
                RuleSupport.add(findings, seen, new Finding("SEC-SQL-INJECTION", "SECURITY", Severity.CRITICAL,
                    "SQL 拼接可能导致注入",
                    "SQL 语句通过字符串拼接用户输入，攻击者可改写查询条件。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "使用 PreparedStatement 参数绑定，禁止拼接 SQL。", "CWE-89"));
            }
            if (line.contains("executeQuery(") || line.contains("executeUpdate(") || line.contains("execute(")) {
                if (line.contains("+") && SQL_KEYWORD.matcher(line).find()) {
                    RuleSupport.add(findings, seen, new Finding("SEC-SQL-INJECTION-EXEC", "SECURITY",
                        Severity.CRITICAL, "动态 SQL 直接执行",
                        "执行了由字符串拼接构造的 SQL 语句。",
                        lineNo, RuleSupport.trimSnippet(line),
                        "改为参数化查询，并对输入做类型/长度校验。", "CWE-89"));
                }
            }

            if ((line.contains("getWriter().write(") || line.contains("getWriter().print(")
                || line.contains("out.println(")) && (line.contains("+") || line.contains("getParameter"))) {
                RuleSupport.add(findings, seen, new Finding("SEC-XSS", "SECURITY", Severity.HIGH,
                    "未编码输出可能导致 XSS",
                    "将请求参数直接写入 HTTP 响应，可能造成反射型/存储型 XSS。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "使用 HTML 编码（如 OWASP Java Encoder）后再输出。", "CWE-79"));
            }

            if ((line.contains("Runtime.getRuntime().exec(") || line.contains("new ProcessBuilder("))
                && line.contains("+")) {
                RuleSupport.add(findings, seen, new Finding("SEC-COMMAND-INJECTION", "SECURITY", Severity.CRITICAL,
                    "命令执行参数拼接",
                    "系统命令由字符串拼接构造，攻击者可注入额外命令。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "使用参数化 ProcessBuilder 列表，避免 shell 解释用户输入。", "CWE-78"));
            }

            Matcher secret = HARD_CODED_SECRET.matcher(line);
            if (secret.find() && !secret.group(2).contains("${")) {
                RuleSupport.add(findings, seen, new Finding("SEC-HARDCODED-SECRET", "SECURITY", Severity.HIGH,
                    "硬编码凭据/密钥",
                    "检测到硬编码的 `" + secret.group(1) + "` 字段，凭据可能随源码泄露。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "改用环境变量、密钥管理服务（KMS）或加密配置中心。", "CWE-798"));
            }

            if (WEAK_HASH.matcher(line).find()) {
                RuleSupport.add(findings, seen, new Finding("SEC-WEAK-HASH", "SECURITY", Severity.MEDIUM,
                    "使用弱哈希算法",
                    "MD5/SHA-1 已不适用于口令或完整性校验。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "使用 SHA-256 以上算法；口令使用 bcrypt/argon2。", "CWE-328"));
            }

            if (RANDOM.matcher(line).find() && hasRandomContext(lines, i)) {
                RuleSupport.add(findings, seen, new Finding("SEC-INSECURE-RANDOM", "SECURITY", Severity.MEDIUM,
                    "安全场景使用非加密随机数",
                    "`java.util.Random` 可预测，不适合生成令牌、验证码等安全值。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "使用 `SecureRandom` 生成安全敏感随机值。", "CWE-330"));
            }

            if ((line.contains("new File(") || line.contains("Paths.get(") || line.contains("Path.of("))
                && (line.contains("+") || USER_INPUT.matcher(line).find())) {
                RuleSupport.add(findings, seen, new Finding("SEC-PATH-TRAVERSAL", "SECURITY", Severity.HIGH,
                    "文件路径可能被遍历",
                    "文件路径包含外部输入，未做规范化与目录边界校验时可能读写任意文件。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "规范化路径并校验 `startsWith(baseDir)`，拒绝 `..` 片段。", "CWE-22"));
            }

            if (DESERIALIZE.matcher(line).find()) {
                RuleSupport.add(findings, seen, new Finding("SEC-UNSAFE-DESERIALIZATION", "SECURITY", Severity.CRITICAL,
                    "不安全的 Java 反序列化",
                    "ObjectInputStream 反序列化不可信数据可导致远程代码执行。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "避免反序列化不可信数据；使用白名单 ObjectInputFilter 或 JSON。", "CWE-502"));
            }

            if (INSECURE_TLS.matcher(line).find()) {
                RuleSupport.add(findings, seen, new Finding("SEC-INSECURE-TLS", "SECURITY", Severity.HIGH,
                    "TLS 证书/主机名校验被关闭",
                    "信任所有证书或跳过主机名校验会破坏 HTTPS 的中间人防护。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "使用受信任的 CA 与严格的主机名校验。", "CWE-295"));
            }

            if (LDAP_SEARCH.matcher(line).find() && line.contains("+") && line.contains("\"")) {
                RuleSupport.add(findings, seen, new Finding("SEC-LDAP-INJECTION", "SECURITY", Severity.HIGH,
                    "LDAP 过滤器拼接",
                    "LDAP 查询过滤器由字符串拼接构造，可能被注入。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "对 LDAP 特殊字符做转义，使用参数化查询 API。", "CWE-90"));
            }

            if (URL_NEW.matcher(line).find() && line.contains("+") && USER_INPUT.matcher(line).find()) {
                RuleSupport.add(findings, seen, new Finding("SEC-SSRF", "SECURITY", Severity.HIGH,
                    "外部输入构造 URL（SSRF 风险）",
                    "攻击者可能控制服务端发起的请求目标，访问内网资源。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "使用目标域名/协议白名单，禁止访问内网地址。", "CWE-918"));
            }

            if (REDIRECT.matcher(line).find() && (line.contains("getParameter") || line.contains("+"))) {
                RuleSupport.add(findings, seen, new Finding("SEC-OPEN-REDIRECT", "SECURITY", Severity.MEDIUM,
                    "开放重定向",
                    "重定向目标来自外部输入，可能被用于钓鱼跳转。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "仅允许相对路径或可信域白名单。", "CWE-601"));
            }

            if (XML_FACTORY.matcher(line).find() && !hasXxeProtection(lines, i)) {
                RuleSupport.add(findings, seen, new Finding("SEC-XXE", "SECURITY", Severity.HIGH,
                    "XML 解析器未禁用外部实体",
                    "DocumentBuilderFactory 默认可能解析外部实体，存在 XXE 风险。",
                    lineNo, RuleSupport.trimSnippet(line),
                    "设置 disallow-doctype-decl=true 并禁用外部 DTD/实体。", "CWE-611"));
            }
        }
        return findings;
    }

    private static boolean hasRandomContext(List<String> lines, int index) {
        int to = Math.min(lines.size() - 1, index + 4);
        for (int i = Math.max(0, index - 1); i <= to; i++) {
            if (RANDOM_CONTEXT.matcher(lines.get(i)).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasXxeProtection(List<String> lines, int index) {
        int to = Math.min(lines.size() - 1, index + 12);
        for (int i = index; i <= to; i++) {
            String line = lines.get(i);
            if (line.contains("disallow-doctype-decl") || line.contains("FEATURE_SECURE_PROCESSING")
                || line.contains("setExpandEntityReferences(false)")) {
                return true;
            }
        }
        return false;
    }
}