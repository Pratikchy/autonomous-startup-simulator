# Autonomous Virtual Startup Simulator
## Project Overview & System Specification

**Course/Assessment:** Continuous Assessment 3 (CA3)
**Project Domain:** Multi-Agent Systems, Autonomous Agentic AI & Full-Stack Simulation
**Repository Architecture:** Decoupled Client-Server (Spring Boot Backend + React/Three.js Frontend)

---

## 1. Project Title
**Autonomous Virtual Startup Simulator:** A Multi-Agent AI Platform for Dynamic Business Ideation, Strategic Boardroom Debate, and Autonomous 90-Day Execution Simulation.

---

## 2. Abstract
The **Autonomous Virtual Startup Simulator** is an advanced multi-agent software platform that simulates how an early-stage startup is analyzed, planned, debated, and executed. Given a single raw business idea entered by a founder, four specialized artificial intelligence agents—representing the **CEO**, **Head of Development**, **Head of Marketing**, and **Head of Finance**—collaboratively analyze the idea, communicate through targeted inboxes, engage in structured boardroom debates, and synthesize a comprehensive technical and financial startup blueprint.

Following planning, the system transitions into an **Autonomous 90-Day Execution Engine** that models day-by-day product development, marketing rollout, financial burn rate, and milestone completion. The entire simulation runs independently of the web browser on a resilient Java Spring Boot backend, persists every event and decision to a relational database, and renders agent reasoning and physical office presence in real time via a 3D isometric virtual office interface.

---

## 3. Problem Statement
Traditional startup planning and software engineering management suffer from critical structural limitations:
1. **Static, Disconnected Tools:** Founders and software teams rely on static templates, fragmented spreadsheets, or linear Gantt charts that do not interact with one another or react dynamically to blockers.
2. **Generic, Single-Prompt AI Limitations:** While modern Large Language Models (LLMs) provide useful answers, querying a single general-purpose AI model yields surface-level advice. It lacks department-specific specialization, inter-agent checks and balances, and the tension between aggressive technical ambitions and strict financial runway constraints.
3. **Absence of Memory and Dynamic Orchestration:** Most AI chatbots do not maintain persistent memory across sessions or adapt their workflows based on execution feedback. When a task fails or dependencies become blocked, typical tools freeze or require manual human intervention.
4. **Browser-Coupled Web Execution:** Many web demonstrations lose their state or cancel running operations when the user reloads the browser, navigates away, or closes the tab.

There is a clear need for an integrated, persistent, multi-agent platform capable of evaluating ideas through diverse departmental viewpoints, debating strategic trade-offs, and autonomously carrying out execution tasks with built-in fault tolerance and replanning.

---

## 4. Motivation
In professional software development and entrepreneurial ventures, success depends on cross-functional collaboration. A development lead cannot build an enterprise feature without knowing the server budget from finance, and marketing cannot launch a customer acquisition campaign before an MVP prototype is available.

The motivation behind this project is to study and implement cutting-edge **Autonomous Multi-Agent Architectures**:
* **Specialized Agent Delegation:** Demonstrating how dividing a complex domain into specialized roles produces substantially higher quality reasoning than a monolithic prompt.
* **Checks and Balances:** Simulating authentic boardroom debates where the Finance agent challenges unrealistic marketing spend or development scope before commitments are made.
* **Autonomous Resilience:** Moving beyond simple proof-of-concept demos by building a robust system with real database persistence, restart recovery, tool invocation, and dynamic task replanning.
* **Engaging Real-Time Visualization:** Transforming invisible AI reasoning into an intuitive, observable experience using 3D spatial simulation and live Server-Sent Events (SSE).

---

## 5. Project Objectives
The primary technical and functional objectives of this project are:

