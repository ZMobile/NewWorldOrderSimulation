# A New World Order: Computation-Driven Societal Architecture

> **What if we treated the economy like a codebase — and actually fixed the bugs?**

This project proposes and simulates a replacement for capitalism. Not a patch. Not a regulation. A new operating system for human civilization, designed from first principles for a world where AI and robotics have made human labor optional.

We call it **MEAS** — the Meritocratic Externality-Aware Economic System. And we built a full agent-based simulator called **MeaSim** to test whether it actually works before anyone tries it in the real world.

---

## The Problem: Capitalism Has a Fatal Bug

Capitalism is the most successful economic system ever deployed. It has a single brilliant design pattern at its core: **incentive alignment through gamification**. Even purely selfish actors are "tricked" into producing value, because the system rewards value creation with capital. This is capitalism's killer feature, and any replacement must preserve it.

But capitalism has two architectural flaws that become fatal as technology advances:

### Bug #1: Productivity Gains Concentrate Instead of Distributing

When a company uses AI to achieve a 10x productivity improvement, the rational response is to lay off 90% of the workforce and capture the surplus as profit. The gains flow to capital owners. The workers who were displaced get nothing. This isn't a moral failure — it's what the system's incentive structure *optimizes for*.

### Bug #2: The System Depends on Labor to Distribute Money

Capitalism uses employment as its primary mechanism for getting purchasing power into people's hands. People work, they get paid, they buy things, demand drives production, cycle continues. But when AI-powered humanoid robots can perform *any* labor more cheaply than humans, this mechanism breaks. Capital flows to robot owners. Workers have no wages. The consumer base collapses. The system crashes — not from external shock, but from its own success at optimization.

These aren't future problems. They're happening now, and they'll accelerate.

---

## The Insight: Economics Is Systems Architecture

Here's the reframing that makes this project possible:

**Every macroeconomic problem is a systems architecture problem.** Wealth inequality is a load balancing failure. Pollution is an unhandled externality — a function with side effects that never got accounted for. Market crashes are cascading failures in a system with no circuit breakers. Poverty in a productive economy is a resource distribution bug.

If you're a software engineer, you already have the mental models to think about these problems. The difference is that previous generations couldn't implement the solutions — they didn't have the computational infrastructure. A system that tracks every economic actor's environmental footprint, adjusts incentives in real-time, and maintains an auditable trail of every scoring decision? That was science fiction in 1950. It's a database and some deterministic functions today.

**This project treats the economy as a system to be architected, not a natural phenomenon to be endured.**

---

## MEAS: The Proposed System

MEAS keeps what capitalism gets right (markets, price discovery, meritocratic incentives) and fixes what it gets wrong (externalities, concentration, labor dependency). Here's how.

### Layer 1: Credits (The Money Part)

At the point of sale, MEAS looks like any market economy. There's a fungible currency ("credits"). You buy bread with credits. The bread seller sees credits. Markets set prices. Supply and demand work normally.

The magic happens *upstream* of transactions.

### Layer 2: The Scoring System (The Fix)

Every economic actor carries a score across five axes. These scores produce **modifier coefficients** that adjust how credits flow:

| Axis | What It Measures | Effect |
|------|-----------------|--------|
| **Environmental Footprint (EF)** | Emissions, waste, water use per revenue | Clean producers earn 10% bonus; dirty ones face up to 40% drag |
| **Commons Contribution (CC)** | Open-source, research, public goods (measured by downstream usage) | Contributors earn up to 8% bonus |
| **Labor Displacement (LD)** | Revenue per human employee vs. sector median | Highly automated operations divert up to 25% to UBI pool |
| **Resource Concentration (RC)** | Accumulated wealth vs. population median | Progressive drag on extreme wealth (floor at 30% — decelerates, never confiscates) |
| **Economic Productivity (EP)** | Market transaction value | The base that other modifiers act on |

