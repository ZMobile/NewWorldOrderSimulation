# MeaSim — MEAS Society Simulator

## Simulation Architecture Specification

**Version:** 0.2.0  
**Date:** March 2026  
**Status:** Draft  
**Language:** Java 21+  

> A Java-based agent simulation for testing the MEAS economic system in a procedurally generated world with AI-powered humanoid labor.

---

## 1. Overview

MeaSim simulates a society operating under the MEAS economic system (see `MEAS_SYSTEM_SPEC.md`). Autonomous agents inhabit a procedurally generated hex-tile world where AI-powered humanoid robots can perform any labor. The simulation measures whether MEAS produces healthy societal outcomes: broad welfare, entrepreneurship, innovation, and environmental sustainability.

### 1.1 Key Architectural Decisions

- **Hex grid with axial coordinates** for distance-consistent spatial mechanics (trade cost, pollution diffusion, infrastructure range)
- **Hybrid deterministic/LLM agent architecture** — most behavior is utility-function-based; LLM calls reserved for novel/complex decisions
- **Separate Game Master LLM** for innovation and world-coherence adjudication, architecturally distinct from agent reasoning
- **Archetype-based agent personalities** for LLM cost management via batching
- **Data-driven visual vocabulary** — new Game Master creations render automatically via shape+color assignment

### 1.2 Simulation Objectives

1. Validate that MEAS scoring produces intended incentive effects under realistic conditions
2. Identify failure modes, gaming strategies, and unintended consequences
3. Compare MEAS outcomes against baseline capitalism under identical world conditions
4. Test governance mechanics: formula modification, judicial disputes, democratic processes
5. Evaluate system behavior across multiple government configurations
6. Measure the impact of increasing automation on societal stability

### 1.3 Prior Art Informing Design

- **Stanford Smallville** (25 agents, Sims-like): observation/planning/reflection architecture, memory stream essential for believability
- **Altera Project Sid** (1000 agents, Minecraft): emergent currency/governance; agents fell into loops at scale → validates our hybrid deterministic/LLM approach
- **ChatDev** (multi-agent role-based): structured communication chains between specialized agents

---

## 2. World Model

### 2.1 Hexagonal Grid System

The world uses a **hexagonal tile grid with axial coordinates (q, r)**. The third cube coordinate is derived: `s = -q - r`.

**Why hexagons over squares:**
- Every neighbor is equidistant (6 neighbors, all at distance 1)
- No diagonal distortion in distance calculations
- Trade cost, pollution diffusion, and infrastructure range all depend on distance — hex eliminates artifacts
- Well-established algorithms for pathfinding, range queries, and field-of-view on hex grids

**Hex distance formula:**
```
distance(a, b) = (|a.q - b.q| + |a.r - b.r| + |a.s - b.s|) / 2
```

**Storage:** 2D array with offset mapping. World dimensions specified in config (e.g., 200×200 hexes).

**Coordinate system:** Axial (q, r) for storage and API. Cube (q, r, s) for distance and range calculations. Pixel conversion for rendering uses standard hex-to-pixel formulas.

### 2.2 Procedural World Generation

Generated from a seed value (ensuring reproducibility for comparative runs).

**Terrain types:** GRASSLAND, MOUNTAIN, DESERT, WATER, FOREST, TUNDRA, WETLAND. Generated using Perlin noise or similar coherent noise, producing naturalistic continents, mountain ranges, and water bodies.

**Resource nodes:** Spatially distributed deposits tied to terrain type:
- MINERAL → primarily MOUNTAIN, some DESERT
- ENERGY → DESERT (solar), MOUNTAIN (geothermal), WATER (hydro), GRASSLAND (wind)
- FOOD_LAND → GRASSLAND, WETLAND
- WATER → adjacent to WATER tiles, WETLAND
- TIMBER → FOREST

Distribution is intentionally uneven to force trade and specialization. Resource abundance per node varies (1–10 units, Gaussian distribution around terrain-specific means).

**Settlement zones:** Pre-designated areas where agents can establish housing and businesses. Placed by the generator at locations with good resource access and connectivity. Multiple settlement zones create distinct communities that can develop different governance parameters.

**Transportation network:** Initial pathways between settlements. Distance affects trade cost:
```
trade_cost(a, b) = base_rate × hex_distance(a, b) × terrain_difficulty_factor
```
Infrastructure technologies can reduce trade cost on specific routes.

### 2.3 Environmental Model

Each hex tile maintains environmental health values:

```java
class TileEnvironment {
    double soilQuality;    // 0.0–1.0, affects FOOD production
    double airQuality;     // 0.0–1.0, affects agent satisfaction
    double waterQuality;   // 0.0–1.0, affects WATER resource yield
    double biodiversity;   // 0.0–1.0, slow to recover once damaged
}
```

