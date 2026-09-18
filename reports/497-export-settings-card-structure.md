# Report 497

## 1. Identification
- **Report number:** 497
- **Task ID:** LIVE-BUG-BATCH follow-up 3 — give "Exportar datos" the same Card structure as every other tab
- **Predecessor task:** report 496 (header/content divider on every other tab)

## 2. Objective
Live follow-up: `ExportSettings.tsx` was left as a plain `<div>` with a hand-rolled frame (from
report 495, since it previously had no `<Card>` at all) instead of the real
`Card`/`CardHeader`/`CardTitle`/`CardDescription`/`CardContent` structure every other tab now uses,
including the report-496 header/content divider.

## 3. Modified Files
- `frontend/src/pages/admin/components/settings/ExportSettings.tsx`

## 4. What Changed?
Replaced the plain `<div className="h-full flex flex-col gap-8 rounded-xl border ...">` +
hand-written `<h2>`/`<p>` header with the real `Card` components: `CardHeader` (icon circle +
`CardTitle` + `CardDescription`), the `border-t border-[#7a1315]/20` divider, and `CardContent`
wrapping the existing date-range form unchanged. No behavior change — same fields, same mutation,
same button.

## 5. Why It Changed?
Direct user follow-up: this tab was the one exception left with an ad-hoc frame instead of the real
`Card` structure every other Settings tab uses (including the divider from report 496), which this
brings in line with.

## Verification
- `cd frontend && pnpm run build` → clean.
- `cd frontend && pnpm run lint` → 0 errors, 16 pre-existing warnings (unchanged).
- `cd frontend && pnpm run test:run` → **150/150** (pure structural/styling change, no test impact).
- No backend changes.
