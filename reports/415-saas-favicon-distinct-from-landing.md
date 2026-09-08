# Report 415 — SaaS favicon = `ember_logo.ico`, distinct from the landing

## 1. Identification
- **Report number:** 415
- **Current Task ID:** ad-hoc frontend polish (no milestone ID)
- **Predecessor Task:** report 414 — Info card bigger logos / Vanter SVG / Ember red

## 2. Objective
Report 412 pointed the SaaS favicon at the landing's icon set. The owner wants the app tab
to be visually distinct from the landing again, and supplied `ember_logo.ico`.

## 3. Modified Files
- `frontend/index.html`
- `frontend/public/ember_logo.ico` (new, owner-provided — 256×256 PNG-in-ICO)
- `frontend/public/favicon.ico`, `frontend/public/favicon.svg`,
  `frontend/public/apple-touch-icon.png` (removed — the landing copies from report 412)

## 4. What Changed?
- `index.html` head: the three icon `<link>`s (`favicon.ico` + `favicon.svg` +
  `apple-touch-icon.png`) collapse to one — `<link rel="icon" href="/ember_logo.ico" />`. The
  SVG link had to go too: browsers prefer an SVG favicon when present, so leaving the
  landing's `favicon.svg` referenced would have kept the landing mark in the tab.
- The three landing icon files added in report 412 are deleted from `frontend/public/` since
  nothing references them any more. (`ember_logo_info.svg` / `vanter-tech_logo.svg` stay —
  those are the in-app Info-card logos, not favicons.)

## 5. Why It Changed?
Two products, two tabs — the operator needs to tell the SaaS and the marketing site apart at
a glance.

## 6. Verification
- `cd frontend && pnpm run build` — clean; `dist/ember_logo.ico` emitted, no stale favicons.
- `pnpm run test:run` — 118/118.