**Degradation:** Production activities reduce environmental values on the production tile and adjacent tiles (with falloff by hex distance). Degradation amount comes from the production chain's pollution value.

**Pollution diffusion:** Each tick, pollution spreads to neighbors with decay:
```
neighbor_pollution = source_pollution × diffusion_rate × (1 / (1 + hex_distance))
```

**Recovery:** Natural recovery of +0.01 per tick per axis on tiles with no active pollution source. Biodiversity recovers at half rate. Recovery can be accelerated by remediation infrastructure technologies.

**Environmental crisis triggers:** If average environmental health in a region drops below 0.3, a crisis event fires (crop failures, water shortages), forcing agent adaptation.

### 2.4 Resource and Production System

Entirely deterministic. Raw resources transform into goods through known production chains.

**Base resource types:** MINERAL, ENERGY, FOOD_LAND, WATER, TIMBER

**Base product types:** BASIC_GOODS, CONSTRUCTION, TECHNOLOGY, FOOD, LUXURY, MEDICINE

**Production chain format:**
```java
record ProductionChain(
    String id,
    String name,
    Map<ResourceType, Integer> inputs,
    Map<ProductType, Integer> outputs,
    double pollutionOutput,    // feeds into EF scoring axis
    int productionTimeTicks,
    Set<String> prerequisiteTechs,  // tech tree dependencies
    boolean isDiscovered       // false for Game Master discoveries not yet made
) {}
```

**Initial production chains (examples):**
```
MINERAL(10) + ENERGY(5)                    → CONSTRUCTION(3)       [pollution: 2.0, time: 1]
FOOD_LAND(1) + WATER(2)                    → FOOD(8)              [pollution: 0.5, time: 1]
MINERAL(3) + ENERGY(8)                     → TECHNOLOGY(2)        [pollution: 1.5, time: 2]
MINERAL(5) + ENERGY(10) + TECHNOLOGY(2)    → LUXURY(1)            [pollution: 3.0, time: 2]
TIMBER(4) + MINERAL(2)                     → BASIC_GOODS(5)       [pollution: 1.0, time: 1]
FOOD_LAND(2) + WATER(3) + TECHNOLOGY(1)    → MEDICINE(2)          [pollution: 0.8, time: 2]
```

New chains can be discovered through the Game Master innovation system (Section 7).

### 2.5 Robot Labor

Humanoid AI robots are first-class entities. Any production chain can be operated by human agents or robots.

```java
record RobotUnit(
    String id,
    String ownerId,         // agent who owns this robot
    double efficiency,      // multiplier vs human production (starts ~1.2, grows)
    double energyCostPerTick,
    int acquisitionTick
) {}
```

**Robot economics:**
- **Capital cost:** One-time credit expenditure. Decreases over simulation time (config: `costDecayRate`)
- **Operating cost:** ENERGY consumption per tick
- **Efficiency multiplier:** Configurable, increases over time (config: `efficiencyGrowthRate`)
- **No wages:** Output value flows to owner, subject to MEAS modifiers including LD diversion

**Automation curve:** Robot cost and efficiency follow configurable curves, allowing simulation of slow-to-rapid automation transition.

### 2.6 Market System

Deterministic order-book-based. No LLM calls.

```java
class OrderBook {
    PriorityQueue<Order> buyOrders;   // sorted by price descending
    PriorityQueue<Order> sellOrders;  // sorted by price ascending
    
    List<Transaction> match();  // returns executed trades
}
```

Multiple markets exist in different settlements. Price differences create arbitrage opportunities that drive trade. Trade cost between settlements is a function of hex distance and transportation infrastructure.

---

## 3. Agent Model

### 3.1 Agent Composition

Each agent has four subsystems:

#### 3.1.1 Identity Profile (Immutable)

Set at creation, never changes. Defines behavioral tendencies:

| Attribute | Type | Description |
|-----------|------|-------------|
| `archetype` | enum (1 of 12) | Personality template for LLM prompt construction |
| `riskTolerance` | float [0, 1] | Willingness to take financial risks |
| `ambition` | float [0, 1] | Drive to accumulate above UBI level |
| `altruism` | float [0, 1] | Weight given to collective welfare vs. self-interest |
| `creativity` | float [0, 1] | Likelihood of attempting novel production chains |
| `skillDomains` | Set\<SkillType\> | What this agent is good at |
| `complianceDisposition` | float [0, 1] | Tendency to follow rules vs. attempt gaming |

#### 3.1.2 Mutable State

Changes every tick:

