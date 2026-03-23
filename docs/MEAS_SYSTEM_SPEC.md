# MEAS — Meritocratic Externality-Aware Economic System

## Societal System Specification

**Version:** 0.2.0  
**Date:** March 2026  
**Status:** Draft  

> A specification for a post-labor economic system that preserves meritocratic incentives while internalizing externalities, distributing automation gains, and maintaining market-based exchange.

---

## 1. Executive Summary

This document specifies the Meritocratic Externality-Aware Economic System (MEAS), a replacement economic architecture designed for a world where artificial general intelligence and humanoid robotics have eliminated the necessity of human labor for production.

MEAS addresses two fundamental failures of capitalism:
- The concentration of productivity gains rather than their broad distribution
- The system's dependency on human labor as the primary mechanism for distributing purchasing power

MEAS preserves capitalism's core strength — the alignment of self-interested action with productive output through market-based incentive structures — while introducing a multi-dimensional scoring system that internalizes externalities, funds a Universal Basic Income through automation dividends, and maintains meritocratic competition for those who seek excess.

The system operates across three enforcement domains: a continuous economic scoring layer, a binary compliance harm layer, and a categorical legal prohibition layer.

---

## 2. Design Principles

### 2.1 Core Constraints

The system must satisfy these constraints simultaneously:

- **Incentive preservation:** Self-interested actors must be motivated to create genuine value. The system must gamify meritocracy such that even purely selfish actors contribute to collective welfare as a side effect of pursuing personal gain.
- **Broad distribution:** Productivity gains from automation must flow to the general population, not concentrate among capital owners. The mechanism is structural, not charitable.
- **Labor independence:** The system must remain functional when human labor is no longer required for production. Currency distribution cannot depend on employment.
- **Externality internalization:** Environmental damage, resource depletion, and social costs must be encoded into the economic calculus, not externalized.
- **Determinism and auditability:** All scoring computations must be deterministic, reproducible, and traceable. No machine learning in the policy/scoring logic. Every score change must have a provable causal chain.

### 2.2 Architectural Principles

- **Transparency:** All formulas are published, versioned, and auditable. Every actor can compute their own score and verify it independently.
- **Compositionality:** Scores are built from discrete, independently verifiable components. No single opaque aggregate.
- **Append-only audit trail:** Every coefficient change is logged with timestamp, cause, and source data. The log is immutable.
- **ML boundary:** Machine learning is permitted only in the measurement layer (sensor interpretation, satellite imagery classification, supply chain document parsing). It is strictly prohibited in the scoring/policy layer.
- **Asymmetric application:** Scoring modifiers apply more aggressively at scale. Small actors get wide tolerance bands; large actors face tight coefficients. This encourages experimentation while heavily regulating scaled operations.

---

## 3. Three-Domain Architecture

MEAS enforces societal objectives through three distinct domains, each using a different enforcement mechanism appropriate to the nature of the concern it addresses.

### 3.1 Domain 1: Scoring/Economic Layer

The core economic engine. Handles things that are physically measurable and where continuous incentive gradients are appropriate. This domain modulates the flow of credits (the fungible exchange medium) through deterministic modifier coefficients applied to every economic transaction.

**Enforcement mechanism:** Continuous coefficients that increase or decrease credit flow. Actors are never blocked from operating based on this layer alone; they simply earn more or less depending on their behavior across multiple axes.

**Scope:** Environmental footprint, commons contribution, labor displacement, resource concentration, economic productivity.

### 3.2 Domain 2: Measurable Harm Layer

Handles quantifiable harms that have a categorical moral dimension — things where a continuous gradient is inappropriate because they involve direct harm to people or living systems.

**Enforcement mechanism:** Binary compliance gates. Below a threshold, the actor is blocked from operating until they comply. Above the threshold, no bonus for exceeding the standard. This prevents "suffering offsetting" where an actor earns credits for reducing harm in one area to justify causing harm in another.

**Scope:** Workplace injury rates, supply chain labor conditions (forced labor indicators, minimum working conditions), animal welfare standards in production, direct environmental contamination (toxic spills, water poisoning) as distinct from general footprint.

**Thresholds:** Published, versioned, and subject to the same governance process as scoring formulas. Thresholds are set per industry category to reflect the inherent risk profile of different activities.

### 3.3 Domain 3: Legal/Rights Layer

