# Report 540 — Pricing page: comparison table and plan-card bullets match what's actually gated

## 1. Identification
- **Report:** 540
- **Task ID:** PRICING-TABLE-HONESTY
- **Predecessor Task:** report 539 — F-10-PIN-ENUMERATION-MITIGATION
- **Branch:** `fix/pricing-table-honesty` off `main`

## 2. Objective
The landing page's plan comparison table and per-card feature bullets (`/planes`) claimed
several features as tier-exclusive (Starter+/Pro+) that are not actually plan-gated anywhere in
the backend — a paying Starter/Pro customer could discover Free already gets the same thing for
free, or that an advertised Pro-only capability (limiting concurrent waiters) simply doesn't
exist in the codebase. Audited every row against `PlanGateService` (the single place plan limits
are enforced) and corrected the copy to match reality.

## 3. Modified Files
- `landing/src/lib/plans.ts`
- `landing/src/i18n/ui.ts` (ES + EN blocks)

## 4. What Changed?
Audited every `requirePlanAtLeast`/`requireTableCapacity` call site in
`backend/src/main/java/com/vanter/ember/restaurant/service/PlanGateService.java` (the only place
plan gates exist) against every row/bullet on the pricing page. The **only** real gates are:
`tables` (FREE=1, STARTER=10, PRO/ENTERPRISE=unlimited), `periodfilters` (STARTER+), `cashclose`
(STARTER+), `roles` (KITCHEN/ACCOUNTANT specifically — STARTER+), `branding` (STARTER+), and
`export` (PRO+).

**Comparison table (`getComparison()`):**
- "Gestión de piso y meseros", "División y unión de cuentas", "Impresión de comandas",
  "Analítica avanzada" (ticket promedio/producto/mesa via `getProducts`/`getTables`) — none are
  gated anywhere; changed from `[false, true, true, true]` to `[true, true, true, true]`.
- "Múltiples salones/áreas" — no such concept exists in the dining-table model at all; row
  removed.
- "Múltiples meseros simultáneos" — zero code backs this (no concurrent-session limit exists
  anywhere); row removed.
- "Multi-sucursal" — no branch/location concept exists in the `Restaurant` model; row removed
  from the Scale & Support group (a real engineering gap, not a sales-negotiable service
  commitment like SLA/integrations/account manager, which stayed).
- "Roles Mesero / Cocina / Admin" relabeled to "Roles de Cocina y Contabilidad" — the gate only
  ever blocks creating `KITCHEN`/`ACCOUNTANT` staff; `WAITER`/`ADMIN` are unrestricted baseline
  roles, so the old label overclaimed.
- "Gestión de empleados" (general staff CRUD) changed to `[true, true, true, true]` — only adding
  `KITCHEN`/`ACCOUNTANT` is gated (the "roles" row above), not staff management itself.

**Plan-card bullets (`getPlans()`):** same corrections in short form —
- Starter: "KDS y gestión de piso" → "Cierre de caja por turno"; "División de cuentas" →
  "Roles de cocina y contabilidad" (both now point at real STARTER gates).
- Pro: "Analítica avanzada" → "Exportación de reportes" (the only real PRO-exclusive gate
  besides unlimited tables); "Múltiples meseros y roles" removed outright — Pro card is now 3
  honest bullets instead of 4 padded ones (`plan.pro.f4`/its English mirror removed as unused).
- Enterprise: "Multi-sucursal" → "Soporte dedicado 24/7" (mirrors the real `ptable.v.dedicated`
  support-tier value already shown in the table; SLA/integrations/account-manager bullets kept
  as legitimate sales-negotiated commitments).

Ember Hub's landing section and pricing (separate task, see memory `ember-hub-pricing`) were not
touched — this report is Ember Web (cloud) only.

## 5. Why It Changed?
A Starter or Pro subscriber who discovers a feature they paid for was already free on the Free
tier is a trust problem in the wrong direction — it looks like the tiers exist to extract money
for artificial scarcity rather than real value. Worse, "Múltiples meseros simultáneos" promised a
capability to Pro/Enterprise customers that has literally no implementation to deliver on.
Cross-referencing every claim against the one real source of truth (`PlanGateService`) turns the
page into an accurate description of the product instead of aspirational copy that happened to
ship. The Enterprise-tier service commitments (SLA, custom integrations, dedicated account
manager, 24/7 support) were deliberately left as-is even though they have no code backing them —
those are legitimate to sell as case-by-case negotiated commitments (standard SaaS Enterprise-tier
practice), unlike a concrete software capability like multi-branch or a waiter concurrency limit
that would need real engineering to exist at all.

## 6. Verification
- `cd landing && pnpm run build` — clean, all 28 pages (ES + EN) built without errors.
- Grepped the built `dist/planes/index.html` and `dist/en/planes/index.html` for every removed
  claim ("Múltiples meseros", "Multi-sucursal", "salones", "División de cuentas") — none remain.
- Grepped source for the removed i18n keys (`ptable.r.rooms`, `ptable.r.multiwaiter`,
  `ptable.r.multibranch`, `plan.pro.f4`) — no leftover references anywhere in `landing/src`.