| Field | Type | Description |
|-------|------|-------------|
| `credits` | double | Current credit balance |
| `scoreVector` | ScoreVector | Current values on all 5 MEAS axes |
| `modifiers` | ModifierSet | Computed modifiers (derived from scoreVector) |
| `employmentStatus` | enum | UNEMPLOYED, EMPLOYED, BUSINESS_OWNER, RETIRED |
| `ownedBusinesses` | List\<Business\> | Businesses owned and operated |
| `ownedRobots` | int | Robot units owned |
| `inventory` | Map\<ItemType, Integer\> | Resources and goods held |
| `location` | HexCoord | Current position (axial q, r) |
| `satisfaction` | float [0, 1] | Computed wellbeing metric |
| `domainTwoCompliance` | boolean | Passes Domain 2 harm thresholds? |

#### 3.1.3 Decision Engine (Hybrid)

**Tier 1 — Deterministic Utility Calculator:**

Runs every tick for every agent. No LLM calls. Selects the action maximizing expected utility weighted by identity parameters:

```
U(agent, action) = E[credits(action)] × (1 + riskTolerance × variance(action))
                 + altruism × E[socialBenefit(action)]
                 - (1 - complianceDisposition) × regulatoryRisk(action)
                 + ambition × longTermGrowth(action)
                 + locationSuitability(action)
```

The `locationSuitability` factor considers: proximity to needed resources, distance to markets, environmental quality of target tiles, existing competition density, and transportation infrastructure availability.

**Tier 2 — LLM Escalation:**

Triggered when the deterministic calculator encounters situations outside its parameters:
- Novel market conditions (price outside 2σ of historical range)
- Complex social decisions (coalition formation, political behavior)
- Response to systemic shocks (formula changes, resource crises, environmental disasters)
- Entrepreneurial decisions (new business type, new production chain research)
- Governance participation (proposing formula changes, voting rationale, filing disputes)
- Strategic relocation decisions (move operations to new area based on changing conditions)

On escalation: archetype prompt template populated with current state + relevant memory + decision context → LLM API call → response parsed into concrete action.

#### 3.1.4 Memory Stream

Chronological log of perceived events, actions, and outcomes. Supports:

- **Recency-weighted retrieval:** Recent events weighted higher
- **Importance scoring:** Events with large state impact (credit changes, score shifts, business success/failure) scored higher
- **Relevance matching:** On LLM escalation, memory stream queried for events relevant to current decision; top-k included in prompt

Stored as bounded circular buffer per agent. Older, low-importance memories evicted first.

### 3.2 Spatial Perception

Each agent has a **perception radius** (configurable, default 7 hexes). Within this radius, they directly observe:
- Resource availability on tiles
- Other agents and their activities
- Environmental quality
- Structures and businesses

Beyond perception radius:
- Market prices are available but delayed by hex distance (1 tick delay per 10 hexes)
- Other agents can share information through social interaction
- Governance announcements are global (no delay)

For LLM-escalated decisions, the agent receives a **spatial context summary** generated deterministically from world state:

```
"You are in Settlement Alpha (industrial zone, environmental health 0.62, good MINERAL 
access within 3 hexes, nearest market 2 hexes, 14 competing businesses). Settlement 
Beta is 18 hexes southwest (environmental health 0.89, FOOD_LAND rich, small market, 
3 competing businesses). A new RARE_EARTH deposit was discovered 6 hexes northeast 
with no nearby infrastructure."
```

The LLM reasons about strategic implications. The pathfinding engine handles actual movement (A* on hex grid, fully deterministic).

### 3.3 Agent Archetypes

| Archetype | Behavioral Profile | What It Tests |
|-----------|--------------------|---------------|
| Optimizer | Low altruism, high ambition | System gaming, max accumulation |
| Entrepreneur | High risk, high creativity | Business creation, innovation |
| Cooperator | High altruism, moderate ambition | Commons contribution, community |
| Free Rider | Low ambition, low compliance | UBI adequacy, minimal participation |
| Regulator | High compliance | Governance, formula proposals |
| Innovator | Extreme creativity | Research system, tech tree |
| Accumulator | Max ambition, moderate risk | RC axis limits |
| Exploiter | Low compliance, high ambition | Scoring loopholes, measurement gaps |
| Artisan | Moderate ambition, quality-focused | Non-maximizing behavior |
| Politician | High social engagement | Coalitions, governance capture |
| Philanthropist | High altruism, low ambition | Redistribution, public goods |
| Automator | High ambition, tech-focused | LD dynamics, robot adoption |

### 3.4 LLM Cost Management

- **Batching:** Same-archetype agents facing similar conditions share a single LLM call; response pattern applied across batch
- **Escalation frequency caps:** Maximum LLM calls per agent per simulation day
- **Response caching:** Identical decision contexts reuse cached responses with small random perturbation
- **Model tiering:** Complex decisions (entrepreneurship, governance) → Opus; simple escalations → Sonnet
- **Budget configuration:** Total USD budget per run; engine distributes calls across ticks, prioritizing high-novelty ticks