**The transaction formula:**
```
net_credits = base_value x EF_modifier x CC_modifier x RC_modifier x (1 - LD_diversion_rate)
```

The difference between what the buyer pays and what the seller receives flows into three funds:
- **Environmental Remediation Fund** (from EF drag)
- **Commons Investment Fund** (from CC drag)
- **UBI Pool** (from RC drag + LD diversion + base transaction tax)

### Layer 3: Universal Basic Income (Funded by Automation)

This is the critical piece. As automation increases, the Labor Displacement axis generates more diversion, which increases UBI payments. **The system automatically scales its safety net to the level of automation without requiring any legislative action.**

UBI is comfortable, not subsistence. It covers housing, food, healthcare, education. Above UBI, a fully competitive economy operates — you want a mega yacht, you compete. The floor is guaranteed; the ceiling is unlimited.

### The Three Enforcement Domains

Not everything should be on a continuous gradient:

1. **Scoring Layer** (continuous) — For measurable externalities. Incentive gradients. The five axes above.
2. **Harm Layer** (binary) — For quantifiable harms like workplace injuries or labor conditions. Below threshold = blocked from operating. Above = no bonus. Prevents "suffering offsetting."
3. **Legal Layer** (categorical) — For things that should never be optimized around. Forced labor, deliberate mass harm. No coefficient. No trade-off. Criminal prosecution.

The system knows where its own formalism breaks down and uses a different enforcement mechanism at that boundary.

### Why This Requires Computation

Previous attempts at alternative economic systems (communism, central planning) failed partly because of the **economic calculation problem** — you can't centrally compute prices for a complex economy. MEAS doesn't try to. Markets still set prices. The scoring system operates *alongside* markets, not instead of them.

But the scoring system itself requires:
- Real-time tracking of environmental metrics across all actors
- Deterministic computation of modifier coefficients
- Append-only audit trails for every score change
- Sector-relative baselines recomputed as industries evolve
- Automatic UBI scaling based on aggregate automation levels

This is the kind of infrastructure that's trivial with modern computing and was impossible without it. **MEAS is the first economic system designed for the computational era.**

### Governance: Separation of Powers Over the Algorithm

Whoever controls the scoring formulas has enormous power. MEAS addresses this with three branches:

- **Legislative**: Sets objectives, approves formula versions, defines thresholds. Democratic input.
- **Technical (Executive)**: Independent body that runs measurements, computes scores, models impact. Insulated from politics like a central bank.
- **Judicial**: Adjudicates measurement disputes, formula challenges, and wrongful scoring claims. Full audit trail access.

No single branch can change the formulas alone. Changes follow: proposal → impact modeling → public comment → vote → judicial review → implementation with transition period.

---

## MeaSim: Testing Before Deploying

You don't deploy a new operating system to production without testing it. MeaSim is a full agent-based simulation that runs hundreds of autonomous agents in a procedurally generated world under MEAS rules, specifically to find where the system breaks.

### What It Simulates

- **Hexagonal tile world** with terrain, resources, environmental health, and pollution diffusion
- **200-500 autonomous agents** across 12 personality archetypes (Optimizer, Entrepreneur, Exploiter, Free Rider, Philanthropist, etc.)
- **Complete MEAS scoring engine** with all five axes and exact spec formulas
- **Order-book markets** with production chains, trade, and credit flow
- **Robot labor** with configurable automation curves
- **Multi-government jurisdictions** with different MEAS parameters, enabling direct policy comparison
- **Agent migration** between jurisdictions based on satisfaction
- **Technology discovery** through a Game Master LLM that adjudicates research and maintains world coherence

### The 12 Archetypes: Adversarial Testing by Design

Each agent archetype is designed to stress-test a specific aspect of the system:

