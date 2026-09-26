# Report 602 — LANDING-LEGAL-IDENTIFICATION

## 1. Identification
- Report: 602
- Task ID: LANDING-LEGAL-IDENTIFICATION
- Predecessor: BACKEND-CI-AND-SECURITY-BUMPS (report 601)

## 2. Objective
Publish the privacy policy / terms text that identifies who operates Ember.

## 3. Modified Files
- `landing/src/i18n/ui.ts`

## 4. What Changed?
- ES and EN privacy policy and terms: they no longer say Vanter is a company with an address in Managua; they state "Vanter" is the trade name of Fernando Obando, a natural person domiciled in Managua, Nicaragua, and name Fernando Obando (Vanter) as the data controller and contact. The terms section 1 becomes "Identificación del prestador y aceptación de los términos". Contact email unchanged.
- Left out on purpose: `landing/CLAUDE.md` (an accidental symlink -> regular file type change) and `docs/legal/` (draft pilot service agreement, untracked).

## 5. Why It Changed?
The text had been edited and built (28 pages, 2026-09-23) but never committed, so production still showed the old wording. The owner confirmed publishing it; note the pilot-readiness item 4 (lawyer review of privacy/terms) is still open in `PROGRESS.md`.

Verification: `cd landing && pnpm run build` clean. Landing deploys from `main` via Cloudflare after the PR merges.