---

## 4. Simulation Engine

### 4.1 Tick Loop

Each tick = one simulated month. Phases execute in strict order:

| Phase | Name | LLM? | Description |
|-------|------|------|-------------|
| 1 | Perception | No | Agents observe local environment; observations appended to memory stream |
| 2 | Decision | Partial | Tier 1 (deterministic) for all agents; Tier 2 (LLM) for triggered escalations |
| 3 | Action Execution | Partial | Production, trade orders, business creation, research proposals. Game Master LLM adjudicates research. |
| 4 | Market Resolution | No | Order books match buy/sell; MEAS modifiers applied to all transactions; diversions computed |
| 5 | Scoring | No | Score vectors recomputed; modifiers updated; audit trail appended; Domain 2 compliance checked |
| 6 | UBI Distribution | No | If distribution cycle tick: credits distributed from UBI pool to all eligible agents |
| 7 | Governance | Partial | Proposals, votes, disputes processed. Politician/Regulator agents may trigger LLM for governance reasoning |
| 8 | Environment | No | Pollution applied to tiles; natural recovery; crisis triggers evaluated |
| 9 | Events | Config | Random/scheduled disruptions injected (resource discoveries, tech breakthroughs, crises) |
| 10 | Measurement | No | Aggregate metrics computed and logged |

### 4.2 Multi-Government Support

Multiple concurrent governments, each controlling a hex region. Same MEAS architecture, different parameter values (modifier thresholds, UBI levels, Domain 2 standards). Enables direct policy comparison.

Agents can migrate between jurisdictions based on satisfaction. Creates competitive pressure between governance configurations.

---

## 5. Game Master LLM Layer

Architecturally distinct from agent LLM. Separate system prompt, separate call budget, more capable model (Opus).

### 5.1 Responsibilities

1. **Adjudicating research outcomes** when agents invest in R&D
2. **Generating novel world elements** — new resources, production chains, infrastructure technologies
3. **Maintaining world coherence** — ensuring new elements don't contradict existing physics or break balance

### 5.2 Research Pipeline

**Stage 1 — Investment:** Agent allocates credits and ticks to a research direction. Deterministic cost.

**Stage 2 — Proposal:** Agent (or agent LLM) formulates specific hypothesis/experiment. Example: "What happens if I combine MINERAL with TIMBER under high ENERGY input?" or "Can I build a device that extracts WATER from humid air?"

**Stage 3 — Game Master Adjudication:** Game Master LLM receives:
- Full current technology tree (all discovered chains, resources, infrastructure)
- World's physical constitution (conservation laws, balance bounds, progression requirements)
- The specific research proposal
- Agent's investment level and research history
- Instructions to return structured JSON within defined bounds

Returns: success boolean + deterministic specification if successful.

**Stage 4 — Registration:** If successful, new element enters the technology registry with immutable deterministic properties + assigned visual properties from available palette.

**Stage 5 — Diffusion:** Discoverer chooses: publish (commons contribution boost, everyone can use) or patent (temporary monopoly of N ticks, no commons boost during monopoly, then public domain).

### 5.3 Discovery Categories

#### Category 1: Production Chain Improvements
Optimizations to existing chains — lower inputs, lower pollution, faster production.
- **Frequency:** Most common
- **Impact:** Low-moderate
- **Example:** Improved smelting: MINERAL(8) + ENERGY(4) → CONSTRUCTION(3) [pollution: 1.5] instead of MINERAL(10) + ENERGY(5) → CONSTRUCTION(3) [pollution: 2.0]

#### Category 2: New Production Chains
Entirely new ways to combine existing resources into existing or new products.
- **Frequency:** Moderate
- **Impact:** Moderate-high
- **Example:** TIMBER(5) + WATER(3) → BIOFUEL(4) [pollution: 0.3, time: 1]

#### Category 3: New Resource Types
New extractable resources discovered in the world.
- **Frequency:** Rare
- **Impact:** High (can reshape economic geography)
- **Example:** RARE_EARTH discovered on MOUNTAIN tiles; ALGAE harvestable from WETLAND tiles
- **Note:** New resources need discoverable production chains to be useful; often paired with Category 2

#### Category 4: Infrastructure Technologies
Change the rules of the world rather than adding items.
- **Frequency:** Very rare, most expensive
- **Impact:** Very high
- **Effect types (enum):** TRADE_COST_REDUCTION, POLLUTION_REDUCTION, ROBOT_EFFICIENCY_BOOST, PRODUCTION_SPEED_BOOST, ENVIRONMENTAL_REMEDIATION, RESOURCE_EXTRACTION_IMPROVEMENT
- **Example:** "Atmospheric carbon capture: passively reduces pollution on adjacent tiles by 0.1/tick"
- **Example:** "Advanced logistics network: reduces trade cost between connected settlements by 40%"