| Archetype | Tests |
|-----------|-------|
| **Optimizer** | System gaming, maximum accumulation paths |
| **Entrepreneur** | Business creation, innovation pipeline |
| **Cooperator** | Commons contribution, community building |
| **Free Rider** | UBI adequacy — does the floor work with minimal participation? |
| **Regulator** | Governance participation, formula proposals |
| **Innovator** | Research system, tech tree expansion |
| **Accumulator** | Resource concentration limits — does the RC drag work? |
| **Exploiter** | Scoring loopholes, measurement gaps — can you game it? |
| **Artisan** | Non-maximizing economic behavior — is there room for craft? |
| **Politician** | Coalition building, governance capture attempts |
| **Philanthropist** | Wealth redistribution, public goods funding |
| **Automator** | Labor displacement dynamics, robot adoption curves |
| **Worker** | Stable work relations, fair wages, labor market dynamics |
| **Speculator** | Asset trading, property flipping, price exploitation |
| **Homesteader** | Self-sufficiency, minimal market participation |
| **Provider** | Service creation and operation (banking, logistics, etc.) |
| **Landlord** | Property acquisition, rent-seeking — does RC axis counter this? |
| **Organizer** | Multi-agent alliances, collective bargaining, coordination |

The distribution mirrors reality: ~15% Workers (largest group), ~7% Free Riders, ~6% each for Entrepreneurs/Providers/Artisans, smaller shares for specialized roles. This tests MEAS against a realistic population, not an entrepreneurship fantasy.

If the Exploiter finds a loophole, that's a bug to fix. If the Free Rider starves, UBI is miscalibrated. If the Accumulator breaks through the RC ceiling, the formula needs adjustment. If the Worker can't find stable employment, the labor market isn't functioning. If the Landlord monopolizes property, the RC axis needs tuning.

### Agent Design Philosophy

Agents are **rational strategic players in a complex game**, not simulated humans with fake emotions. LLMs cannot accurately simulate human emotion, and pretending they can would introduce a known inaccuracy that undermines the simulation's scientific value.

What agents DO have:
- **Strategic reasoning** based on archetype disposition and game state
- **Satisfaction** as a rational assessment: "my material conditions meet/don't meet my objectives" — not a feeling, but a measurable evaluation of outcomes vs expectations
- **Opinions** about other agents based on observed behavior (an agent that defaulted on a contract is rationally assessed as unreliable)
- **Preferences** driven by archetype (a Worker prefers stable income; a Speculator prefers volatile markets)
- **Specialization advantage** — the GM treats experienced agents more favorably (better risk profiles, better evaluations)

What agents DON'T have:
- Simulated emotions (joy, anger, love, fear)
- Psychological states that LLMs can't genuinely model
- Irrational behavior (agents play optimally for their archetype)

The simulation measures **material outcomes** (Gini, food access, environmental health, economic mobility). Humans judge whether those outcomes represent a society they'd want to live in. The agents test the system; they don't evaluate it.

### The Game Master: An AI Dungeon Master

MeaSim includes a Game Master LLM (Claude) that acts as the simulation's "Dungeon Master." It doesn't just adjudicate research — it maintains the entire world's narrative and mechanical coherence:

- **Spontaneous world events**: Environmental disasters when health drops, market booms in healthy economies, social unrest when satisfaction is low, resource discoveries
- **Novel agent actions**: Every archetype can attempt things outside the deterministic rules — the Artisan creates unique products, the Politician builds coalitions, the Exploiter tries novel gaming strategies. The GM adjudicates outcomes.
- **World coherence audits**: Catches inconsistencies (e.g., high inequality but high satisfaction is incoherent) and issues corrections
- **Research adjudication**: When agents invest in R&D, the GM determines what they discover within physical constraints (conservation laws, tech prerequisites, balance bounds)

When no LLM API key is configured, everything falls back to deterministic probability-based systems — the simulation runs fully without external dependencies.

### What We Measure

