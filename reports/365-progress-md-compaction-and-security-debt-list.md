# Report 365 — PROGRESS.md compaction + security-debt task list

## Identification
- **Report:** 365
- **Task ID:** ad hoc (user-requested housekeeping, no milestone ID)
- **Predecessor Task:** report 364 (FIX-QA 2nd-pass remediation)

## Objective
Bring `PROGRESS.md` back under the 180-line cap mandated by `CLAUDE.md` §6 (it had drifted to 370 lines of accumulated per-task narrative), and add an explicit checkbox list for the security/hardening findings (F-15, F-21, F-24, F-10/E-23, F-22) that a live SaaS-status review surfaced as accepted-but-unplanned debt.

## Modified Files
- `PROGRESS.md` (full rewrite)

## What Changed?
- **Current Execution State** trimmed to the 4 required fields (Last Completed Task, Predecessor, Current Active, System Health) instead of a running task-by-task log.
- **Active Context & Recent Decisions** cut from ~29 sprawling paragraphs to 9 bullets, keeping only the gotchas that are still load-bearing for future sessions (tenant-isolation model, the WebSocket multi-endpoint broker-registration bug class, Flyway baseline, analytics money semantics, the missing-shadcn-primitive pattern, the digital-payments stub, Hub heartbeat merge status). Historical implementation detail for already-shipped features was dropped — it already lives in the numbered `reports/*.md` files this index points to.
- **Task Queue Status** collapsed each completed initiative (EMB-PRINT, EMB-MOD, EMB-INV, onboarding, Ember Hub HUB-01/heartbeat, EMBER-FIX, HPD, Landing SEO, payment-flow cluster, FIX-QA) to one line each, keeping every still-open checkbox (EMB-GATEWAY, Hub v2/installer/service, HPD-21/22, LSEO-04/06-18, FIX-QA's E-23) verbatim.
- Added a new **"Security/hardening debt"** subsection with 5 unchecked items (F-15, F-21, F-24, F-10/E-23, F-22), each carrying enough context to start a `brainstorming`/spec+plan pass without re-reading old QA reports.
- Corrected one stale fact caught while compacting: the note that Ember Hub license-heartbeat's PR to `main` was "PENDING operator" was outdated — `git log` confirms PR #60 merged 2026-08-28; the note is now "merged, manual smoke test status unconfirmed" instead of asserting it never happened.

## Why It Changed?
`CLAUDE.md` §6 hard-caps `PROGRESS.md` at 180 lines specifically so it stays a live pointer, not a changelog — the changelog role is already served by `reports/`. The file had violated that cap for a long time without anyone forcing a rewrite. Separately, the security debt identified during today's SaaS-status assessment (F-15/F-21/F-22/F-10/F-24) existed only as prose inside a completed-task narrative bullet, with no checkbox — easy to lose track of. Turning it into its own Task Queue subsection makes it pickable as a real next task tomorrow.
