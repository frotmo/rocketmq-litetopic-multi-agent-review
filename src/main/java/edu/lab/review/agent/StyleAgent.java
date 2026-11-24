package edu.lab.review.agent;

import edu.lab.review.analysis.StyleAnalyzer;
import edu.lab.review.config.LabConfig;
import edu.lab.review.config.RocketMqFacade;
import edu.lab.review.model.Dimension;

public class StyleAgent extends AnalysisAgent {
    public StyleAgent(LabConfig config, RocketMqFacade facade) {
        super(config, facade, Dimension.STYLE, new StyleAnalyzer());
    }
}