package edu.lab.review.agent;

import edu.lab.review.config.LabConfig;
import edu.lab.review.config.RocketMqFacade;
import edu.lab.review.model.Dimension;
import edu.lab.review.model.Finding;
import edu.lab.review.model.ReviewResult;
import edu.lab.review.model.ReviewTask;
import edu.lab.review.report.ReportRenderer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SummaryAgent extends BaseAgent {

    public SummaryAgent(LabConfig config, RocketMqFacade facade) {
        super(config, facade, Dimension.SUMMARY);
    }

    @Override
    protected ReviewResult review(ReviewTask task, long startedAt) throws InterruptedException {
        Thread.sleep(config.getAnalysisDelayMs());
        List<ReviewResult> dimensions = task.getResults() == null ? List.of() : task.getResults();
        List<Finding> merged = mergeFindings(dimensions);

        ReviewResult summary = new ReviewResult();
        summary.setStatus("OK");
        summary.setFindings(merged);
        summary.setStats(ReviewResult.statsOf(merged));
        summary.setSummary("SummaryAgent 汇总 " + dimensions.size() + " 个维度结果，去重后得到 "
            + merged.size() + " 项问题。");
        return summary;
    }

    @Override
    protected void customize(ReviewTask task, ReviewResult result) {
        String markdown = ReportRenderer.render(config, task, task.getResults(), result);
        result.setReportMarkdown(markdown);
        log.info("[summary] report rendered, session={} findings={} markdownChars={}",
            task.getSessionId(), result.getFindings().size(), markdown.length());
    }

    private List<Finding> mergeFindings(List<ReviewResult> results) {
        Map<String, Finding> unique = new LinkedHashMap<>();
        for (ReviewResult result : results) {
            if (result.getFindings() == null) {
                continue;
            }
            for (Finding finding : result.getFindings()) {
                String key = finding.getDimension() + ":" + finding.getRuleId() + ":" + finding.getLine();
                unique.putIfAbsent(key, finding);
            }
        }
        List<Finding> merged = new ArrayList<>(unique.values());
        merged.sort(Comparator.comparingInt((Finding f) -> -f.getSeverity().rank())
            .thenComparing(Finding::getDimension)
            .thenComparingInt(Finding::getLine));
        return merged;
    }
}