package com.measim.model.config;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class SimulationConfig {

    private long seed = 42;
    private int worldWidth = 50;
    private int worldHeight = 50;
    private double resourceDensity = 0.3;
    private int noiseOctaves = 6;
    private double noisePersistence = 0.5;
    private int agentCount = 500;
    private Map<String, Double> archetypeDistribution = new LinkedHashMap<>();
    private int perceptionRadius = 7;
    private int memoryCapacity = 200;
    private double startingCredits = 1000.0;
    private double robotInitialCost = 10000;
    private double robotCostDecayRate = 0.05;
    private double robotInitialEfficiency = 1.2;
    private double robotEfficiencyGrowthRate = 0.03;
    private boolean measEnabled = true;
    private String formulaVersion = "v0.1.0";
    private boolean ubiEnabled = true;
    private double ubiAdequacyTarget = 0.85;
    private double baseTransactionTax = 0.005;
    private String llmProvider = "anthropic";
    private String apiKey = "";  // Set via config YAML or ANTHROPIC_API_KEY env var
    private String apiBaseUrl = "https://api.anthropic.com";
    private String agentModel = "claude-sonnet-4-6";
    private String complexModel = "claude-sonnet-4-6";
    private String gameMasterModel = "claude-sonnet-4-6";
    private int maxAgentCallsPerTick = 50;
    private int maxGameMasterCallsPerTick = 20;
    private double totalBudgetUsd = 100.0;
    private boolean cacheEnabled = true;
    private int cacheTtlTicks = 5;
    private int ticksPerYear = 12;
    private int totalYears = 50;
    private int snapshotInterval = 12;
    private int metricsInterval = 1;
    private int ubiDistributionInterval = 1;
    private double resourceDiscoveryProbability = 0.02;
    private double techBreakthroughProbability = 0.01;
    private double environmentalCrisisThreshold = 0.3;
    private List<GovernmentDef> governments = new ArrayList<>();

    public SimulationConfig() {
        archetypeDistribution.put("OPTIMIZER", 0.10);
        archetypeDistribution.put("ENTREPRENEUR", 0.06);
        archetypeDistribution.put("INNOVATOR", 0.04);
        archetypeDistribution.put("ACCUMULATOR", 0.04);
        archetypeDistribution.put("AUTOMATOR", 0.04);
        archetypeDistribution.put("OPTIMIZER", 0.05);
        archetypeDistribution.put("SPECULATOR", 0.03);
        archetypeDistribution.put("WORKER", 0.15);
        archetypeDistribution.put("PROVIDER", 0.06);
        archetypeDistribution.put("ARTISAN", 0.06);
        archetypeDistribution.put("HOMESTEADER", 0.05);
        archetypeDistribution.put("COOPERATOR", 0.08);
        archetypeDistribution.put("ORGANIZER", 0.04);
        archetypeDistribution.put("POLITICIAN", 0.03);
        archetypeDistribution.put("REGULATOR", 0.03);
        archetypeDistribution.put("PHILANTHROPIST", 0.03);
        archetypeDistribution.put("LANDLORD", 0.04);
        archetypeDistribution.put("EXPLOITER", 0.04);
        archetypeDistribution.put("FREE_RIDER", 0.07);
    }

    @SuppressWarnings("unchecked")
    public static SimulationConfig load(Path path) {
        SimulationConfig config = new SimulationConfig();
        if (!Files.exists(path)) {
            System.out.println("Config file not found at " + path + ", using defaults.");
            return config;
        }
        try (InputStream in = Files.newInputStream(path)) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            if (root == null) return config;

            Map<String, Object> world = (Map<String, Object>) root.getOrDefault("world", Map.of());
            config.seed = toLong(world.getOrDefault("seed", config.seed));
            config.worldWidth = toInt(world.getOrDefault("width", config.worldWidth));
            config.worldHeight = toInt(world.getOrDefault("height", config.worldHeight));
            config.resourceDensity = toDouble(world.getOrDefault("resourceDensity", config.resourceDensity));
            Map<String, Object> terrainNoise = (Map<String, Object>) world.getOrDefault("terrainNoise", Map.of());
            config.noiseOctaves = toInt(terrainNoise.getOrDefault("octaves", config.noiseOctaves));
            config.noisePersistence = toDouble(terrainNoise.getOrDefault("persistence", config.noisePersistence));

            Map<String, Object> agents = (Map<String, Object>) root.getOrDefault("agents", Map.of());
            config.agentCount = toInt(agents.getOrDefault("count", config.agentCount));
            config.perceptionRadius = toInt(agents.getOrDefault("perceptionRadius", config.perceptionRadius));
            Object arcDist = agents.get("archetypeDistribution");
            if (arcDist instanceof Map<?, ?> dist) {
                config.archetypeDistribution.clear();
                dist.forEach((k, v) -> config.archetypeDistribution.put(k.toString(), toDouble(v)));
            }

            Map<String, Object> robots = (Map<String, Object>) root.getOrDefault("robots", Map.of());
            config.robotInitialCost = toDouble(robots.getOrDefault("initialCost", config.robotInitialCost));
            config.robotCostDecayRate = toDouble(robots.getOrDefault("costDecayRate", config.robotCostDecayRate));
            config.robotInitialEfficiency = toDouble(robots.getOrDefault("initialEfficiency", config.robotInitialEfficiency));
            config.robotEfficiencyGrowthRate = toDouble(robots.getOrDefault("efficiencyGrowthRate", config.robotEfficiencyGrowthRate));

            Map<String, Object> meas = (Map<String, Object>) root.getOrDefault("meas", Map.of());
            config.measEnabled = (boolean) meas.getOrDefault("enabled", config.measEnabled);
            config.ubiEnabled = (boolean) meas.getOrDefault("ubiEnabled", config.ubiEnabled);
            config.baseTransactionTax = toDouble(meas.getOrDefault("baseTransactionTax", config.baseTransactionTax));

            Map<String, Object> llm = (Map<String, Object>) root.getOrDefault("llm", Map.of());
            config.llmProvider = (String) llm.getOrDefault("provider", config.llmProvider);
            config.agentModel = (String) llm.getOrDefault("agentModel", config.agentModel);
            config.complexModel = (String) llm.getOrDefault("complexModel", config.complexModel);
            config.gameMasterModel = (String) llm.getOrDefault("gameMasterModel", config.gameMasterModel);
            config.maxAgentCallsPerTick = toInt(llm.getOrDefault("maxAgentCallsPerTick", config.maxAgentCallsPerTick));
            config.maxGameMasterCallsPerTick = toInt(llm.getOrDefault("maxGameMasterCallsPerTick", config.maxGameMasterCallsPerTick));
            config.totalBudgetUsd = toDouble(llm.getOrDefault("totalBudgetUsd", config.totalBudgetUsd));
            config.cacheEnabled = (boolean) llm.getOrDefault("cacheEnabled", config.cacheEnabled);
            config.cacheTtlTicks = toInt(llm.getOrDefault("cacheTtlTicks", config.cacheTtlTicks));
            config.apiKey = (String) llm.getOrDefault("apiKey", config.apiKey);
            config.apiBaseUrl = (String) llm.getOrDefault("apiBaseUrl", config.apiBaseUrl);
            // Fallback to environment variable
            if (config.apiKey == null || config.apiKey.isEmpty()) {
                String envKey = System.getenv("ANTHROPIC_API_KEY");
                if (envKey != null && !envKey.isEmpty()) {
                    config.apiKey = envKey;
                    System.out.println("API key loaded from ANTHROPIC_API_KEY environment variable.");
                } else {
                    System.out.println("WARNING: No API key found. LLM features will use deterministic fallback.");
                    System.out.println("  Set via: $env:ANTHROPIC_API_KEY = \"your-key\" (PowerShell)");
                    System.out.println("  Or enter it in the launcher UI's API Key field.");
                }
            } else {
                System.out.println("API key loaded from config file.");
            }

            Map<String, Object> sim = (Map<String, Object>) root.getOrDefault("simulation", Map.of());
            config.ticksPerYear = toInt(sim.getOrDefault("ticksPerYear", config.ticksPerYear));
            config.totalYears = toInt(sim.getOrDefault("totalYears", config.totalYears));
            config.snapshotInterval = toInt(sim.getOrDefault("snapshotInterval", config.snapshotInterval));
            config.metricsInterval = toInt(sim.getOrDefault("metricsInterval", config.metricsInterval));
        } catch (IOException e) {
            System.err.println("Error loading config: " + e.getMessage() + ". Using defaults.");
        }
        return config;
    }

    private static int toInt(Object o) { return o instanceof Number n ? n.intValue() : Integer.parseInt(o.toString()); }
    private static long toLong(Object o) { return o instanceof Number n ? n.longValue() : Long.parseLong(o.toString()); }
    private static double toDouble(Object o) { return o instanceof Number n ? n.doubleValue() : Double.parseDouble(o.toString()); }

    public long seed() { return seed; }
    public int worldWidth() { return worldWidth; }
    public int worldHeight() { return worldHeight; }
    public double resourceDensity() { return resourceDensity; }
    public int noiseOctaves() { return noiseOctaves; }
    public double noisePersistence() { return noisePersistence; }
    public int agentCount() { return agentCount; }
    public Map<String, Double> archetypeDistribution() { return archetypeDistribution; }
    public int perceptionRadius() { return perceptionRadius; }
    public int memoryCapacity() { return memoryCapacity; }
    public double startingCredits() { return startingCredits; }
    public double robotInitialCost() { return robotInitialCost; }
    public double robotCostDecayRate() { return robotCostDecayRate; }
    public double robotInitialEfficiency() { return robotInitialEfficiency; }
    public double robotEfficiencyGrowthRate() { return robotEfficiencyGrowthRate; }
    public boolean measEnabled() { return measEnabled; }
    public boolean ubiEnabled() { return ubiEnabled; }
    public double baseTransactionTax() { return baseTransactionTax; }
    public String llmProvider() { return llmProvider; }
    public String apiKey() { return apiKey; }
    public String apiBaseUrl() { return apiBaseUrl; }
    public String agentModel() { return agentModel; }
    public String complexModel() { return complexModel; }
    public String gameMasterModel() { return gameMasterModel; }
    public int maxAgentCallsPerTick() { return maxAgentCallsPerTick; }
    public int maxGameMasterCallsPerTick() { return maxGameMasterCallsPerTick; }
    public double totalBudgetUsd() { return totalBudgetUsd; }
    public boolean cacheEnabled() { return cacheEnabled; }
    public int cacheTtlTicks() { return cacheTtlTicks; }
    public boolean hasApiKey() { return apiKey != null && !apiKey.isEmpty(); }
    public int ticksPerYear() { return ticksPerYear; }
    public int totalYears() { return totalYears; }
    public int totalTicks() { return ticksPerYear * totalYears; }
    public int snapshotInterval() { return snapshotInterval; }
    public int metricsInterval() { return metricsInterval; }
    public int ubiDistributionInterval() { return ubiDistributionInterval; }
    public double resourceDiscoveryProbability() { return resourceDiscoveryProbability; }
    public double techBreakthroughProbability() { return techBreakthroughProbability; }
    public double environmentalCrisisThreshold() { return environmentalCrisisThreshold; }
    public List<GovernmentDef> governments() { return governments; }

    public record GovernmentDef(String name, int q1, int r1, int q2, int r2,
                                double efWeight, double ubiMultiplier, double domainTwoStrictness) {}
}