### 5.4 Game Master Constraint System

**Conservation laws:** Every production chain must have inputs that account for outputs. Energy conserved. Matter approximately conserved.

**Technology progression:** Tech tree with prerequisites. Can't discover fusion before plasma physics. Can't discover advanced robotics before basic electronics. Game Master can add branches but each node must connect logically to existing nodes.

**Balance bounds:** All numerical properties must fall within defined ranges per discovery category. A new chain can't produce 1000× output of existing chains. Response parser validates bounds; out-of-bounds → discovery fails.

**Diminishing returns:** As tech tree grows, further discoveries harder. Game Master prompt includes tree depth and difficulty scaling factor. Early simulation = easier breakthroughs. Late simulation = harder marginal improvements.

**Pollution floor:** Every physical production chain must have non-zero pollution (thermodynamics). Remediation technologies can clean up pollution but cannot make production zero-impact.

### 5.5 Discovery Specification Format

```json
{
  "type": "PRODUCTION_CHAIN | NEW_RESOURCE | INFRASTRUCTURE_TECH",
  "id": "auto-generated UUID",
  "name": "Efficient Solar Smelting",
  "description": "Uses concentrated solar energy to reduce mineral processing pollution",
  "category": 1,
  
  "inputs": [{"type": "MINERAL", "amount": 7}, {"type": "ENERGY", "amount": 3}],
  "outputs": [{"type": "CONSTRUCTION", "amount": 3}],
  "pollutionOutput": 1.2,
  "productionTimeTicks": 1,
  "prerequisiteTechs": ["basic_metallurgy", "solar_concentration"],
  
  "spawnConditions": null,
  "effectType": null,
  "effectMagnitude": null,
  
  "visualProperties": {
    "shape": "ASSIGNED_AT_REGISTRATION",
    "color": "ASSIGNED_AT_REGISTRATION"
  }
}
```

### 5.6 Game Master Cost Budget

Game Master calls are expensive (Opus + large context for full tech tree). Estimated 5–20 research proposals per tick. Budget allocation: ~20–30% of total LLM budget reserved for Game Master.

---

## 6. Visualization System

### 6.1 Rendering Layers

The hex map renders five stacked layers:

1. **Terrain layer (base):** Hex tiles colored by terrain type. GRASSLAND=green, MOUNTAIN=grey, DESERT=tan, WATER=blue, FOREST=dark green, TUNDRA=white, WETLAND=teal. Generated once, never changes.

2. **Resource layer:** Icons on tiles showing extractable resources. Each resource type has a shape+color from the visual vocabulary. Base resources have fixed assignments (MINERAL=grey circle, ENERGY=yellow lightning, FOOD_LAND=green square, WATER=blue droplet, TIMBER=brown triangle). Game Master-created resources get assigned from the available palette.

3. **Structure layer:** Buildings and facilities placed by agents. Production facility=rectangle (tinted by pollution output: green→red). Market=circle with trade icon. Housing=small square cluster. Infrastructure=type-specific rendering (see 6.3).

4. **Agent layer:** Agents as dots/small sprites moving on the hex grid. Color-coded by archetype. Robots are squares (distinct from human circles). Lines briefly drawn between interacting agents.

5. **Environment overlay (toggleable):** Color gradient overlay showing tile environmental health. Green (healthy, >0.7) → yellow (moderate, 0.4–0.7) → red (degraded, <0.4). Makes pollution spread visually immediate.

### 6.2 Visual Vocabulary

| Primitive | Options |
|-----------|---------|
| Shapes | circle, triangle, square, diamond, pentagon, hexagon, star, cross |
| Colors | 20-color high-contrast palette |
| Size | small, medium, large (encodes quantity/importance) |
| Border | solid (active), dashed (under construction), glowing (recently discovered) |

8 shapes × 20 colors = **160 unique resource/item identifiers** before size or border variations. Sufficient for any reasonable simulation run.

New Game Master creations are assigned shape+color from the unused pool during registration. The renderer is data-driven — reads the technology registry and draws what's there.

### 6.3 Infrastructure Visualization

Each infrastructure effect type maps to a predefined visual treatment:

| Effect Type | Visual Rendering |
|-------------|-----------------|
| TRADE_COST_REDUCTION | Dashed line between connected settlements, color by magnitude |
| POLLUTION_REDUCTION | Green translucent overlay on affected hexes |
| ROBOT_EFFICIENCY_BOOST | Small gear icon on facilities using it |
| PRODUCTION_SPEED_BOOST | Distinct border color on affected facility |
| ENVIRONMENTAL_REMEDIATION | Animated green pulse on installation tile |
| RESOURCE_EXTRACTION_IMPROVEMENT | Upward arrow on affected resource nodes |

