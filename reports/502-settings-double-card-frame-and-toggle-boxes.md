# Report 502

## 1. Identification
- **Report number:** 502
- **Task ID:** LIVE-BUG-BATCH follow-up — Settings tabs' double-card frame + toggle sections need a bordered box
- **Predecessor task:** report 501 (LIVE-BUG-BATCH complete, all 7 original bugs)

## 2. Objective
Live follow-up to bug 4 (Settings cards grid layout): the user reported that every Settings tab
still rendered **two stacked card frames** — a visible line at the end of the first "card," then a
second box beginning below it. Also requested: sections with an enable/disable toggle should be
visually grouped in their own rounded border, from the label text to the switch, so it reads as one
unit being turned on/off.

## 3. Modified Files
- `frontend/src/pages/admin/Settings.tsx`
- `frontend/src/pages/admin/components/settings/InfoSettings.tsx`
- `frontend/src/pages/admin/components/settings/ExportSettings.tsx`
- `frontend/src/pages/admin/components/settings/MenuSettings.tsx`
- `frontend/src/pages/admin/components/settings/PaymentGatewaySettings.tsx`
- `frontend/src/pages/admin/components/settings/HardwareSettings.tsx`
- `frontend/src/pages/admin/components/settings/TicketSettings.tsx`
- `frontend/src/pages/admin/components/settings/LoyaltySettings.tsx`
- `frontend/src/pages/admin/components/settings/BillingSettings.tsx`

## 4. What Changed?
**Root cause of the double frame.** `Settings.tsx`'s `#settings-tour-content` container drew its
own frame (`bg-white rounded-xl shadow-sm border border-zinc-200`) around whichever tab was active
— but every tab (except `InfoSettings`/`ExportSettings`, already fixed for this exact issue
earlier) also renders its own `<Card>`, which has its own `bg-card`/`ring-1 ring-foreground/10`, and
a `CardFooter` with its own `bg-muted/50 rounded-b-xl`. Two nested, independently-styled boxes —
the inner `Card`'s edge (plus the `CardFooter`'s distinct gray background) read as "a card ending,"
followed by the outer container's own border further down, reading as "a second card."

User-confirmed fix direction: keep every tab's `CardHeader`/`CardFooter` structure as-is, and
instead strip the frame from the **outer** container in `Settings.tsx` — leaving each tab's own
`<Card>` as the single source of truth for the frame. `InfoSettings.tsx`/`ExportSettings.tsx` (the
two tabs with no `<Card>` of their own, having been fixed for this same bug previously by removing
their inner Card) now needed the frame moved back onto their own top-level `<div>` instead, since
the outer container no longer supplies one — same visual classes a `<Card>` would produce
(`rounded-xl border-zinc-100 bg-card shadow-sm ring-1 ring-foreground/10`), applied directly rather
than importing the component (neither has a `CardHeader`/`CardFooter` split to preserve).

**Toggle sections get a bordered box.** Every enable/disable `Switch` row (label + description +
switch, previously a bare `flex items-center justify-between`) in `MenuSettings` (2),
`PaymentGatewaySettings` (1), `HardwareSettings` (2), `TicketSettings` (2), `LoyaltySettings` (1),
and `BillingSettings` (1) now wraps in `rounded-xl border border-zinc-200 p-4` — visually grouping
the whole row as one controlled unit. `BusinessHoursSettings.tsx`'s per-day rows already had this
treatment (`border border-zinc-200 rounded-lg p-3`) from before this task and needed no change.

## 5. Why It Changed?
Direct, confirmed user request following up on bug 4's grid-layout fix — the layout fix alone
didn't address the deeper double-card rendering, and the toggle-box request makes it visually clear
which text a given switch actually controls, especially now that toggles sit in a 2-column grid
next to other, non-toggle fields.

## Verification
- `cd frontend && pnpm run build` → clean.
- `cd frontend && pnpm run lint` → 0 errors, 16 pre-existing warnings (unchanged).
- `cd frontend && pnpm run test:run` → **150/150** (pure styling change, no test impact).
- No backend changes.
