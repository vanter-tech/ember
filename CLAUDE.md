# CLAUDE.md — Ember Project & Bug Tracker Guidelines

## 1. Project Overview & Architecture
Ember is a multi-tenant, modular monolith restaurant management platform designed to handle end-to-end operations including real-time collaborative customer ordering, Kitchen Display Systems (KDS), floor/waiter management, billing, and multi-granularity admin analytics.

### Core System Vision & Roles
- **Multi-Tenant System:** Built to isolate, configure, and serve multiple restaurant tenants seamlessly within a single architecture.
- **Customer Experience (Collaborative Real-Time Cart):**
  - Customers join a table session by scanning a QR code or entering a 5-character numeric code.
  - Features a live collaborative cart where participants see everyone's items in real time and can place/confirm their own orders.
- **Waiter Role (Floor Operations & Billing):**
  - Table session management (assigning tables, managing sessions, closing tables).
  - Manual item additions, bill calculation/splitting, and shift cash register closures (*cierre de caja*).
- **Kitchen Display System (KDS):**
  - Real-time order queue tracking dish statuses through controlled state transitions (`PENDING` → `PREPARING` → `READY` → `DELIVERED`).
- **Admin Panel (Full Control & Analytics):**
  - Full system administration across catalog management (categories, menu items, pricing).
  - Real-time table status monitoring across the floor.
  - Comprehensive business analytics & metrics dashboards filtered by day, week, month, and year.
  - Employee management (roles, access) and tenant system configurations.

### Tech Stack & Persistence
- **Backend:** Java 17, Spring Boot 3.5.14, Spring Security (JWT), WebSocket/STOMP.
- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS 4, Zustand 5, TanStack Query 5, shadcn/ui.
- **Hybrid Persistence:**
  - **PostgreSQL (JPA):** `identity`, `catalog`, `billing`, `settings`, `restaurant`.
  - **MongoDB:** `session` (documents with embedded participants), `kitchen` (embedded orders and items).
- **Event Handling:** 100% internal synchronous communication via Spring `ApplicationEventPublisher` and `@EventListener`. **DO NOT use or configure Kafka** (the dependency in `pom.xml` should be ignored or removed).

---
## 2. Primary Development Commands

### Backend (`/backend`)
- **Compile and run:** `./mvnw spring-boot:run`
- **Run all tests:** `./mvnw test`
- **Run specific test:** `./mvnw test -Dtest=E2EOrderFlowTest`

### Frontend (`/frontend`)
- **Package Manager:** Use `pnpm` exclusively.
- **Development server:** `pnpm run dev`
- **Type check & Build:** `pnpm run build` (executes `tsc -b && vite build`)
- **Linter:** `pnpm run lint`
- **Clean compilation:** `tsc -b --clean`

---

## 3. High-Level Engineering Priorities

The active granular task backlog is tracked dynamically in `PROGRESS.md`. Engineering rules must align with:

1. **Frontend Stability:** Zero-tolerance for TypeScript compilation errors (`tsc -b`) and linter failures (`pnpm run lint`).
2. **Real-Time Synchronization:** Correct handling of STOMP/WebSocket connections and state cleanup on disconnect.
3. **Backend Data Consistency:** Optimistic locking on MongoDB models (`@Version`), explicit `@Transactional` boundaries, and atomic race-condition prevention in billing/payments.
4. **Security & Identity:** Strict validation of authenticated JWT claims against request resources.

---

## 4. Strict Report and Commit Policies (Mandatory)

### Task Report Generation
- **Mandatory Location:** All reports MUST be saved inside the `/reports/` directory at the project root (`ember/reports/`). DO NOT place reports inside `.claude/`. If `/reports/` does not exist, create it automatically.
- **Sequential & Numerical Naming:** The filename MUST include a two-digit sequential number (`01`, `02`, `03`...) followed by the Task ID and a brief description (e.g., `reports/01-task-1.1-fix-typescript-kitchen-build.md`, `reports/02-task-1.2-repair-eslint-config.md`). This enables tracking chronologically which task was executed last and its predecessor. **This sequence number reflects chronological completion order, assigned at COMMIT time — it is independent of the task's milestone ID.** If tasks are executed out of backlog order, the report number will diverge from the task's ID (e.g., task `2.1` could become report `05` if executed fifth). `PROGRESS.md`'s task queue therefore lists bare milestone IDs (`task-1.1`, `task-2.1`...) with no baked-in sequence prefix.
- **Internal Report Structure:** Each document must strictly include:
  1. **Identification:** Report number, current Task ID, and Predecessor Task.
  2. **Objective:** Brief description of the goal.
  3. **Modified Files:** List of exact file paths.
  4. **What Changed?:** Detailed technical explanation of the modifications.
  5. **Why It Changed?:** Rationale behind the fix or feature introduced.

