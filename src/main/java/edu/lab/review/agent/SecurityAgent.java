package edu.lab.review.agent;

import edu.lab.review.analysis.SecurityAnalyzer;
import edu.lab.review.config.LabConfig;
import edu.lab.review.config.RocketMqFacade;
import edu.lab.review.model.Dimension;

public class SecurityAgent extends AnalysisAgent {
    public SecurityAgent(LabConfig config, RocketMqFacade facade) {
        super(config, facade, Dimension.SECURITY, new SecurityAnalyzer());
    }
}