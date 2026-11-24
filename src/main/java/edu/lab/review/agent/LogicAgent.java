package edu.lab.review.agent;

import edu.lab.review.analysis.LogicAnalyzer;
import edu.lab.review.config.LabConfig;
import edu.lab.review.config.RocketMqFacade;
import edu.lab.review.model.Dimension;

public class LogicAgent extends AnalysisAgent {
    public LogicAgent(LabConfig config, RocketMqFacade facade) {
        super(config, facade, Dimension.LOGIC, new LogicAnalyzer());
    }
}