Every tick, MeaSim computes:
- **Gini coefficient** (wealth inequality — target: 0.25-0.40)
- **Environmental health** (average tile quality — target: >0.70)
- **Satisfaction distribution** (mean and spread)
- **UBI adequacy** (fraction of agents whose needs are met)
- **Entrepreneurship rate** (new businesses per 1000 agents)
- **Innovation rate** (tech tree growth)
- **Gaming incidents** (detected exploit attempts)

Every scenario runs in pairs: **MEAS enabled vs. baseline capitalism** with identical world seeds and agent populations. The differential tells us exactly what the scoring system adds.

---

## Technical Architecture

```
Java 21+ | Gradle | Guice DI | JavaFX | Claude API
```

Layered architecture: **model → dao → service → ui**

```
com.measim
├── model/                Pure data classes
│   ├── world/            HexCoord, HexGrid, Tile, Terrain, Environment
│   ├── economy/          Resources, Products, Orders, Transactions, OrderBook
│   ├── scoring/          ScoreVector, ModifierSet, AuditEntry, SectorBaseline
│   ├── agent/            Agent, State, Identity, 12 Archetypes, Memory, Actions
│   ├── gamemaster/       Discoveries, TechTree, WorldEvents, NovelActions, InfrastructureProposals
│   ├── governance/       Government, Proposals, Disputes
│   ├── infrastructure/   Infrastructure, InfrastructureType, Effects, Constraints
│   ├── risk/             Risk, RiskProfile, RiskEvolutionModel, PerceivedRisk, RiskEvent
│   ├── communication/    Message, Conversation (observable agent/GM dialogue)
│   ├── llm/              LlmRequest, LlmResponse
│   ├── robot/            RobotUnit
│   ├── event/            EventBus
│   └── config/           SimulationConfig (YAML)
├── dao/                  Data access (interface + impl, Guice-injected)
│   13 DAOs: World, Agent, Market, Audit, ProductionChain, Metrics, LLM,
│   TechnologyRegistry, Government, Infrastructure, Communication, Risk, Externality
├── service/              Business logic (interface + impl)
│   ├── world/            Generation, Environment, Pathfinding
│   ├── economy/          Production, CreditFlow (realistic: no fake credit injection)
│   ├── scoring/          MEAS scoring engine (fully deterministic, all 5 axes)
│   ├── agent/            Decision engine (risk-adjusted), Spawning
│   ├── llm/              Claude API client, archetype prompts, response parsing, cost tracking
│   ├── gamemaster/       Full DM: research, infrastructure eval, novel actions, world events, coherence
│   ├── governance/       Voting, judicial disputes, migration
│   ├── infrastructure/   Build validation, resource flow, maintenance, GM-dynamic types
│   ├── risk/             Evolution model evaluation, cascade propagation, consequence application
│   ├── externality/      Byproduct processing, true vs measured vs perceived pollution
│   ├── communication/    Agent-to-agent, agent-to-GM, observable thought logging
│   ├── simulation/       Tick loop (11 phases via Guice Multibinder)
│   ├── metrics/          Gini, satisfaction, CSV export
│   ├── snapshot/         JSON state persistence
│   └── comparison/       MEAS vs baseline differential analysis
└── ui/
    ├── cli/              Main entry point, Guice bootstrap
    └── fx/               JavaFX hex renderer, dashboard, inspector
```

### The Game Master as Physics Engine

The GM is the simulation's DM. Crucially:
- **Agents propose solutions** (creative agency belongs to agents)
- **GM evaluates feasibility** and sets numerical properties (costs, capacity, risks)
- **GM never invents solutions** for agents — it only determines reality
- **All GM reasoning is observable** via the communication log
- **Multi-turn conversations** — agents can counter-propose, GM can ask for clarification

Everything the GM creates comes with a **risk profile**: what could go wrong, how probability evolves over time, and what cascading effects a failure would have.

### The Risk System

Risk is a universal property — not just infrastructure, but resources, agents, tiles, production chains, markets.

