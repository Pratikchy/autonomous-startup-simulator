# Project Presentation Outline

Use this outline to introduce the Autonomous Virtual Startup Simulator in a short project presentation. Adjust the timing and demo to fit the audience and available setup.

## Audience and timing

- **Audience:** classmates, assessors, or technical reviewers who need a concise product and architecture overview.
- **Suggested duration:** 8–10 minutes, including a short demonstration and questions.
- **Main takeaway:** the project combines specialized agent reasoning with a persistent, observable startup simulation; present only behavior verified in the demo environment.

## Suggested flow

| Slide | Topic | Talking points |
|---|---|---|
| 1 | Project and goal | Introduce the simulator and explain that it explores how specialized AI agents can plan and model an early-stage startup. |
| 2 | The problem | Explain the limits of disconnected planning tools and one-shot AI advice when plans need cross-functional review and adaptation. |
| 3 | The simulation | Show how the CEO, Development, Marketing, and Finance agents analyze a founder's idea and contribute different perspectives. |
| 4 | Collaboration and decisions | Describe addressed inter-agent messages, the Position–Challenge–Convergence debate, and CEO synthesis. |
| 5 | System architecture | Walk through the React and TypeScript frontend, Spring Boot backend, REST and SSE communication, and relational persistence. See [the system architecture](./02_SYSTEM_ARCHITECTURE.md). |
| 6 | From plan to execution | Explain how the startup blueprint leads into the 90-day execution simulation, with task dependencies, progress, financial metrics, and blocker handling. |
| 7 | Demonstration | Create or load a startup simulation, inspect agent analysis and debate activity, then show the execution view. Use `SCRIPTED_DEMO` when a deterministic, offline demonstration is needed; use `REAL` only when its LLM provider is configured. |
| 8 | Lessons and next steps | Summarize what the project demonstrates, identify limitations observed during testing, and describe planned work without presenting incomplete phases as finished. |
| 9 | Questions | Invite questions and point to the [project overview](./01_PROJECT_OVERVIEW.md) and repository README for additional detail. |

## Opening talk track

Introduce the project as an interactive simulation of early-stage startup planning and execution. Explain that four departmental agents contribute distinct perspectives, while the application records decisions and presents activity through a web interface.

## Demo preparation

- Verify the frontend and backend start successfully in the environment being used.
- Prefer `SCRIPTED_DEMO` if network access or LLM credentials are unavailable.
- If showing `REAL` mode, verify the provider configuration beforehand and do not display or commit credentials.
- Keep a prepared example startup idea available in case live entry takes too long.
- Avoid claiming a feature works unless it has been verified in the demo environment.
