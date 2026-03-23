# MEAS Project — Design Context & Decision Record

## Project: Meritocratic Exte rnality-Aware Economic System (MEAS) + MeaSim Simulator

**Version:** 0.2.0  
**Date:** March 2026  
**Status:** Pre-implementation specification  

---

## 1. Problem Statement & Motivation

### 1.1 The Core Problem with Capitalism as Systems Architecture

Capitalism has a fundamental architectural flaw that becomes critical as AI and robotics advance: **productivity gains concentrate rather than distribute**. When an engineer achieves a 10x productivity improvement through AI automation, the company's rational response is to reduce headcount — capturing the gain as profit rather than distributing it as broad welfare. This pattern repeated during the Industrial Revolution and is accelerating now.

The second, more severe problem: **capitalism depends on human labor as the primary mechanism for distributing purchasing power**. When AI-powered humanoid robots can perform any labor, this mechanism breaks entirely. Capital flows to robot owners. Workers have no wages. The consumer base that drives demand collapses. The system crashes.

### 1.2 What Capitalism Gets Right

Capitalism's core strength is **incentive alignment through gamification**. Even purely self-centered, sociopathic actors are "tricked" into contributing value because the system rewards value creation with capital. This gamification of meritocracy extracts expertise and labor from people who would otherwise contribute nothing. Any replacement system must preserve this property.

### 1.3 What Communism Gets Wrong

Communism lacks the incentive/gamification structure. It also suffered from the **economic calculation problem** (Hayek): central planning cannot compute prices efficiently. Without differential rewards, you lose the signal about what's actually valued. Any replacement needs both distributed price discovery (which markets do well) AND externality accounting (which markets do terribly).

### 1.4 Design Goal

Design an ideal economic system architecture that:
- Preserves meritocratic incentives (the gamification that makes self-interested actors productive)
- Distributes automation/productivity gains broadly (not just to capital owners)
- Functions when human labor is no longer needed for production
- Internalizes externalities (pollution, climate damage, resource depletion) into the economic calculus
- Is measurable, deterministic, auditable, and resistant to gaming

---

## 2. Key Design Decisions (Chronological)

### Decision 1: Multi-Dimensional Scoring with Scalar Exchange Medium

**Problem:** A single currency (scalar) cannot encode externality information. A multi-dimensional value vector breaks fungibility and creates barter problems.

**Solution:** Two-layer system. Layer 1 is a fungible exchange medium ("credits") — this is what you buy bread with. Layer 2 is a modifier system that modulates how credits flow to/from each actor. The multi-dimensional score operates *upstream* of transactions, shaping accumulation and drain, but at the point of sale, it's scalar. The bread seller sees credits, not your vector.

**Rationale:** Preserves market efficiency at the transaction layer while embedding externality accounting into the capital flow layer.

### Decision 2: Three-Domain Architecture

**Problem:** Not everything that matters can be quantified. Attempting to put suffering, harm, and rights violations on a continuous scoring gradient creates implicit exchange rates between incommensurable things ("this much pollution = this much human suffering").

**Solution:** Three enforcement domains:
1. **Scoring/Economic Layer** — continuous incentive gradients for measurable externalities (environmental footprint, commons contribution, labor displacement, resource concentration)
2. **Measurable Harm Layer** — binary compliance gates for quantifiable harms (injury rates, labor conditions, animal welfare). Below threshold = blocked from operating. Above threshold = no bonus. Prevents "suffering offsetting."
3. **Legal/Rights Layer** — categorical prohibition for things that should never be optimized around (forced labor, deliberate mass harm). No coefficient, no trade-off.

**Rationale:** The right enforcement mechanism for each category. The system knows where its own formalism breaks down and uses a different mechanism there.

### Decision 3: Deterministic Scoring, ML Only in Measurement

**Problem:** If scoring uses ML, disputes become arguments with a black box. No legitimacy, no auditability, no legal tractability.

**Solution:** Strict separation. ML is permitted only in the measurement layer (sensor interpretation, satellite image classification, supply chain document parsing) because physical reality is messy. The scoring layer is purely deterministic — published formulas, versioned parameters, append-only audit trail. Every score change has a provable causal chain.

**Rationale:** Makes disputes tractable engineering/legal problems rather than philosophical arguments about opaque models. "Your sensor was miscalibrated" and "this formula is unfair" are both adjudicable claims.

### Decision 4: UBI Funded by Automation Dividends

**Problem:** How do you distribute purchasing power when labor is no longer needed?

**Solution:** UBI funded by three streams:
- Labor displacement axis diversions (companies with high revenue-to-employee ratios divert a fraction to UBI pool)
- Resource concentration redirections (progressive drag on extreme wealth flows to UBI pool)
- Environmental modifier deltas (the gap between base transaction value and modified value)