**Two layers of risk knowledge:**
1. **True risk** — set by the GM, used by the engine. Agents don't see this.
2. **Perceived risk** — what agents THINK the risks are. Built from observation, experience, personality bias.

The gap between perceived and true risk drives behavior: overconfident agents take on too much risk and get burned. Cautious agents miss opportunities. Information sharing between agents can improve or distort perceptions.

**Risk evolution model** — GM sets parameters, engine evaluates deterministically:
```
P(tick) = base × age(t) × usage(intensity) × maintenance(gap) × environment(health) × neighbor(load)
```
Zero LLM calls for probability. GM only called when a risk triggers (to adjudicate specific consequences).

### The Externality System

Byproducts/externalities are a separate system from risk (risk = what could go wrong; externalities = what IS being produced, often invisibly).

**Three layers of knowledge:**
1. **True byproducts** — what actually happens. Applied to tiles/environment. Some are visible (smoke), some are hidden (groundwater contamination), some are cumulative (microplastics).
2. **Measured byproducts** — what the measurement system can detect. This is what feeds into EF scoring. Hidden byproducts may go unmeasured for years.
3. **Perceived byproducts** — what agents think. They may not know about hidden externalities until consequences emerge.

The gap between true and measured is where the system can be gamed — an Exploiter might produce hidden pollution that doesn't affect their EF score. Until the accumulated damage crosses a detection threshold or causes visible consequences (crop failures, health impacts), they get away with it. This is realistic: it's exactly how real environmental regulation works.

Byproduct types: air pollution, water contamination, soil degradation, noise, waste, radiation, chemical, thermal, ecological, social, custom (GM-defined). Each has a visibility class, evolution model, diffusion radius, and accumulation rate.

### Tile History & GM Corrections

Every tile tracks its history: cumulative infrastructure ticks, agent visits, production activity, pollution received, risk events, idle time. The GM uses this during:

- **Coherence audits** — "This tile has been industrialized for 50 ticks, soil should be degraded" → GM issues tile-specific environment corrections
- **World events** — notable tiles (highest activity, worst environment, most risk events) are included in the GM's context so events target appropriate locations
- **Infrastructure evaluation** — the GM knows the tile's history when evaluating proposals ("building here is risky given 3 prior failures")

Corrections modify tile environment directly: soil quality, air quality, water quality, biodiversity — with specific deltas per tile.

### Agent-Created Services

Services are first-class entities — not hardcoded, not predefined. Agents create them:

- An **Accumulator** proposes "I want to offer lending — agents deposit credits, I lend at interest" → GM evaluates → bank exists
- An **Entrepreneur** proposes "Logistics service using my trade road infrastructure" → GM evaluates → shipping company
- A **Cooperator** proposes "Mutual insurance pool — members contribute, payouts on risk events" → GM evaluates → insurance co-op
- An **Innovator** proposes "Training academy — improves agents' skill scores" → GM evaluates → education service

This means **banking, insurance, logistics, education, and even governance services all emerge from agent creativity** rather than being hardcoded system features. The competitive market applies to services just like it does to physical goods.

Each service has:
- GM-set properties (setup cost, operating cost, price, capacity, quality)
- Effects on consumers (credit lending, satisfaction boost, skill improvement, risk reduction)
- Risk profiles (bank run, malpractice, insolvency)
- Byproduct profiles (data collection, social displacement)
- Reputation that builds with successful use and decays without maintenance
- Infrastructure dependencies (a logistics service needs roads)

Service categories: financial, logistics, healthcare, education, legal, security, information, entertainment, maintenance, governance, custom.

### Tick Loop (11 Phases)