1. **Autonomous Multi-Agent Reasoning:** Implement four autonomous agents (CEO, Development, Marketing, Finance) that analyze business concepts using structured LLM prompts and typed JSON schemas.
2. **Addressed Inter-Agent Communication:** Enable agents to send targeted, addressable messages to each other's inboxes to challenge assumptions, ask clarifying questions, and exchange requirements.
3. **Dynamic Orchestration (Non-Fixed Sequence):** Implement an LLM-driven orchestrator that evaluates the global state snapshot after every action to decide the optimal next step (`RUN_AGENT`, `START_DEBATE`, or `COMPLETE_ANALYSIS`) rather than adhering to a rigid pipeline.
4. **Persistent Private & Shared Memory:** Build a persistent memory subsystem where agents store private observations and shared company milestones that persist across server restarts and inform future decisions.
5. **Agent-Driven Tool Selection:** Equip agents with domain tools (`financial_calculator`, `development_effort_estimator`, `pricing_revenue_calculator`) that agents discover and execute dynamically.
6. **Autonomous 90-Day Execution Engine:** Build a backend-driven execution worker that advances simulated days, resolves task dependencies, models budget burn rate, and recalculates health metrics without requiring browser interaction.
7. **Adaptive Execution & Task Graph Replanning:** When an execution blocker or risk is diagnosed, the system dynamically validates and applies task graph mutations (adding tasks, deferring work, modifying dependencies) while preventing dependency cycles.
8. **Relational Persistence & Disaster Recovery:** Guarantee that every startup, task, agent state, and debate message is stored in a relational database (H2/PostgreSQL), and automatically resume interrupted workflows after a backend reboot.
9. **Real-Time 3D Office Interface:** Provide an interactive Three.js 3D isometric office where virtual employees animate, display speech bubbles, show status monitors, and reflect live backend state.

---

## 6. Key Features

### A. Instant Startup Creation & Persistent Identity
* Founders submit a startup idea and optional name.
* The startup is immediately persisted to the database with a stable unique ID before AI analysis begins, ensuring work is never lost.

### B. Departmental AI Analysis
* **CEO Strategy:** Evaluates problem-solution fit, identifies target demographics, defines value propositions, formulates hypotheses, and specifies initial MVP direction.
* **Technical Blueprint:** Selects tech stacks (e.g., React Native, Node.js, PostgreSQL), designs architecture diagrams, outlines core MVP features, and identifies technical risks.
* **Go-To-Market Plan:** Defines user acquisition channels, messaging frameworks, competitive advantages, and launch funnels.
* **Financial Model:** Computes initial budget allocation, estimates monthly burn rate, calculates runway in months, and defines pricing tiers with CAC and LTV assumptions.

### C. Multi-Turn Boardroom Debate
* A structured 3-round debate protocol:
  1. **Position Round:** Agents present initial departmental stances.
  2. **Challenge Round:** Agents cross-examine peer plans and point out blind spots (e.g., Finance challenging aggressive hiring).
  3. **Convergence Round:** Agents revise recommendations based on peer critique.
* **CEO Synthesis:** The CEO reviews debate findings, resolves conflicts, and issues binding strategic decisions.

### D. Comprehensive Startup Blueprint
* Consolidates executive summary, technical architecture, marketing strategy, budget allocations, and an actionable 90-day task roadmap into an interactive dashboard.

### E. Autonomous 90-Day Execution Engine
* Driven by a background worker loop on the server (`simulationScheduler`), advancing simulated calendar days at a steady cadence.
* Tracks 14+ execution tasks with explicit topological dependencies, assigned agents, and actual vs. estimated days.
* Dynamically calculates startup health metrics: Overall Progress, MVP Progress, Technical Feasibility, Market Readiness, Financial Health, and Remaining Budget.

### F. Explicit Blocker Resolution & Adaptive Replanning
* Calendar-injected and dependency-driven blockers pause affected tasks.
* Blocker resolution strictly verifies prerequisites before allowing work to resume.
* When necessary, the `ReplanEngine` proposes and validates structural task graph changes without creating dependency deadlocks.

### G. First-Class Queryable Execution Trace
* Full audit trail accessible at `GET /api/startups/{id}/trace`.
* Records every prompt, decision, tool invocation, message, and task state change in chronological order for complete evaluator transparency.

### H. Browser-Independent Execution & Automatic Restart Recovery
* Closing, reloading, or navigating away from the web page has zero impact on active execution.
* On backend restart, the `StartupExecutionRecoveryService` detects previously interrupted startups and safely resumes unfinished workflows from their last database checkpoint.

---

## 7. The Four AI Agents and Their Responsibilities

| Agent Name | Identity / Persona | Primary Responsibilities | Core Outputs |
| :--- | :--- | :--- | :--- |
| **CEO Agent** | **Ava** (Alex Vance) | Chief Executive Officer. Sets overall business vision, ensures team alignment, enforces strategic constraints, moderates debates, and conducts final synthesis. | Problem statement, value proposition, business model, executive summary, debate synthesis. |
| **Development Agent** | **Devin** (Dev Lead / Backend Eng) | Head of Engineering. Evaluates technical feasibility, selects system architecture and tech stacks, breaks down MVP features, estimates developer-months, and identifies infrastructure risks. | Technical architecture, core feature list, tech stack specification, development effort estimates, engineering task execution. |
| **Marketing Agent** | **Mara** (Marketing Lead / Growth Lead) | Head of Marketing & Growth. Defines target user personas, researches competitors, designs acquisition funnels, models customer acquisition cost (CAC), and prepares launch campaigns. | Target persona breakdown, channel strategy, positioning statement, launch campaign timeline. |
| **Finance Agent** | **Finn** (Finance Dir / Controller) | Chief Financial Officer. Enforces financial discipline, models capital allocation, calculates burn rate and runway in months, validates unit economics, and flags cashflow risks. | Budget allocation matrix, monthly burn rate, runway calculations, pricing tiers, unit economics (LTV/CAC ratio). |