### Git & Commit Guidelines
- **Squashed Atomic Commits:** Every completed task MUST result in exactly one squashed atomic commit. Do not create or leave fragmented WIP commits in the git history for a single task.
- **Scoped Staging:** Stage only the files the task actually touched, plus `PROGRESS.md` and the new report (`git add <specific paths>`). NEVER use `git add -A` or `git add .` — this repo has previously tracked secrets (see `.env`) and a blanket add risks re-staging or sweeping in unrelated stray files.
- **No Co-authorship or Signatures:** Forbidden to include tags such as `Co-authored-by:`, `Signed-off-by:`, AI signatures, or collaborative mentions in commit messages or code.
- **Commit Format:** Clear, concise, lowercase messages adhering to Conventional Commits standards (e.g., `fix(frontend): resolve unused variables in kitchen view`).
- **Authorship:** Commits must be performed cleanly as direct executions by the local user.

---

## 5. Context Persistence & Anti-Loop Protocol

### Memory Persistence
- Maintain a `PROGRESS.md` file at the root to track execution state.
- At the START of a session, read `PROGRESS.md` to identify the active task.
- At the END of a task, update `PROGRESS.md` with the new state, completed task ID, and system health status.

### Quality Control & Anti-Slop
- **Zero-Tolerance Build Policy:** Every task must end with explicit verification via `Bash` (`pnpm run build` for frontend or `./mvnw test` for backend — see §2 for canonical commands; do not substitute `mvn` or bare `tsc -b`, they are not guaranteed to exist/behave identically on this machine).
- **Surgical Edits:** Modify only necessary code lines. Do not rewrite full files or introduce unrequested dependencies.
- **Loop Prevention:** If a fix fails twice consecutively, stop file modifications, summarize the blocker, and request human guidance.

---

## 6. PROGRESS.md Schema & Maintenance Rules

The agent MUST maintain `PROGRESS.md` at the project root (`ember/PROGRESS.md`) adhering strictly to the following 3-section structure:

1. **Current Execution State:** Tracks `Last Completed Task`, `Current Active Task`, `Predecessor Task`, and `System Health` (build status of frontend and backend).
2. **Active Context & Recent Decisions:** High-level summary (<10 bullet points) of architectural decisions and active context.
3. **Task Queue Status:** Checkbox list of task IDs and brief descriptions.

### Strict Maintenance Policies
- **Size Limit:** Keep `PROGRESS.md` under 60 lines. Overwrite obsolete context notes as tasks progress.
- **Update Timing:** Read `PROGRESS.md` upon session start; update `PROGRESS.md` immediately upon task completion.

---

## 7. Mandatory Workflow Lifecycle (1-Task-1-Context)

For every single task prompt, the agent MUST strictly enforce the following 6-step execution lifecycle:

1. **[1. PLAN] (Read-Only Phase):**
  - Read `PROGRESS.md` to identify active task context.
  - Analyze target codebase using `Grep`/`FileRead`.
  - Output a concise, step-by-step implementation plan.
  - **STRICT RULE:** DO NOT edit, create, or delete any files during this step.

2. **[2. APPROVE] (Gatekeeper Phase):**
  - Pause execution and explicitly prompt the user for approval (e.g., *"Do you approve this plan to proceed with execution?"*).

3. **[3. EXECUTE] (Surgical Modification & Verification Phase):**
  - Apply minimal, targeted code edits.
  - Run verification via `Bash`: `cd frontend && pnpm run build` (for frontend) or `cd backend && ./mvnw test` (for backend).
  - If verification fails, fix errors up to 2 attempts maximum before stopping.

4. **[4. REPORT] (Documentation & Memory Update Phase):**
  - Create a sequential report file inside `/reports/XX-task-id-description.md`.
  - Update `PROGRESS.md` with current active task state, system health, and completed checkboxes.

5. **[5. COMMIT] (Clean Git Snapshot Phase):**
  - Execute a clean, squashed atomic git commit covering all changes into a single commit per task following Conventional Commits (e.g., `fix(frontend): resolve TS compilation errors in kitchen view`).
  - **STRICT RULE:** Forbidden to include `Co-authored-by:`, `Signed-off-by:`, or AI signatures.

6. **[6. RESET REMINDER] (Context Clearing Phase):**
  - Conclude the final output message with an explicit reminder to the user to run `/clear` in the terminal to reset the context window for the next task.
---

## 8. Token Optimization & Terse Output Rules

To maximize context efficiency, reduce latency, and prevent token depletion, the agent MUST strictly follow these output rules:

- **Zero Conversational Fluff:** No introductory pleasantries, meta-commentary, or conversational wrapping (e.g., NEVER write "Sure!", "I will now...", "I have completed...").
- **No Code Echoing:** When editing files with tools (`FileEdit`/`FileWrite`), NEVER print full code blocks or file contents in the chat response. State only the modified file path and a brief 1-line description.
- **Compact Planning Format:** Step 1 ([PLAN]) must be strictly under 5 bullet points and under 100 words total.
- **Truncated Error Log Output:** When `pnpm run build` or `mvn test` fails, print only the failing file and specific error line—do NOT output full stack traces in chat.
- **Direct Lifecycle Deliverables:** Deliver only the exact output required by the current step in the 6-step lifecycle.