The UBI is *comfortable*, not subsistence. It covers genuine needs. Above UBI, a fully competitive economy operates — you want a mega yacht, you compete.

**Key property: self-balancing.** As automation increases, the LD axis generates more diversion, which increases UBI. The system automatically scales its safety net to the level of automation without requiring legislative adjustment.

### Decision 5: Governance as Separation of Powers

**Problem:** Whoever controls the scoring formulas has enormous power.

**Solution:** Three branches:
- **Legislative:** Sets objectives, approves formula versions, defines thresholds. Democratic input lives here.
- **Executive/Operational:** Independent technical body that runs measurement infrastructure, computes scores, models impact of proposed changes. Insulated from political pressure (like central bank independence).
- **Judicial:** Adjudicates measurement disputes, formula challenges, and wrongful scoring harm claims. Has access to full audit trail.

No single branch can unilaterally change formulas. Changes follow: proposal → impact modeling → public comment → legislative vote → judicial review window → implementation with transition period.

### Decision 6: Simulation Required Before Any Real-World Consideration

**Problem:** This system cannot be validated theoretically. It must be tested with agents operating under these rules to find failure modes, gaming strategies, and unintended consequences.

**Solution:** Build MeaSim — a Java-based agent simulation with a procedurally generated world, AI-powered humanoid robot labor, multiple government configurations, and LLM-powered agent reasoning for complex decisions.

### Decision 7: Hybrid Deterministic/LLM Agent Architecture

**Problem:** Full LLM-driven agents (as in Project Sid) don't scale well — agents fall into loops, costs explode, behavior becomes unpredictable.

**Solution:** Most agent behavior is deterministic utility-function-based. LLM calls are reserved for novel/complex decisions (entrepreneurship, governance, responses to systemic shocks). This is informed by the scaling challenges observed in Altera's Project Sid (1000 agents in Minecraft) and the success of Stanford Smallville's observation/planning/reflection architecture.

Agents use archetypes (12-15 personality templates) to manage LLM costs — agents of the same archetype facing similar conditions can share batched LLM calls.

### Decision 8: Game Master LLM Layer for Innovation

**Problem:** A static economy with fixed production chains just models dividing a fixed pie. The whole argument for meritocratic systems is that they drive innovation that grows the pie.

**Solution:** A separate Game Master LLM (architecturally distinct from agent LLMs) that:
- Adjudicates research outcomes when agents invest in R&D
- Generates novel world elements (new resources, production chains, infrastructure technologies)
- Maintains world coherence through a constitution of physical rules (conservation laws, tech tree progression, balance bounds)

The creative leap is LLM-mediated. Once a technology exists, it enters the deterministic world with fixed, immutable properties. The Game Master uses a more capable model (Opus) than agent reasoning (Sonnet).

### Decision 9: Hexagonal Tile Grid

**Problem:** Square tiles have diagonal distance distortion. Free-form coordinates are too complex for the simulation's needs.

**Solution:** Hexagonal tiles with axial coordinates. Every neighbor is equidistant, which matters because trade cost, pollution diffusion, resource proximity, and infrastructure range are all distance-dependent. Axial coordinates (q, r) store two integers per tile, derive the third cube coordinate when needed.

### Decision 10: Spatial Reasoning Without LLM Navigation

**Problem:** Should agents use LLM calls to navigate the map?

**Solution:** No. Two-layer spatial model:
- **Movement layer:** Fully deterministic. A* pathfinding on hex grid. No LLM.
- **Strategic location layer:** LLM escalation for novel location decisions ("should I relocate my business to be near the new RARE_EARTH deposit?"). The LLM receives a spatial summary, not raw map data. It reasons about strategic implications; the pathfinding engine handles actual movement.

Agents have a perception radius (5-10 hexes) for local information. Beyond that, they rely on market data (delayed by distance) and social information sharing.

### Decision 11: Data-Driven Visual Vocabulary

**Problem:** The Game Master can create new resources and technologies that didn't exist at world generation. How do they appear visually?

**Solution:** A visual vocabulary of geometric primitives: 8 shapes × 20 colors = 160 unique identifiers. New Game Master creations are assigned shape+color from an available pool during registration. The renderer is entirely data-driven — it reads the technology registry and draws what's there. No custom assets needed.

Visualization layers: terrain (base), resources (shape+color icons), structures (rectangles/circles), infrastructure (overlays and connection lines), environment health (color gradient overlay), agents (colored dots by archetype).

---

## 3. Prior Art & References

