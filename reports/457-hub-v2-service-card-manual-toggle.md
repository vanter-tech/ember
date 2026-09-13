# Report 457

## 1. Identification
- **Report Number:** 457
- **Task ID:** EMBER-HUB-V2 UX change (ad-hoc, post-plan) — service cards should be manually collapsible with persistent, individually-boxed logs
- **Predecessor Task:** report 456 (Tauri capabilities + first round of UI fixes)

## 2. Objective
User feedback after testing report 456's build: the Servidor card was stuck expanded with no way to collapse it, while Postgres/MinIO correctly auto-collapsed once `RUNNING` — but the user wants the opposite of auto-collapse: logs should stay put once shown, and every card should be manually collapsible/expandable on demand instead of being purely phase-driven. Also: each log line needs its own `#8c1717` box (they were all inside one shared box).

## 3. Modified Files
- Modify: `ember-hub/ui/src/components/ServiceCard.tsx`
- Modify: `ember-hub/ui/src/components/ServiceCard.test.tsx`

## 4. What Changed?
Replaced the old `expanded = starting || stopping || isError` (purely phase-derived, unmounts the log the instant the phase moves on) with real component state: a `useState` `expanded` flag that a new always-visible toggle button (the chevron, now a real `<button aria-label="Mostrar/Ocultar registro de <título>">`, not just a decorative icon) can flip at any time, plus a `useEffect` that force-opens it (but never force-closes it) whenever `phase` transitions into `STARTING`/`STOPPING`/`ERROR` — so something new happening still auto-reveals itself, but the user's own manual collapse is never overridden until the *next* such transition.

`useTypedLog` (two separate hook calls, one for start-script one for stop-script, each clearing its own lines back to `[]` whenever its `active` flag went false) was replaced by a single `useServiceLog(id, phase)` that only resets `lines` when a *new* STARTING or STOPPING cycle begins — moving past that phase (e.g. into `RUNNING`) no longer clears anything, so the last-revealed script stays visible until the next start/stop cycle overwrites it.

The log container itself changed from one `bg-[#8c1717]` box wrapping all `<p>` lines to a plain `flex flex-col gap-1 overflow-y-auto max-h-32` wrapper, with **each line now its own** `rounded-lg bg-[#8c1717] text-white ... px-2 py-1` chip (including the single-line real error message on `ERROR`) — matching "cada log tiene que tener su propio fondo #8c1717" instead of one shared block.

## 5. Why It Changed?
Direct, explicit user feedback contradicting the plan's original assumption (auto-expand-then-auto-collapse) — confirmed live during testing that Postgres/MinIO's auto-collapse-on-RUNNING behavior was the *unwanted* one, not a bug to fix elsewhere. Manual control plus persistence is a strictly more capable design (auto-open still happens on real events; nothing is ever hidden without the user choosing to hide it) and needed no compromise with the "never fake success/failure" principle from the original plan — the real `ERROR` message is still what's shown, just now toggleable like everything else.

## 6. Verification
- `cd ember-hub/ui && npm run test` → 10/10 pass (8 previous + 2 new: log persistence across a STARTING→RUNNING rerender via fake timers, and manual collapse/re-expand via the toggle button on an ERROR card).
- `npm run build` → clean.
- Live, on a real install (screenshotted): all three service cards show a chevron toggle regardless of phase; MinIO/Servidor logs (each line in its own `#8c1717` chip) stayed visible after reaching "En ejecución" instead of disappearing.