### 6.4 Inspector Panel

Clicking any hex, agent, or structure opens a detail panel:

- **Tile:** Terrain type, resources, environmental health values, structures, government jurisdiction
- **Agent:** Full identity profile, current state, score vector, modifiers, memory highlights, recent actions
- **Structure:** Production chains running, input/output rates, pollution, workforce (humans vs robots), owner's scoring impact
- **Technology:** Full deterministic spec, discoverer, discovery tick, adoption rate, world impact

### 6.5 Dashboard

Separate from map. Time-series graphs updated each tick:
- Gini coefficient
- Environmental health trend
- UBI pool size and per-capita distribution
- Entrepreneurship rate
- Innovation count (tech tree growth)
- Satisfaction distribution
- Comparative overlay (MEAS vs baseline) if running comparison

### 6.6 Implementation Approach

**Phase 1 (headless):** No visualization. Simulation runs in CLI mode, outputs CSV metrics and JSON snapshots. Analysis in external tools (Python/pandas, R).

**Phase 2 (JavaFX):** 2D hex map renderer using JavaFX Canvas. Inspector panel as JavaFX sidebar. Dashboard using JavaFX charts. All standard Java — no exotic dependencies.

**Phase 3 (web, optional):** Export tile data to web-based renderer (HTML Canvas or WebGL) that reads JSON snapshots. Decouples visualization from simulation engine.

---

## 7. Java Application Architecture

### 7.1 Module Structure

```
com.measim
├── core/                  // Simulation engine, tick loop, event bus
│   ├── SimulationEngine.java
│   ├── TickPhase.java              (interface)
│   ├── SimulationConfig.java
│   ├── SimulationState.java
│   └── EventBus.java
├── world/                 // World generation, hex grid, resources
│   ├── HexGrid.java               // Axial coordinate system, neighbors, distance
│   ├── HexCoord.java              // record(int q, int r)
│   ├── WorldGenerator.java        // Procedural generation from seed
│   ├── Tile.java                  // Terrain + environment + resources
│   ├── ResourceNode.java
│   ├── EnvironmentModel.java      // Pollution diffusion, recovery
│   ├── Pathfinding.java           // A* on hex grid
│   └── TerrainType.java           (enum)
├── economy/               // Markets, production, credits
│   ├── MarketEngine.java
│   ├── OrderBook.java
│   ├── ProductionChain.java       (record)
│   ├── ProductionExecutor.java
│   ├── Transaction.java           (record)
│   ├── CreditFlow.java
│   ├── ResourceType.java          (enum, extensible for GM discoveries)
│   └── ProductType.java           (enum, extensible for GM discoveries)
├── scoring/               // MEAS scoring system (fully deterministic)
│   ├── ScoringEngine.java
│   ├── ScoreVector.java
│   ├── ModifierFunction.java      (interface)
│   ├── EnvironmentalFootprintModifier.java
│   ├── CommonsContributionModifier.java
│   ├── LaborDisplacementDiversion.java
│   ├── ResourceConcentrationModifier.java
│   ├── FormulaVersion.java
│   ├── SectorBaseline.java
│   └── AuditTrail.java            // Append-only log
├── agent/                 // Agent model, decision engine, archetypes
│   ├── Agent.java
│   ├── IdentityProfile.java       (record, immutable)
│   ├── AgentState.java            (mutable)
│   ├── DecisionEngine.java
│   ├── UtilityCalculator.java     // Tier 1 deterministic
│   ├── LlmEscalationTrigger.java  // Tier 2 conditions
│   ├── MemoryStream.java          // Bounded circular buffer
│   ├── MemoryEntry.java           (record)
│   ├── SpatialPerception.java     // Generates context summaries
│   ├── Archetype.java             (enum)
│   └── ArchetypePromptTemplates.java
├── governance/            // Multi-government, voting, judicial
│   ├── Government.java
│   ├── GovernmentConfig.java      // Parameter values for this jurisdiction
│   ├── FormulaProposal.java
│   ├── VotingSystem.java
│   ├── JudicialSystem.java
│   ├── DisputeCase.java
│   └── MigrationEngine.java      // Agent jurisdiction changes
├── robot/                 // Robot labor entities
│   ├── RobotUnit.java             (record)
│   └── AutomationCurve.java      // Cost/efficiency over time
├── gamemaster/            // Game Master LLM layer
│   ├── GameMaster.java
│   ├── ResearchProposal.java
│   ├── DiscoverySpec.java         (record — the JSON spec)
│   ├── TechnologyRegistry.java    // All discovered techs
│   ├── TechTree.java              // Prerequisite graph
│   ├── ConstraintValidator.java   // Conservation, balance, progression checks
│   ├── DifficultyScaling.java     // Diminishing returns model
│   └── GameMasterPromptBuilder.java
├── llm/                   // LLM integration layer
│   ├── LlmClient.java            (interface)
│   ├── AnthropicClient.java
│   ├── OpenAiClient.java
│   ├── PromptBuilder.java
│   ├── ResponseParser.java
│   ├── BatchManager.java
│   ├── ResponseCache.java
│   └── CostTracker.java          // Budget management
├── event/                 // Disruption events, scenarios
│   ├── EventScheduler.java
│   ├── DisruptionEvent.java       (interface)
│   ├── ResourceDiscovery.java
│   ├── ResourceDepletion.java
│   ├── TechBreakthrough.java
│   ├── EnvironmentalCrisis.java
│   └── PopulationChange.java
├── metrics/               // Measurement and analysis
│   ├── MetricsCollector.java
│   ├── GiniCalculator.java
│   ├── InnovationIndex.java
│   ├── SatisfactionSurvey.java
│   ├── TimeSeriesLogger.java      // CSV output
│   └── ComparisonReporter.java    // MEAS vs baseline diff
└── ui/                    // Visualization (Phase 2)
    ├── SimulationViewer.java
    ├── HexRenderer.java           // Hex-to-pixel, layer rendering
    ├── VisualVocabulary.java      // Shape+color assignment pool
    ├── DashboardPanel.java
    └── InspectorPanel.java
```

