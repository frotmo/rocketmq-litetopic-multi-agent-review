package edu.lab.review.analysis;

import edu.lab.review.model.Dimension;
import edu.lab.review.model.Finding;

import java.util.List;

public interface Analyzer {
    Dimension dimension();

    List<Finding> analyze(String code, String language);
}