Handles things that are categorically prohibited regardless of economic benefit. No coefficient, no modifier, no trade-off.

**Enforcement mechanism:** Legal prosecution and punitive consequences. Operates entirely outside the economic mechanism.

**Scope:** Forced labor, deliberate mass environmental destruction, knowingly causing widespread harm, violations of fundamental rights. These are bright lines that cannot be optimized around.

**Rationale:** Not everything that matters can be a number. A well-designed system knows where its own formalism breaks down and uses a different mechanism at that boundary. The legal layer exists precisely for concerns that would be morally corroded by quantification.

---

## 4. Scoring Axes (Domain 1)

Each economic actor (individual, company, or legal entity) carries a score vector across five axes. Each axis produces a deterministic modifier coefficient applied to transactions.

### 4.1 Axis 1: Environmental Footprint (EF)

**What it measures:** Net resource consumption, greenhouse gas emissions, waste generation, water usage, and land-use impact, all normalized against economic output.

**Units:** Physical units (tonnes CO₂-equivalent, litres water, kg waste, hectares land-use-equivalent) per unit of revenue generated. The ratio form ensures larger operations are compared against their own scale.

**Data sources:** IoT sensors on facilities, satellite monitoring of land use and emissions, supply chain transparency records, energy grid metering. The measurement layer uses ML for sensor interpretation and satellite image classification, but outputs structured numerical data.

#### 4.1.1 Modifier Function

Let R = emissions intensity ratio (actual emissions per revenue unit / sector baseline emissions per revenue unit):

```
if R <= 0.5:          modifier = 1.10   (10% bonus)
if 0.5 < R <= 0.8:   modifier = 1.05   (5% bonus)
if 0.8 < R <= 1.0:   modifier = 1.00   (neutral)
if 1.0 < R <= 1.5:   modifier = 0.90   (10% drag)
if 1.5 < R <= 2.0:   modifier = 0.75   (25% drag)
if R > 2.0:           modifier = 0.60   (40% drag)
```

Sector baselines are computed annually from the median performance of all actors in that sector category. This creates a relative standard that tightens over time as the sector improves.

### 4.2 Axis 2: Commons Contribution (CC)

**What it measures:** Value contributed to non-excludable public goods: open-source software, published research, public infrastructure, educational resources, patent releases to public domain.

**Measurement approach:** Downstream usage and dependency rather than raw output. Code is scored by the number of projects that depend on it. Research is scored by citations and derivative works. Infrastructure is scored by utilization. This introduces a time lag but resists gaming better than quantity-based metrics.

**Anti-gaming provision:** A minimum maturity period (e.g., 12 months) before contributions begin accruing score, preventing actors from flooding the commons with junk content for immediate credit.

#### 4.2.1 Modifier Function

Let C = commons contribution score (normalized 0–1 against sector peers):

```
if C >= 0.9:          modifier = 1.08   (8% bonus)
if 0.7 <= C < 0.9:   modifier = 1.04   (4% bonus)
if 0.3 <= C < 0.7:   modifier = 1.00   (neutral)
if C < 0.3:           modifier = 0.97   (3% drag)
```

The drag for low commons contribution is deliberately mild. The system incentivizes contribution without heavily penalizing actors whose business model does not naturally produce public goods.

### 4.3 Axis 3: Labor Displacement (LD)

**What it measures:** The ratio of revenue generated to human employees, used to compute the automation dividend that funds UBI.

**Rationale:** When a company replaces 100 workers with AI/robotics, the productivity surplus should not flow exclusively to shareholders. The labor displacement axis diverts a computed fraction of transaction value to the UBI pool, proportional to how automated the operation is.

#### 4.3.1 Diversion Function

Let A = revenue per human employee (annual, inflation-adjusted). Let B = sector median revenue per human employee. Let D = A / B (displacement ratio):

```
if D <= 1.0:          diversion_rate = 0.00                           (at or below sector norm)
if 1.0 < D <= 2.0:   diversion_rate = 0.02 × (D - 1.0)              (linear 0–2%)
if 2.0 < D <= 5.0:   diversion_rate = 0.02 + 0.03 × (D - 2.0)      (linear 2–11%)
if D > 5.0:           diversion_rate = 0.11 + 0.04 × (D - 5.0)      (linear 11%+, capped at 25%)
```

