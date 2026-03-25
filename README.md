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

Pre-automation UBI is funded by property claim purchases, extraction royalties on public land, and transaction taxes — all flowing to the UBI pool; UBI distributes yearly. UBI is comfortable, not subsistence. It covers housing, food, healthcare, education. Above UBI, a fully competitive economy operates — you want a mega yacht, you compete. The floor is guaranteed; the ceiling is unlimited.

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

### The 5-Layer Architecture

1. **MEAS Protocol** (automatic) — Scoring, modifiers, UBI distribution. Runs every tick. No vote required.
2. **Contracts** (binding) — Wages auto-garnish, trades atomic, either party can terminate. Enforced by the engine.
3. **Property** (first-come-first-served) — Registered tile claims, cost flows to UBI pool.
4. **Governance GM** (periodic) — Yearly MEAS audit, reserve management, public infrastructure approval. Enforces protocol; doesn't make policy.
5. **Emergent** — Agents build courts, police, regulations, insurance as services. Nothing is provided; everything is earned.

The GM operates in two contexts: **Nature GM** (physics, risk, materials, world events — cannot be influenced by agents) and **Governance GM** (reserve management, MEAS audit, property registration, public infrastructure approval — enforces protocol, doesn't make policy).

---

## MeaSim: Testing Before Deploying

You don't deploy a new operating system to production without testing it. MeaSim is a full agent-based simulation that runs hundreds of autonomous agents in a procedurally generated world under MEAS rules, specifically to find where the system breaks.

### What It Simulates

- **Hexagonal tile world** with terrain, resources, environmental health, and pollution diffusion
- **200-500 autonomous agents** across 18 personality archetypes (Optimizer, Entrepreneur, Exploiter, Free Rider, Philanthropist, etc.)
- **Complete MEAS scoring engine** with all five axes and exact spec formulas
- **Proximity-based trade** — no built-in exchange; trade is agent-to-agent or through agent-created marketplace services
- **Robot labor** with configurable automation curves
- **Single MEAS protocol zone** — one set of rules, emergent governance built by agents as services
- **Agent messaging** — private messages (SEND_MESSAGE) and tile-local broadcasts (BROADCAST) within communication range. Conversation-pair interaction: up to 3 exchanges per pair per tick, strict no-double-texting (must wait for reply). Agents can return multiple actions per LLM call (JSON array), enabling rich multi-step turns.
- **Technology discovery** through a Game Master LLM that adjudicates research and maintains world coherence

### The 18 Archetypes: Adversarial Testing by Design

Each agent archetype is designed to stress-test a specific aspect of the system:

| Archetype | Tests |
|-----------|-------|
| **Optimizer** | System gaming, maximum accumulation paths |
| **Entrepreneur** | Business creation, innovation pipeline |
| **Cooperator** | Commons contribution, community building |
| **Free Rider** | UBI adequacy — does the floor work with minimal participation? |
| **Regulator** | Governance service creation, rule enforcement attempts |
| **Innovator** | Research system, tech tree expansion |
| **Accumulator** | Resource concentration limits — does the RC drag work? |
| **Exploiter** | Scoring loopholes, measurement gaps — can you game it? |
| **Artisan** | Non-maximizing economic behavior — is there room for craft? |
| **Politician** | Coalition building, emergent governance capture attempts |
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
- **Tool-grounded decisions**: The GM inspects tiles, agents, markets, infrastructure, and contracts via structured tool calls before making any adjudication — no hallucinated world state

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
│   ├── economy/          Resources, Products, Orders, Transactions
│   ├── scoring/          ScoreVector, ModifierSet, AuditEntry, SectorBaseline
│   ├── agent/            Agent, State, Identity, 18 Archetypes, Memory, Actions
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
│   16 DAOs: World, Agent, Market, Audit, ProductionChain, Metrics, LLM,
│   TechnologyRegistry, Government, Infrastructure, Communication, Risk,
│   Externality, Service, Property, Contract
├── service/              Business logic (interface + impl)
│   ├── world/            Generation, Environment, Pathfinding
│   ├── economy/          Production, CreditFlow (realistic: no fake credit injection)
│   ├── scoring/          MEAS scoring engine (fully deterministic, all 5 axes)
│   ├── agent/            Decision engine (risk-adjusted), Spawning
│   ├── llm/              Claude API client, archetype prompts, response parsing, cost tracking
│   ├── gamemaster/       Full DM: research, infrastructure eval, novel actions, world events, coherence
│   ├── governance/       Governance GM (reserve, audit, infrastructure approval)
│   ├── infrastructure/   Build validation, resource flow, maintenance, GM-dynamic types
│   ├── risk/             Evolution model evaluation, cascade propagation, consequence application
│   ├── externality/      Byproduct processing, true vs measured vs perceived pollution
│   ├── communication/    Agent-to-agent, agent-to-GM, observable thought logging
│   ├── agentservice/     Agent-created services (banking, logistics, etc.)
│   ├── contract/         Work relations, rental, trade agreements
│   ├── property/         Tile claims, purchase, rent, transfer
│   ├── simulation/       Tick loop (12 phases via Guice Multibinder)
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
- **GM has world-inspection tools** — `inspect_tile`, `inspect_agent`, `list_nearby_agents`, `query_market`, `list_infrastructure`, `list_services`, `query_contracts`, `query_property_claims`, `get_world_summary`. It uses multi-turn tool conversations to ground every decision in actual world state rather than relying on context summaries.
- **All GM reasoning is observable** via the communication log
- **Multi-turn conversations** — agents can counter-propose, GM can ask for clarification
- **GM refuses out-of-jurisdiction requests** — it will not facilitate trades, buy/sell goods, or act as a market participant. It is strictly a physics engine and referee.
- **Tech tree consistency** — the GM enforces technology prerequisites for all proposals. No skipping steps; everything must connect to the existing tech tree.

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

### Tick Loop (12 Phases)

| # | Phase | LLM? | What Happens |
|---|-------|------|-------------|
| 1 | Perception | No | Agents observe environment, update risk perceptions from events |
| 2 | Decision | **Tier 1+2** | Deterministic for all agents, then LLM escalation for eligible agents (20-50% per tick) |
| 3 | Action | Partial | Deterministic physics (autoExtract→autoProduce) + LLM-driven actions (multi-action per call, 3 exchanges/pair/tick, no double-texting): trade, messaging, contract negotiation, infrastructure proposals + GM eval |
| 4 | Market | No | Proximity-based trades resolve, MEAS modifiers applied, credits flow |
| 5 | Contracts | No | Wages paid, rent collected, service subscriptions, breach detection |
| 6 | Scoring | No | Score vectors recomputed, modifiers updated, audit trail |
| 7 | UBI | No | Credits distributed yearly from UBI pool to all agents |
| 8 | Governance | Partial | Property registration, public infrastructure approval, yearly MEAS audit |
| 9 | Environment | No | Pollution diffusion, recovery, infrastructure maintenance, externality processing |
| 10 | Risk | Partial | Deterministic probability check → GM adjudicates triggered risks → cascades |
| 11 | Events | **Sonnet/Opus** | GM: research, novel actions, world events (Sonnet), yearly coherence audit (Opus) |
| 12 | Measurement | No | Metrics collected, snapshots saved |

**Model tiering**: Sonnet 4.6 for all routine operations (agent decisions, infrastructure eval, novel actions, world events). Opus 4.6 only for yearly coherence audits — these need holistic world understanding. ~10 Opus calls per 50-year sim. Tool conversations (multi-turn with world inspection) are estimated at 4x single-call cost for budget tracking.

### Property Rights & Labor Market

**Tile claims**: Agents don't own entire tiles — they own **slots** (claims). A grassland tile has 8 capacity slots. Multiple agents can own claims on the same tile. Claims can be purchased, sold, rented, or left empty. Infrastructure and services require a claim to operate on. This creates real location scarcity, rent-seeking, and property markets.

**Contracts**: General-purpose binding agreements between agents. Employment, rental, trade agreements, service subscriptions, partnerships. Standard contracts are between agents (no GM needed). GM involved only for disputes and novel terms. Contracts process each tick: wages paid, rent collected, breaches detected.

**Labor market**: Hiring is agent-negotiated via OFFER_JOB/ACCEPT_JOB. Employer pays wages, employee works. When employer buys robots and fires workers, the LD axis generates UBI diversion. Unemployed agents fall back to UBI. This is how the Labor Displacement axis actually gets tested.

### Running It

```bash
# Build
./gradlew build

# Launch with settings UI (configure agents, world, LLM, then press Start)
# The launcher includes a map preview — generate and inspect the world before starting.
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

Or enter it in the launcher UI's API Key field. Default model: Claude Sonnet 4.6 (`claude-sonnet-4-6`). When API credits run out, the simulation pauses with options to continue (after reloading credits), skip to deterministic-only mode, or leave paused for inspection. A status bar shows LLM state with a resume button and a live cost tracker displaying cumulative API spend.

### Free-Form Agent Actions

Agents route survival operations (extract, produce, move) through the deterministic system — these never touch the GM. Trade decisions (offer, accept, reject, negotiate) go through LLM agent reasoning, where archetype personality drives whether an agent haggles, accepts, or walks away. The `FREE_FORM` action type is reserved for novel combined strategies that cannot be expressed as standard actions:

```json
{"action": "FREE_FORM", "description": "Use my aqueduct's excess capacity to sell water transport to neighboring farmers while running my drill at half capacity to reduce wear", "budget": 200}
```

The Game Master translates this into concrete game mechanics: what resources change, what it costs, what risks and byproducts it creates, what experience domain it exercises. Standard action types (MOVE, EXTRACT, PRODUCE, CLAIM_PROPERTY) handle physics deterministically. Claims require physical proximity (within 2 tiles). Contract negotiation (OFFER_JOB, ACCEPT_JOB, PROPOSE_CONTRACT, ACCEPT_CONTRACT, TERMINATE_CONTRACT, ACCEPT_PROPOSAL, REJECT_PROPOSAL) lets agents negotiate work agreements, rentals, partnerships, and infrastructure quotes. Verbal acceptance detection: saying "I accept" in a message with a pending offer auto-creates the contract.

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

### No Magic Systems: Everything Is Emergent

The simulation provides **nothing for free**. There is no built-in marketplace, no free communication network, no magic logistics. Every system that exists in a real economy must be **built by agents** and **approved by the GM for physical/technological consistency**.

This is the emergent progression:

1. **Day 1**: Agents start with nothing. They can shout to adjacent tiles (range 1-2). They can physically walk to a neighbor and offer a trade. That's it.
2. **An agent might build a market stall** — an infrastructure proposal the GM evaluates. It needs construction materials, labor, and a property claim. If approved, agents within range can come trade there.
3. **An agent might invent a message board** — requiring basic tech and materials. Communication range increases for that tile.
4. **Eventually**: telecommunications, roads, logistics networks — all expanding range and capability. But only if agents build them and the tech tree supports them.

None of these are guaranteed. They're possibilities that emerge (or don't) from agent behavior. If no one builds a marketplace, there is no marketplace. If no one builds roads, trade stays local. The simulation tests whether MEAS incentivizes the creation of these systems.

**The GM's job at every evaluation**: "Does the tech exist for this? Does the infrastructure exist? Is this physically possible given what's been built?" The GM refuses proposals that skip technological prerequisites — no online exchange without internet infrastructure, no phone communication without telecommunications.

### Communication Range

Communication range is dynamic and technology-dependent. Settlement clusters have communal gathering points with +5 communication range, serving as natural meeting spots for trade and coordination. Without infrastructure, agents can only communicate with those on adjacent tiles (shouting distance). Infrastructure extends range — a message board covers a settlement, a postal service reaches further, telecommunications go wider. The GM enforces tech consistency: an agent cannot broadcast across the map without the infrastructure to support it.

Without an API key, all LLM features fall back to deterministic systems. The simulation runs fully either way.

### Subsistence Model

Agents have real survival needs with graduated consequences:

- **Food**: consume 1 FOOD/tick or penalties escalate (mild hunger → serious → starvation)
- **Shelter**: need property claim, rental contract, or settlement zone access
- **Incapacitation**: at satisfaction 0.1, agents enter survival mode — can only extract and trade for food. Creates genuine UBI dependence.
- **Grace period**: first 6 ticks have no penalties while the economy bootstraps
- **Safety valves**: starting credits buy ~300 ticks of food, foraging needs only 1 resource input, settlement zones = shelter

### Commodity Reserve & Construction

Credits are backed by a physical commodity reserve (not pure fiat):

- Reserve holds MINERAL, ENERGY, TIMBER, FOOD_LAND, WATER_RESOURCE
- **Minimum ratio**: 20% (reserve value / credits in circulation). Governance parameter.
- **GM manages yearly** (Opus call): buys/sells commodities, adjusts valuations based on scarcity
- **Agents cannot access the reserve directly** — it's institutional backing (Fort Knox, not a bank)
- Prevents infinite money printing: can't create credits beyond reserve backing
- All reserve transactions numerically logged and auditable

**Reserve Construction Service**: The reserve maintains construction robots that agents can hire at a premium (1.5-3x market rate) to build infrastructure. The GM (Sonnet) decides per-proposal what portion of resources comes from:
1. **Agent's own inventory** (free)
2. **Market purchase** (agent must buy from other agents)
3. **Reserve supply** (premium rate, depletes reserve stock)

The reserve will NOT cover 100% of any resource — agents must participate in the economy. As private "Construction Services" emerge (agents hiring workers to build), reserve robots become the expensive fallback, naturally phasing out. Infrastructure has construction time proportional to complexity (simple = instant, complex = 6+ ticks).

---

## Why This Matters

We're building toward a world where human labor is optional. That's not a dystopia — it's potentially a utopia, if the economic system distributes the gains rather than concentrating them. But the current system won't do that. It's architecturally incapable of it.

MEAS is a proposal. MeaSim is the test. If the simulation shows that MEAS produces broad welfare, functional entrepreneurship, environmental sustainability, and resistance to gaming — under adversarial conditions with agents specifically designed to break it — then it's worth taking seriously.

If it doesn't, we'll see exactly where it fails and fix the formulas.

That's the point. **Economics should be an engineering discipline.** You specify requirements, build a system, test it under load, find the bugs, and ship the fix. We have the tools for this now. We just haven't been using them.

---

## Project Status

All core systems built, wired, and compiling:

- **World**: Hex grid, Perlin noise terrain, 7 terrain types, resource placement, settlement zones, tile history tracking
- **Economy**: Realistic credit flow (no fake injection), proximity-based agent-to-agent trade with LLM-driven negotiation (no built-in exchange — marketplaces emerge from agent-created services), production chains, consumption needs
- **MEAS Scoring**: All 5 axes (EF, CC, LD, RC, EP) with exact spec formulas, deterministic, auditable
- **Agents**: 18 archetypes (Worker 15%, Entrepreneur 6%, Free Rider 7%, etc.), memory streams, risk-adjusted utility decisions, perceived risk model, experience tracking per domain
- **LLM Integration**: Claude API over HTTP/1.1 with a dedicated 100-thread pool (handles 100+ concurrent requests without thread starvation), two-tier decisions (deterministic + LLM escalation), concurrent batching, retry logic (3 retries with backoff), cost tracking
- **Game Master**: Full DM — research, infrastructure evaluation (agent proposes/GM evaluates), novel actions for all archetypes, free-form action resolution, spontaneous world events, tile-specific coherence corrections. Multi-turn tool conversations with world-inspection tools (inspect_tile, inspect_agent, query_market, etc.) ground every decision in actual world state. Refuses out-of-jurisdiction requests (no trading, no market participation). Enforces tech tree prerequisites. Information boundaries. All reasoning observable.
- **Model Tiering**: Sonnet 4.6 for routine operations (including world events), Opus 4.6 only for yearly coherence audits. Tool conversations estimated at 4x single-call cost for budget tracking
- **Infrastructure**: GM-dynamic types, resource flow, maintenance/degradation, construction time. **Two-phase proposals**: BUILD_INFRASTRUCTURE returns a GM quote (cost only — risk/byproducts hidden); agent must ACCEPT_PROPOSAL or REJECT_PROPOSAL before construction begins. **Private** (farms, mines, factories) requires property claim. **Public** (roads, trails) requires governance approval, no property needed. Reserve robots available at premium for construction labor.
- **Risk System**: Universal (all entity types), evolution model (age/usage/maintenance/environment/neighbors), true vs perceived risk, cascading effects
- **Externalities**: Universal byproduct system with true/measured/perceived pollution layers. Hidden externalities go undetected until consequences emerge. Processed every tick, feeds into EF scoring.
- **Services**: Agent-created (banking, logistics, insurance, education, etc.), GM evaluates proposals. Competitive market with reputation. Agents strategically choose which services to consume.
- **Property**: Tile claim system — agents own slots, not tiles. Required for building. Purchase, sell, rent.
- **Contracts**: Work relations (full-time, gig, freelance — weighted labor for LD axis), rental, trade, subscriptions, partnerships. Wages, rent, breach detection. Pending offers carry actual negotiated terms (not hardcoded defaults). Duplicate contract prevention.
- **Labor Market**: Business owners hire unemployed agents. Wages flow. Robot displacement triggers LD axis. Weighted labor count prevents gig economy gaming.
- **Subsistence**: Food + shelter requirements with grace period, graduated consequences, incapacitation at low satisfaction (survival mode)
- **Commodity Reserve**: Physical resource backing for credits, GM-managed yearly, 20% minimum ratio, agents can't access directly
- **Communication**: Observable message log — agent-to-agent, agent-to-GM, GM internal reasoning, multi-turn conversations with information boundaries. Auto-refreshing panel with filter options (All, All Agents, GAME_MASTER, individual agents), Ctrl+F search, auto-scroll toggle, full message detail view. Dynamic communication range (adjacent tiles without phones; infrastructure extends range; GM enforces tech consistency).
- **Governance**: Minimal protocol layer (Governance GM for reserve/audit/infrastructure), emergent services for courts/police/regulation. Single zone, no jurisdictions. Contracts are binding, property is registered.
- **Metrics/Output**: CSV metrics, comprehensive JSON snapshots (agents + infrastructure + services + contracts + property + reserve state + LLM costs + risk events + communication), full communication transcript. Output files (metrics, snapshots, communication log) are cleared on each run.
- **Visualization**: JavaFX hex renderer with agent dots, auto-refreshing dashboard charts, inspector panel, threaded conversation view grouped by chat partner with filter options (All, All Agents, GAME_MASTER, individual agents), Ctrl+F search, auto-scroll toggle, live console with pause/clear/copy

### Documentation

| Document | Contents |
|----------|----------|
| `docs/DESIGN_CONTEXT.md` | Decision record — the *why* behind every design choice |
| `docs/MEAS_SYSTEM_SPEC.md` | Full specification of the economic system |
| `docs/MEASIM_ARCHITECTURE.md` | Java application architecture for the simulator |

---

## License

This project is currently in development. License TBD.
