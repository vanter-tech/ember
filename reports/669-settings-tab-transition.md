# Report 669 — SETTINGS-TAB-TRANSITION

## 1. Identification
- Report: 669
- Task ID: SETTINGS-TAB-TRANSITION
- Predecessor: 668 (RELEASE-V0.3.5)

## 2. Objective
Make switching between the Settings tabs animate like the rest of the interface (routed pages fade/slide in; the Settings tabs did not).

## 3. Modified Files
- New: `frontend/src/components/AnimatedTabContent.tsx` (+ `AnimatedTabContent.test.tsx`)
- Modified: `frontend/src/pages/admin/Settings.tsx`
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
`AnimatedTabContent` wraps its children in the same `animate-in fade-in slide-in-from-bottom-2 duration-300` used by `AnimatedOutlet`, plus `motion-reduce:animate-none`, keyed by the active tab so the wrapper remounts (and the animation replays) on every tab change but not when the same tab re-renders (typing in a form). `Settings.tsx` wraps `renderContent()` with it, keeping the `#settings-tour-content` container for the guided tour.

## 5. Why It Changed?
The routed pages animate because `AnimatedOutlet` remounts its wrapper per pathname; the Settings tabs switch by local state, so nothing remounted and there was no transition.

### Verification
- 3 new component tests failed first; `pnpm exec vitest run src/components src/pages/admin` 65/65; `pnpm run build` exit 0; `pnpm run lint` 0 errors. In the running app the wrapper remounts on tab click and the `enter` animation starts.

### NOT verified
- The fade itself on screen (the browser tab used for checking was hidden).