The diversion rate is applied to each transaction's base value. The diverted credits flow directly to the UBI pool. The actor still captures the majority of gains from automation.

**Self-balancing property:** As automation increases across the economy, sector baselines shift upward, and total diversion volume grows, which increases UBI payments. The system automatically scales UBI to the level of automation without requiring legislative adjustment.

### 4.4 Axis 4: Resource Concentration (RC)

**What it measures:** Accumulated wealth and market control. A continuous progressive function that creates increasing drag on extreme concentration.

**Rationale:** Replaces the blunt instrument of periodic wealth taxes with a continuous coefficient applied to every transaction.

#### 4.4.1 Modifier Function

Let W = actor's net accumulated credits. Let M = population median net credits. Let K = W / M (concentration ratio):

```
if K <= 10:           modifier = 1.00                                    (negligible)
if 10 < K <= 100:     modifier = 1.00 - 0.001 × (K - 10)               (max 9% drag)
if 100 < K <= 1000:   modifier = 0.91 - 0.0005 × (K - 100)             (additional up to 45% drag)
if K > 1000:          modifier = max(0.46 - 0.0001 × (K - 1000), 0.30) (floor at 30%)
```

The floor of 0.30 ensures even the wealthiest actors can still accumulate — the system decelerates, not confiscates. The modifier applies to incoming credits; outgoing spending is unmodified.

### 4.5 Axis 5: Economic Productivity (EP)

**What it measures:** Straightforward value creation as measured by market transactions. This axis carries no modifier of its own — it is the base upon which all other modifiers act.

**Rationale:** Market-based price discovery remains the mechanism for determining what is valued. The scoring system does not replace markets; it adds feedback loops that markets currently lack.

---

## 5. Credit Flow Mechanics

### 5.1 Transaction Formula

For every economic transaction, the net credits received by the seller are:

```
net_credits = base_value × EF_modifier × CC_modifier × RC_modifier × (1 - LD_diversion_rate)
```

Where `base_value` is the market price agreed upon by buyer and seller (standard market dynamics, unmodified).

The difference between `base_value` and `net_credits` is distributed:
- **EF delta** → Environmental Remediation Fund
- **CC delta** → Commons Investment Fund (funds public goods infrastructure)
- **RC delta** → UBI Pool
- **LD diversion** → UBI Pool

### 5.2 UBI Distribution

The UBI pool is funded by three streams: labor displacement diversions, resource concentration redirections, and a base allocation from general economic activity (analogous to a flat transaction tax at a low rate, e.g., 0.5%).

UBI is distributed as plain fungible credits on a regular cycle (monthly):

```
monthly_UBI = total_UBI_pool / eligible_population
```

UBI is designed to cover all genuine needs: housing, food, healthcare, education, transportation. It is comfortable, not subsistence.

**At the point of sale, UBI credits are indistinguishable from earned credits.** The bread seller sees credits. The vector scores are invisible at the transaction layer.

### 5.3 The Competitive Economy Above UBI

UBI provides the floor. Above it, a fully competitive economy operates with market dynamics, entrepreneurship, and differential rewards. Success requires genuine value creation with good externality management — you cannot get wealthy by externalizing costs because the modifier system makes that path economically nonviable.

This preserves the core capitalist incentive for innovation and risk-taking while ensuring competition happens on actual value creation.

---

## 6. Measurement Layer

### 6.1 Separation of Concerns

This is the most critical architectural decision:

- **Measurement layer:** ML permitted. Physical reality is messy. Computer vision for satellite imagery, NLP for supply chain documentation, sensor fusion for environmental monitoring. Outputs are structured, quantified data.
- **Scoring layer:** Purely deterministic. No learned weights. Published formulas with versioned parameters. Disputes become evidentiary or policy questions, both legally adjudicable.

### 6.2 Data Sources

| Axis | Primary Data Sources | Verification Method |
|------|---------------------|-------------------|
| Environmental Footprint | IoT facility sensors, satellite imagery, energy grid data, supply chain records | Independent sensor calibration audits, cross-referencing satellite with ground truth |
| Commons Contribution | Package registries, citation databases, infrastructure utilization APIs, patent databases | Downstream dependency analysis, usage metrics, independent citation verification |
| Labor Displacement | Payroll records, revenue filings, corporate registration data | Cross-reference tax filings, statistical sampling audits |
| Resource Concentration | Asset registries, financial account data, corporate ownership records | Forensic accounting audits, cross-entity ownership tracing |

