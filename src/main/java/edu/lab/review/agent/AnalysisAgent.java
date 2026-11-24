package edu.lab.review.agent;

import edu.lab.review.analysis.Analyzer;
import edu.lab.review.config.LabConfig;
import edu.lab.review.config.RocketMqFacade;
import edu.lab.review.model.Dimension;
import edu.lab.review.model.Finding;
import edu.lab.review.model.ReviewResult;
import edu.lab.review.model.ReviewTask;

import java.util.List;

public class AnalysisAgent extends BaseAgent {
    private final Analyzer analyzer;

    public AnalysisAgent(LabConfig config, RocketMqFacade facade, Dimension dimension, Analyzer analyzer) {
        super(config, facade, dimension);
        this.analyzer = analyzer;
    }

    @Override
    protected ReviewResult review(ReviewTask task, long startedAt) throws InterruptedException {
        // Simulates the latency of an agent reasoning step (e.g. an LLM call) so the
        // concurrent execution window is visible in timings without an external model.
        Thread.sleep(config.getAnalysisDelayMs());
        List<Finding> findings = analyzer.analyze(task.getCode(), task.getLanguage());
        ReviewResult result = new ReviewResult();
        result.setStatus("OK");
        result.setFindings(findings);
        result.setSummary(summaryOf(findings));
        return result;
    }

    private String summaryOf(List<Finding> findings) {
        long critical = findings.stream().filter(f -> f.getSeverity().name().equals("CRITICAL")).count();
        long high = findings.stream().filter(f -> f.getSeverity().name().equals("HIGH")).count();
        long medium = findings.stream().filter(f -> f.getSeverity().name().equals("MEDIUM")).count();
        long low = findings.stream().filter(f -> f.getSeverity().name().equals("LOW")).count();
        return dimension.name() + " 维度共发现 " + findings.size() + " 项问题（Critical " + critical
            + " / High " + high + " / Medium " + medium + " / Low " + low + "）。";
    }
}