| # | Phase | LLM? | What Happens |
|---|-------|------|-------------|
| 1 | Perception | No | Agents observe environment, update risk perceptions from events |
| 2 | Decision | No | Deterministic utility calculator, risk-adjusted |
| 3 | Action | Partial | Economic pipeline (extract→produce→sell→buy) + strategic action + GM infrastructure eval |
| 4 | Market | No | Order books match, MEAS modifiers applied, credits flow |
| 5 | Scoring | No | Score vectors recomputed, modifiers updated, audit trail |
| 6 | UBI | No | Credits distributed from pool to all agents |
| 7 | Governance | Partial | Proposals, votes, disputes |
| 8 | Environment | No | Pollution diffusion, recovery, infrastructure maintenance |
| 9 | Risk | Partial | Deterministic probability check → GM adjudicates triggered risks → cascades |
| 10 | Events | Partial | GM: research results, novel actions, spontaneous world events, coherence audit |
| 11 | Measurement | No | Metrics collected, snapshots saved |

### Property Rights & Labor Market

**Tile claims**: Agents don't own entire tiles — they own **slots** (claims). A grassland tile has 8 capacity slots. Multiple agents can own claims on the same tile. Claims can be purchased, sold, rented, or left empty. Infrastructure and services require a claim to operate on. This creates real location scarcity, rent-seeking, and property markets.

**Contracts**: General-purpose binding agreements between agents. Employment, rental, trade agreements, service subscriptions, partnerships. Standard contracts are between agents (no GM needed). GM involved only for disputes and novel terms. Contracts process each tick: wages paid, rent collected, breaches detected.

**Labor market**: Agents hire other agents via employment contracts. Employer pays wages, employee works. When employer buys robots and fires workers, the LD axis generates UBI diversion. Unemployed agents fall back to UBI. This is how the Labor Displacement axis actually gets tested.

### Running It

```bash
# Build
./gradlew build

# Launch with settings UI (configure agents, world, LLM, then press Start)
java -jar build/libs/measim-0.1.0-all.jar --visualize

# Quick-start with visualizer (skip settings, use config file)
java -jar build/libs/measim-0.1.0-all.jar --visualize --quick --config config/default.yaml

# Headless (console output + CSV metrics, no UI)
java -jar build/libs/measim-0.1.0-all.jar --config config/default.yaml

# Comparison mode (MEAS vs baseline capitalism)
java -jar build/libs/measim-0.1.0-all.jar --compare --config config/default.yaml
```

On Windows with Java 21 not on PATH:
```powershell
& "C:\Program Files\Java\jdk-21\bin\java.exe" -jar build\libs\measim-0.1.0-all.jar --visualize
```

### LLM Configuration

Set your Anthropic API key via environment variable (never put keys in code/config files):
```bash
# Linux/Mac
export ANTHROPIC_API_KEY=your-key-here

# Windows PowerShell
$env:ANTHROPIC_API_KEY = "your-key-here"
```

Or enter it in the launcher UI's API Key field. Default model: Claude Sonnet 4.6 (`claude-sonnet-4-6`).

### Free-Form Agent Actions

When LLM is enabled, agents aren't limited to hardcoded action types. They can describe creative, novel, or combined strategies in natural language:

```json
{"action": "FREE_FORM", "description": "Use my aqueduct's excess capacity to sell water transport to neighboring farmers while running my drill at half capacity to reduce wear", "budget": 200}
```

The Game Master translates this into concrete game mechanics: what resources change, what it costs, what risks and byproducts it creates, what experience domain it exercises. Standard action types (MOVE, BUY, SELL, PRODUCE) remain for routine operations.

### Agent Experience & Specialization

Agents build experience through doing. An agent who has been mining for 50 ticks gets better GM evaluations for mining-related proposals (lower cost, lower risk, higher capacity). Experience is tracked per domain (extraction, production:farming, infrastructure, research:chemistry, service:FINANCIAL, etc.) and passed to the GM in every evaluation.

The GM never tells agents their experience stats — agents know what they remember, not numbers. Specialization advantage emerges from play.

### Multi-Turn GM Conversations