---

## 7. Governance Model

### 7.1 Legislative Function

Sets axes, approves formula versions, defines Domain 2 thresholds, determines UBI adequacy targets. Democratic input lives here — citizens vote on what the system optimizes for. The legislative body specifies objectives and constraints; the technical body translates these into deterministic functions.

### 7.2 Executive/Operational Function

Independent technical body that runs measurement infrastructure, maintains sensors, operates the computation layer, publishes scores. Insulated from short-term political pressure (like central bank independence). Technical governors serve fixed terms; removable for cause, not for inconvenient scores.

Responsibilities: translating legislative objectives into scoring formulas, operating sensor/measurement network, computing and publishing scores with full audit trails, modeling impact of proposed formula changes, maintaining the append-only audit log.

### 7.3 Judicial Function

Independent courts adjudicating three dispute categories:

1. **Measurement disputes:** "Your sensors say I emitted 500 tonnes; I say the sensor was miscalibrated." Evidentiary questions. Examine raw data, calibration records, independent expert verification.

2. **Formula disputes:** "The modifier function is unfair to my industry." Regulatory challenges. Specialized scoring courts evaluate whether formulas violate fairness principles or create perverse incentives. Because formulas are deterministic, counterfactual analysis is exact.

3. **Wrongful scoring harm:** "My score was wrong for six months; I lost business; I am owed damages." Tort claims. Trace the append-only audit trail to identify exactly when the error was introduced, which transactions were affected, and who is liable.

### 7.4 Formula Modification Process

No single branch can unilaterally change scoring formulas:

1. Legislative body proposes a parameter change
2. Technical body models the impact using current data; analysis is published
3. Public comment period for affected actors
4. Legislative vote with impact analysis as mandatory context
5. Judicial review window for constitutional/rights challenges
6. If unchallenged or upheld, new formula version takes effect with transition period

### 7.5 Constitutional Safeguards

Foundational rights that scoring formulas cannot violate:

- **Due process:** Notification before score changes; opportunity to contest measurements
- **Equal protection:** Formulas apply uniformly; no actor-specific carve-outs
- **Right to audit:** Any actor can request complete computation chain for their score, at no cost
- **Retroactivity prohibition:** Scores cannot be retroactively recomputed beyond 90 days
- **Floor guarantee:** UBI cannot be reduced below adequacy threshold without supermajority + judicial review

---

## 8. Novel Legal Concepts

### 8.1 Algorithmic Standing
Can a class of actors challenge a formula for systematic disadvantage, even if each individual application is correct? Analogous to disparate impact law applied to economic scoring.

### 8.2 Versioning Liability
If formula v2.3 produces unjust outcomes, who is liable? Legislators who approved it? Technical body that implemented it? Explicit liability assignment required.

### 8.3 Measurement Liability Chain
When scoring errors cause harm, liability traces through: sensor hardware manufacturer → sensor operator → measurement ML model → data aggregation pipeline → scoring computation. Each link has defined responsibilities and liability exposure.

---

## 9. Expected System Properties

- **Self-balancing UBI:** Automation and concentration automatically increase UBI funding
- **Race to the top on externalities:** EF modifier creates perpetual pressure toward cleaner operations via sector-relative baselines
- **Preserved entrepreneurship:** UBI eliminates existential risk of failure; competitive economy provides unbounded upside
- **Innovation incentive:** CC axis rewards open innovation with direct financial benefit
- **Corruption resistance:** Deterministic formulas with audit trails resist corruption; gaming is constrained to measurement falsification or governance capture

---

## 10. Open Questions

- **International coordination:** Capital flight risk if implemented in single jurisdiction
- **Transition path:** Migration from current capitalism (separate problem)
- **Commons contribution measurement:** Weakest axis; downstream impact measurement is conceptually sound but practically difficult
- **Governance capture:** Technical body has enormous power; insulation from politics creates technocratic capture risk
- **Simulation validation:** Must be validated through MeaSim before any real-world consideration

---

## 11. Version History

| Version | Date | Description |
|---------|------|-------------|
| 0.1.0 | March 2026 | Initial draft |
| 0.2.0 | March 2026 | Added three-domain rationale for suffering/harm, refined governance safeguards, clarified credit flow at point of sale |
