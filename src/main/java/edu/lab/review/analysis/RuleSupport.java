package edu.lab.review.analysis;

import edu.lab.review.model.Finding;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RuleSupport {
    private RuleSupport() {
    }

    static List<String> lines(String code) {
        return Arrays.asList(code.split("\\R", -1));
    }

    static boolean isComment(String line) {
        String trimmed = line.strip();
        return trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")
            || trimmed.startsWith("*/");
    }

    static boolean isCode(String line) {
        return !line.isBlank() && !isComment(line);
    }

    static void add(List<Finding> findings, Set<String> seen, Finding finding) {
        String key = finding.getRuleId() + ":" + finding.getLine() + ":" + finding.getTitle();
        if (seen.add(key)) {
            findings.add(finding);
        }
    }

    static Set<String> seen() {
        return new HashSet<>();
    }

    static String trimSnippet(String line) {
        String trimmed = line.strip();
        return trimmed.length() <= 160 ? trimmed : trimmed.substring(0, 157) + "...";
    }
}