Agent-GM interactions can involve clarification rounds:
1. Agent proposes → GM evaluates
2. If GM needs more info → asks clarification question (about plans/materials only, NEVER reveals hidden info)
3. Agent responds with what they'd naturally know
4. GM gives final evaluation

The GM has strict information boundaries: it never reveals true risk profiles, hidden byproducts, other agents' information, or its internal reasoning to agents.

Or in `config/default.yaml`:
```yaml
llm:
  apiKey: "your-key-here"
```

Without an API key, all LLM features fall back to deterministic systems. The simulation runs fully either way.

---

## Why This Matters

We're building toward a world where human labor is optional. That's not a dystopia — it's potentially a utopia, if the economic system distributes the gains rather than concentrating them. But the current system won't do that. It's architecturally incapable of it.

MEAS is a proposal. MeaSim is the test. If the simulation shows that MEAS produces broad welfare, functional entrepreneurship, environmental sustainability, and resistance to gaming — under adversarial conditions with agents specifically designed to break it — then it's worth taking seriously.

If it doesn't, we'll see exactly where it fails and fix the formulas.

That's the point. **Economics should be an engineering discipline.** You specify requirements, build a system, test it under load, find the bugs, and ship the fix. We have the tools for this now. We just haven't been using them.

---

## Project Status

All core systems built and compiling (BUILD SUCCESSFUL, 35 tests):

- **World**: Hex grid, Perlin noise terrain, 7 terrain types, resource placement, settlement zones
- **Economy**: Realistic trade-driven credit flow (no fake injection), order book markets, production chains, consumption needs
- **MEAS Scoring**: All 5 axes (EF, CC, LD, RC, EP) with exact spec formulas, deterministic, auditable
- **Agents**: 12 archetypes, memory streams, risk-adjusted utility decisions, perceived risk model
- **LLM Integration**: Claude API client, archetype prompts with risk profiles, cost tracking, caching
- **Game Master**: Full DM — research, infrastructure evaluation (agent proposes/GM evaluates), novel actions for all archetypes, spontaneous world events, yearly coherence audits. All reasoning observable.
- **Infrastructure**: GM-dynamic types (no fixed catalog), resource flow across tiles, maintenance/degradation, terrain capacity constraints, stacking diminishing returns
- **Risk System**: Universal (all entity types), evolution model with GM-set parameters, true vs perceived risk, cascading effects, probability evolves with age/usage/maintenance/environment/neighbors
- **Externalities**: Universal byproduct system with true/measured/perceived pollution layers. Hidden externalities go undetected until consequences emerge. Feeds into EF scoring.
- **Services**: Agent-created services (banking, logistics, insurance, education, etc.) — not hardcoded. GM evaluates proposals, sets properties. Competitive service market with reputation system.
- **Property**: Tile claim system — agents own slots, not tiles. Purchase, sell, rent. Location scarcity drives property markets.
- **Contracts**: Employment, rental, trade, subscriptions, partnerships. Wages, rent, breach detection, termination.
- **Labor Market**: Agents hire each other. Wages flow. Robot displacement triggers LD axis. Unemployment → UBI fallback.
- **Communication**: Observable message log — agent-to-agent, agent-to-GM, GM internal reasoning, multi-turn conversations
- **Governance**: Multi-government, voting lifecycle, judicial disputes, agent migration
- **Metrics/Output**: Gini, satisfaction, env health — CSV export, JSON snapshots, comparison framework
- **Visualization**: JavaFX hex renderer, dashboard charts, inspector panel

### Documentation

| Document | Contents |
|----------|----------|
| `docs/DESIGN_CONTEXT.md` | Decision record — the *why* behind every design choice |
| `docs/MEAS_SYSTEM_SPEC.md` | Full specification of the economic system |
| `docs/MEASIM_ARCHITECTURE.md` | Java application architecture for the simulator |

---

## License

This project is currently in development. License TBD.