---

## 8. Technologies Used

### Backend Architecture
* **Java 25 / OpenJDK 21:** High-performance, strongly typed object-oriented foundation.
* **Spring Boot 3.5.4:** Enterprise web framework providing dependency injection, task scheduling, security, and REST controllers.
* **Spring Data JPA & Hibernate:** Object-Relational Mapping (ORM) connecting domain models to relational tables.
* **H2 Database Engine:** File-based relational database (`jdbc:h2:file:./data/startupsim`) delivering zero-config persistence.
* **PostgreSQL:** Production driver included for scalable cloud database deployment.
* **Server-Sent Events (SSE):** Spring `SseEmitter` streaming low-latency real-time simulation events to connected clients.
* **Apache Maven:** Project build, dependency management, and automated test execution.

### Frontend Architecture
* **React 18.3.1:** Component-based user interface library.
* **TypeScript 5.6.3:** Type-safe frontend codebase enforcing strict contract alignment with backend DTOs.
* **Vite 5.4.11:** Next-generation frontend build tooling offering instantaneous Hot Module Replacement (HMR).
* **Zustand 4.5.5:** Lightweight, reactive centralized state store managing frontend simulation snapshots and real-time event updates.
* **React Router 7.18.4:** Client-side URL routing (`/` for landing page, `/startups/:id` for specific simulations).
* **Three.js & React Three Fiber (R3F):** WebGL-based 3D scene rendering the isometric virtual startup office.
* **Tailwind CSS 3.4.15:** Utility-first CSS framework providing a sleek, dark-mode glassmorphic user interface.

### AI & LLM Integration Modes
* **`REAL` Mode:** Integrates with real LLM endpoints (OpenRouter, OpenAI GPT-4o, Google Gemini) using JSON schema validation to produce authentic, dynamic business reasoning.
* **`SCRIPTED_DEMO` Mode:** A 100% offline, deterministic simulation mode executing predefined reasoning paths without external API calls—ideal for offline grading, instant testing, and presentation safety.

---

## 9. Scope and Limitations

### In Scope
* End-to-end multi-agent business simulation from idea formulation to 90-day execution completion.
* Genuine multi-agent collaboration with inter-agent messaging, tool invocation, and memory.
* Dynamic orchestration where the sequence of agent execution adapts to emerging data.
* Full relational database persistence of startups, tasks, events, and metrics.
* Server-side background execution completely decoupled from the browser.
* Automated recovery from server restarts and graceful handling of network disconnections.
* Real-time 3D spatial visualization of virtual employees and department status badges.

### Limitations & Honest Academic Disclosures
1. **Simulated Business Outcomes:** The system models **simulated** business metrics, progress percentages, and synthetic financial burn rates. It does not deploy real commercial mobile apps to public app stores, register legal corporate entities, or process real financial transactions.
2. **LLM Cost & Latency:** In `REAL` mode, execution speed is governed by upstream LLM API response times and requires an active internet connection with valid API credentials.
3. **Bounded Iterations:** To prevent runaway token consumption or infinite execution loops, dynamic orchestration is bounded to a maximum of 12 reasoning steps per planning phase, and execution is bounded to 90 simulated days.
4. **Local Single-Node Deployment:** Local development utilizes an embedded file-based H2 database and in-process task scheduler rather than a distributed message broker (e.g., RabbitMQ or Kafka).

---

## 10. Conclusion
The **Autonomous Virtual Startup Simulator** bridges the gap between theoretical multi-agent research and practical full-stack software engineering. By implementing genuine agent autonomy, addressed inter-agent communication, persistent memory, dynamic orchestration, and an autonomous browser-independent execution engine, the platform demonstrates how specialized AI agents can model complex, interdisciplinary human workflows.

With a comprehensive automated test suite (299 backend tests and 19 frontend tests) and a clean decoupled architecture, the project provides a rock-solid, production-grade foundation for academic evaluation in Continuous Assessment 3 (CA3).