### 7.2 Key Interfaces

#### TickPhase
```java
public interface TickPhase {
    String name();
    void execute(SimulationState state, TickContext context);
    int order();  // execution order within tick
}
```
Each tick loop phase implements this. SimulationEngine collects, sorts by order(), executes sequentially. Extensible without modifying engine.

#### ModifierFunction
```java
public interface ModifierFunction {
    String axisName();
    FormulaVersion version();
    double compute(Agent agent, SectorBaseline baseline);
    String explain(Agent agent, SectorBaseline baseline);  // human-readable audit trace
}
```
Every scoring axis implements this. `explain()` produces audit string supporting traceability requirement.

#### LlmClient
```java
public interface LlmClient {
    CompletableFuture<LlmResponse> complete(LlmRequest request);
    CompletableFuture<List<LlmResponse>> batchComplete(List<LlmRequest> requests);
    CostEstimate estimateCost(LlmRequest request);
}
```
Abstracts LLM provider. `batchComplete` enables archetype batching. `estimateCost` enables budget throttling.

#### DisruptionEvent
```java
public interface DisruptionEvent {
    String description();
    int triggerTick();           // when it fires (-1 for random)
    double severity();           // 0.0–1.0
    void apply(WorldState world, List<Agent> agents);
    boolean triggersLlmEscalation();
}
```

### 7.3 Configuration (YAML)

```yaml
world:
  seed: 42
  width: 200
  height: 200
  resourceDensity: 0.3
  terrainNoise:
    octaves: 6
    persistence: 0.5

agents:
  count: 500
  archetypeDistribution:
    OPTIMIZER: 0.10
    ENTREPRENEUR: 0.12
    COOPERATOR: 0.15
    FREE_RIDER: 0.08
    REGULATOR: 0.05
    INNOVATOR: 0.08
    ACCUMULATOR: 0.07
    EXPLOITER: 0.05
    ARTISAN: 0.12
    POLITICIAN: 0.05
    PHILANTHROPIST: 0.05
    AUTOMATOR: 0.08
  perceptionRadius: 7

robots:
  initialCost: 10000
  costDecayRate: 0.05        # per simulated year
  initialEfficiency: 1.2
  efficiencyGrowthRate: 0.03 # per simulated year

meas:
  enabled: true              # false for baseline capitalism run
  formulaVersion: "v0.1.0"
  ubiEnabled: true
  ubiAdequacyTarget: 0.85
  baseTransactionTax: 0.005  # 0.5% flat rate to UBI pool

llm:
  provider: "anthropic"
  agentModel: "claude-sonnet-4-20250514"
  complexModel: "claude-opus-4-6"
  gameMasterModel: "claude-opus-4-6"
  maxAgentCallsPerTick: 50
  maxGameMasterCallsPerTick: 20
  totalBudgetUsd: 100.0
  cacheEnabled: true
  cacheTtlTicks: 5

simulation:
  ticksPerYear: 12           # monthly ticks
  totalYears: 50
  snapshotInterval: 12       # save state yearly
  metricsInterval: 1         # log metrics every tick

governments:
  - name: "Progressive"
    region: {q1: 0, r1: 0, q2: 100, r2: 200}
    efWeight: 1.2
    ubiMultiplier: 1.1
    domainTwoStrictness: 1.2
  - name: "Moderate"
    region: {q1: 100, r1: 0, q2: 200, r2: 200}
    efWeight: 1.0
    ubiMultiplier: 1.0
    domainTwoStrictness: 1.0

events:
  resourceDiscoveryProbability: 0.02  # per tick
  techBreakthroughProbability: 0.01
  environmentalCrisisThreshold: 0.3
```

