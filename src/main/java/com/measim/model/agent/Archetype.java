package com.measim.model.agent;

public enum Archetype {
    OPTIMIZER(0.2, 0.9, 0.1, 0.4, 0.8, "Low altruism, high ambition. Seeks maximum accumulation and system gaming."),
    ENTREPRENEUR(0.8, 0.7, 0.3, 0.8, 0.6, "High risk tolerance, high creativity. Creates businesses and drives innovation."),
    COOPERATOR(0.3, 0.5, 0.8, 0.4, 0.7, "High altruism, moderate ambition. Contributes to commons and community."),
    FREE_RIDER(0.2, 0.2, 0.3, 0.1, 0.3, "Low ambition, low compliance. Tests UBI adequacy with minimal participation."),
    REGULATOR(0.1, 0.5, 0.5, 0.3, 0.95, "High compliance. Participates in governance, proposes formula changes."),
    INNOVATOR(0.7, 0.6, 0.4, 0.95, 0.6, "Extreme creativity. Invests heavily in research and tech tree expansion."),
    ACCUMULATOR(0.5, 0.95, 0.1, 0.3, 0.7, "Maximum ambition, moderate risk. Tests resource concentration limits."),
    EXPLOITER(0.6, 0.8, 0.05, 0.5, 0.15, "Low compliance, high ambition. Seeks scoring loopholes and measurement gaps."),
    ARTISAN(0.3, 0.5, 0.4, 0.5, 0.7, "Moderate ambition, quality-focused. Non-maximizing economic behavior."),
    POLITICIAN(0.4, 0.6, 0.5, 0.3, 0.5, "High social engagement. Builds coalitions and attempts governance capture."),
    PHILANTHROPIST(0.3, 0.2, 0.9, 0.3, 0.8, "High altruism, low personal ambition. Redistributes wealth, funds public goods."),
    AUTOMATOR(0.6, 0.8, 0.2, 0.7, 0.6, "High ambition, tech-focused. Drives robot adoption and labor displacement."),

    // Additional archetypes for full economic behavior spectrum
    WORKER(0.2, 0.4, 0.5, 0.2, 0.8, "Seeks stable work relations and fair wages. Tests labor market and employment dynamics."),
    SPECULATOR(0.9, 0.8, 0.05, 0.4, 0.4, "Extreme risk tolerance. Trades assets, buys/sells property claims, exploits price differences."),
    HOMESTEADER(0.2, 0.4, 0.3, 0.4, 0.6, "Self-sufficient. Extracts, produces, and consumes locally. Minimal market participation."),
    PROVIDER(0.3, 0.5, 0.6, 0.5, 0.7, "Creates and operates services for other agents. Tests service economy dynamics."),
    LANDLORD(0.3, 0.7, 0.1, 0.2, 0.7, "Acquires property claims and rents them. Tests property market and rent-seeking under MEAS."),
    ORGANIZER(0.3, 0.5, 0.7, 0.5, 0.6, "Builds lasting alliances and multi-agent agreements. Tests collective action and coordination.");

    private final double riskTolerance;
    private final double ambition;
    private final double altruism;
    private final double creativity;
    private final double complianceDisposition;
    private final String description;

    Archetype(double riskTolerance, double ambition, double altruism,
              double creativity, double complianceDisposition, String description) {
        this.riskTolerance = riskTolerance;
        this.ambition = ambition;
        this.altruism = altruism;
        this.creativity = creativity;
        this.complianceDisposition = complianceDisposition;
        this.description = description;
    }

    public double riskTolerance() { return riskTolerance; }
    public double ambition() { return ambition; }
    public double altruism() { return altruism; }
    public double creativity() { return creativity; }
    public double complianceDisposition() { return complianceDisposition; }
    public String description() { return description; }
}
