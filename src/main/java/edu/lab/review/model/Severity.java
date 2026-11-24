package edu.lab.review.model;

public enum Severity {
    CRITICAL(4, 25),
    HIGH(3, 10),
    MEDIUM(2, 3),
    LOW(1, 1),
    INFO(0, 0);

    private final int rank;
    private final int weight;

    Severity(int rank, int weight) {
        this.rank = rank;
        this.weight = weight;
    }

    public int rank() {
        return rank;
    }

    public int weight() {
        return weight;
    }
}