### Stanford Smallville (2023)
- 25 generative agents in a Sims-like 2D environment
- Architecture: observation → planning → reflection, backed by memory stream
- Key insight: memory stream is essential for believable agent behavior
- Limitation: only 25 agents, didn't test economic systems
- Paper: Park et al., "Generative Agents: Interactive Simulacra of Human Behavior"

### Altera Project Sid (2024)
- Up to 1,000 agents in Minecraft
- Agents spontaneously developed roles, currency (emeralds), governance, religion
- Key insight: agents can self-organize economic and political systems
- Limitation: agents fell into polite agreement loops at scale; required intervention to prevent societal collapse
- Lesson for us: pure LLM agent behavior doesn't scale; validates our hybrid deterministic/LLM approach

### ChatDev (2023)
- Multi-agent role-based software development simulation
- Agents hold roles (CEO, CTO, programmer, tester) and collaborate through structured "chat chains"
- Key insight: decomposing complex tasks into phases with role-specific agents works well
- Lesson for us: structured communication patterns between agents are more reliable than free-form

### Voyager (2023)
- LLM-powered embodied agent in Minecraft with lifelong learning
- Key insight: skill library for storing and reusing learned behaviors
- Lesson for us: agents need persistent memory of what works, informing our memory stream design

---

## 4. Scoring Axis Quick Reference

| Axis | Measures | Modifier Type | Feeds Into |
|------|----------|---------------|------------|
| Environmental Footprint (EF) | Emissions, waste, water, land use per revenue | Continuous coefficient (0.60–1.10) | Environmental Remediation Fund |
| Commons Contribution (CC) | Open-source, research, public goods (by downstream usage) | Continuous coefficient (0.97–1.08) | Commons Investment Fund |
| Labor Displacement (LD) | Revenue per human employee vs. sector median | Diversion rate (0–25%) | UBI Pool |
| Resource Concentration (RC) | Accumulated wealth vs. population median | Continuous coefficient (0.30–1.00) | UBI Pool |
| Economic Productivity (EP) | Market transactions (base value) | No modifier (base for others) | — |

**Transaction formula:**
```
net_credits = base_value × EF_modifier × CC_modifier × RC_modifier × (1 - LD_diversion_rate)
```

---

## 5. Agent Archetypes

| Archetype | Key Traits | What It Tests |
|-----------|-----------|---------------|
| Optimizer | Low altruism, high ambition | System gaming, max accumulation paths |
| Entrepreneur | High risk, high creativity | Business creation, innovation pipeline |
| Cooperator | High altruism, moderate ambition | Commons contribution, community building |
| Free Rider | Low ambition, low compliance | UBI adequacy, minimal participation |
| Regulator | High compliance | Governance participation, formula proposals |
| Innovator | Extreme creativity | Research system, tech tree expansion |
| Accumulator | Max ambition, moderate risk | Resource concentration axis limits |
| Exploiter | Low compliance, high ambition | Scoring loopholes, measurement gaps |
| Artisan | Moderate ambition, quality-focused | Non-maximizing economic behavior |
| Politician | High social engagement | Coalition building, governance capture |
| Philanthropist | High altruism, low personal ambition | Wealth redistribution, public goods |
| Automator | High ambition, tech-focused | Labor displacement dynamics, robot adoption |

---

## 6. Technology Stack

- **Language:** Java 21+ (virtual threads, pattern matching, records)
- **Build:** Gradle with Kotlin DSL
- **Grid system:** Hexagonal tiles, axial coordinates
- **LLM integration:** java.net.http async client, CompletableFuture
- **Agent LLM:** Claude Sonnet (routine escalations), Claude Opus (complex decisions)
- **Game Master LLM:** Claude Opus (world-defining decisions)
- **Serialization:** Jackson (JSON), optional Kryo (binary snapshots)
- **Configuration:** SnakeYAML
- **Metrics:** CSV export via OpenCSV, optional Prometheus
- **Testing:** JUnit 5, Mockito
- **Visualization:** JavaFX (initial), optional web-based renderer later

---

## 7. Document Index

| Document | Purpose | Status |
|----------|---------|--------|
| `DESIGN_CONTEXT.md` (this file) | Decision record, rationale, conversation context | v0.2.0 |
| `MEAS_SYSTEM_SPEC.md` | Full specification of the economic system | v0.2.0 |
| `MEASIM_ARCHITECTURE.md` | Java application architecture for the simulator | v0.2.0 |

All three documents should be provided to Claude Code together when beginning implementation. The Design Context provides the "why" behind every decision. The System Spec provides the "what" — the rules of the economic system. The Architecture Spec provides the "how" — the Java application design.

---

## 8. Implementation Priority

Recommended build order:

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