### 7.4 Data Persistence

- **Snapshots:** Full simulation state serialized to JSON at configurable intervals. Enables pause/resume, branching, post-hoc analysis.
- **Metrics:** Time-series CSV files for external analysis (Python/pandas, R).
- **Audit trail:** Append-only file logging every score computation with inputs and outputs.
- **Tech registry:** JSON file of all discovered technologies with full specs.

---

## 8. Metrics and Measurement

### 8.1 Core Health Metrics

| Metric | Definition | Healthy Range |
|--------|-----------|---------------|
| Gini Coefficient | Wealth inequality (0=equal, 1=total inequality) | 0.25–0.40 |
| Entrepreneurship Rate | New businesses per 1000 agents per year | > 20 |
| Innovation Rate | New production chains discovered per year | > 5 |
| Environmental Health | Average tile quality (0–1) | > 0.70 |
| UBI Adequacy | Fraction of agents whose UBI covers basic needs | > 0.95 |
| Satisfaction Mean | Average agent satisfaction | > 0.60 |
| Satisfaction Std Dev | Satisfaction spread (lower=more equitable) | < 0.20 |
| Gaming Incidents | Detected scoring exploit attempts per year | < 50 |
| Governance Stability | Formula changes per year | 1–4 |
| Migration Rate | Agents changing jurisdiction per year | Convergence trend |

### 8.2 Comparison Methodology

Every scenario runs in pairs: MEAS enabled vs. baseline capitalism (no modifiers, no UBI). Same world seed, agent population, event schedule. ComparisonReporter generates differential analysis.

Additional comparison dimensions:
- Different MEAS parameter values (aggressive vs. moderate thresholds)
- Different government configurations
- Different automation curves (slow vs. rapid robot adoption)
- Different event schedules (stable vs. crisis-heavy)

---

## 9. Build and Run

### 9.1 Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 21+ (virtual threads, records, pattern matching) |
| Build | Gradle with Kotlin DSL |
| LLM | java.net.http async, CompletableFuture |
| Serialization | Jackson (JSON), optional Kryo (binary) |
| Config | SnakeYAML |
| Metrics | OpenCSV; optional Prometheus |
| Testing | JUnit 5, Mockito |
| Visualization | JavaFX (Phase 2); web renderer (Phase 3) |

### 9.2 Commands

```bash
# Build
./gradlew build

# Run with default config
java -jar measim.jar --config config/default.yaml

# Run comparison (MEAS vs baseline)
java -jar measim.jar --compare --config config/scenario1.yaml

# Resume from snapshot
java -jar measim.jar --resume snapshots/tick_120.json --config config/default.yaml

# Run headless (no UI, metrics only)
java -jar measim.jar --headless --config config/default.yaml
```

---

## 10. Implementation Priority

1. **World generation** — hex grid, procedural resource placement, terrain types
2. **Core economy** — production chains, market engine, credit flow (no MEAS modifiers yet)
3. **MEAS scoring engine** — all five axes, modifier functions, audit trail
4. **Agent framework** — identity profiles, mutable state, deterministic utility calculator
5. **Simulation tick loop** — all 10 phases executing in order
6. **Metrics collection** — Gini, entrepreneurship, environmental health, etc.
7. **LLM integration** — agent escalation, archetype prompts, batching
8. **Game Master** — research pipeline, discovery adjudication, tech tree
9. **Governance** — multi-government, voting, judicial disputes
10. **Visualization** — hex renderer, agent display, dashboard, inspector panel
11. **Comparison framework** — MEAS vs. baseline capitalism runs

---

## 11. Open Questions

- **Agent count vs. LLM cost:** 500 agents × 50 calls/tick × 600 ticks = ~30K API calls. At ~$0.003/call (Sonnet) ≈ $90/run. 1000+ agents needs more aggressive batching.
- **Deterministic reproducibility:** LLM outputs are stochastic. Fix temperature to 0 where possible; log all responses for replay; separate deterministic from LLM-dependent metrics.
- **Validation against real data:** Calibrate archetype distributions against real-world economic data (entrepreneurship rates, Gini, labor force participation).
- **Governance fidelity:** Simplified voting may miss dynamics. Consider lobbying, information asymmetry, campaign mechanics in later versions.

---

## 12. Version History

| Version | Date | Description |
|---------|------|-------------|
| 0.1.0 | March 2026 | Initial draft |
| 0.2.0 | March 2026 | Added: hex grid (axial coords), Game Master LLM layer with 4 discovery categories and constraint system, spatial perception model, visualization system with data-driven visual vocabulary, updated module structure |
