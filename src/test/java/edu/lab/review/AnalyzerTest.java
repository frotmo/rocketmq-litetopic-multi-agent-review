package edu.lab.review;

import edu.lab.review.analysis.LogicAnalyzer;
import edu.lab.review.analysis.SecurityAnalyzer;
import edu.lab.review.analysis.StyleAnalyzer;
import edu.lab.review.model.Finding;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyzerTest {
    private static String sample() throws Exception {
        return Files.readString(Path.of("samples", "InsecureOrderService.java"));
    }

    private static boolean has(List<Finding> findings, String ruleId) {
        return findings.stream().anyMatch(f -> ruleId.equals(f.getRuleId()));
    }

    @Test
    void styleAnalyzerFindsStyleIssues() throws Exception {
        List<Finding> findings = new StyleAnalyzer().analyze(sample(), "java");
        assertTrue(has(findings, "STYLE-NAMING-METHOD"));
        assertTrue(has(findings, "STYLE-NAMING-VAR"));
        assertTrue(has(findings, "STYLE-UNUSED-IMPORT"));
        assertTrue(has(findings, "STYLE-MISSING-BRACES"));
        assertTrue(has(findings, "STYLE-LONG-METHOD"));
    }

    @Test
    void logicAnalyzerFindsLogicDefects() throws Exception {
        List<Finding> findings = new LogicAnalyzer().analyze(sample(), "java");
        assertTrue(has(findings, "LOGIC-STRING-EQ"));
        assertTrue(has(findings, "LOGIC-ASSIGN-IN-COND"));
        assertTrue(has(findings, "LOGIC-OFF-BY-ONE"));
        assertTrue(has(findings, "LOGIC-NULL-DEREF"));
        assertTrue(has(findings, "LOGIC-NPE-CHAIN"));
        assertTrue(has(findings, "LOGIC-RETURN-IN-FINALLY"));
        assertTrue(has(findings, "LOGIC-RESOURCE-LEAK"));
    }

    @Test
    void securityAnalyzerFindsCommonVulnerabilities() throws Exception {
        List<Finding> findings = new SecurityAnalyzer().analyze(sample(), "java");
        assertTrue(has(findings, "SEC-SQL-INJECTION"));
        assertTrue(has(findings, "SEC-XSS"));
        assertTrue(has(findings, "SEC-COMMAND-INJECTION"));
        assertTrue(has(findings, "SEC-HARDCODED-SECRET"));
        assertTrue(has(findings, "SEC-SSRF"));
        assertTrue(has(findings, "SEC-UNSAFE-DESERIALIZATION"));
        assertTrue(has(findings, "SEC-PATH-TRAVERSAL"));
    }
}