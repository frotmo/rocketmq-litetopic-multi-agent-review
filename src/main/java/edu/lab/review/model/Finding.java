package edu.lab.review.model;

public class Finding {
    private String ruleId;
    private String dimension;
    private Severity severity;
    private String title;
    private String description;
    private int line;
    private String lineText;
    private String suggestion;
    private String cwe;

    public Finding() {
    }

    public Finding(String ruleId, String dimension, Severity severity, String title,
                   String description, int line, String lineText, String suggestion, String cwe) {
        this.ruleId = ruleId;
        this.dimension = dimension;
        this.severity = severity;
        this.title = title;
        this.description = description;
        this.line = line;
        this.lineText = truncate(lineText);
        this.suggestion = suggestion;
        this.cwe = cwe;
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        return trimmed.length() <= 160 ? trimmed : trimmed.substring(0, 157) + "...";
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public String getLineText() {
        return lineText;
    }

    public void setLineText(String lineText) {
        this.lineText = lineText;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getCwe() {
        return cwe;
    }

    public void setCwe(String cwe) {
        this.cwe = cwe;
    }
}