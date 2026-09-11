# Report 428

**Task ID:** ad-hoc — Settings: "Reporta un problema" floating action
**Predecessor Task:** report 427 — landing downloads agent cloud badge

## Objective
Let an admin report an issue from `/admin/settings` via a form (asunto, quién lo envía, descripción). Final shape: a floating circular button (flag icon, bottom-left) shown while on the Settings page, opening a Dialog with the form — not a sidebar tab/sub-item.

## Modified Files
- `frontend/src/pages/admin/Settings.tsx`
- `frontend/src/pages/admin/components/settings/ReportIssueForm.tsx` (new)
- `frontend/src/locales/es/admin.ts`
- `frontend/src/locales/en/admin.ts`

## What Changed?
`Settings.tsx` renders a fixed circular `Button` (`variant="destructive"`, `h-16 w-16`, `Flag` icon) bottom-right, at the same vertical offset as `FloatingNav.tsx` (`bottom-[calc(1rem_+_env(safe-area-inset-bottom))] sm:bottom-8`, mirrored to `right-6`). It's a `PopoverTrigger` (not `Dialog`) — `PopoverContent` has no overlay/backdrop-blur, so the rest of the page stays fully visible and interactive, only the panel itself renders above the button. `PopoverContent` (`w-96`, `side="top" align="end"`) holds a small header + `ReportIssueForm` — Asunto, Quién lo envía (pre-filled from `useAuthStore().name`), Descripción — which builds a `mailto:tofernandoband01@outlook.com?subject=...&body=...` link on submit and closes the popover. +5 i18n keys/locale (`reportIssueLabel`, `reportIssueCardTitle/Description`, `reportIssueSubjectLabel`, etc.).

Iteration history (same task, corrected twice before landing here, commit amended each time — not pushed until final): (1) first pass put the form behind an in-card `Tabs` switcher inside `InfoSettings.tsx` — rejected, looked like a disconnected box glued into the card; (2) second pass made "Información" an expandable sidebar group (`SettingsBar.tsx`, matching Hardware/Facturación) with `REPORT_ISSUE` as a `SettingsType` leaf — reverted per further feedback in favor of the floating-button + Dialog approach, which doesn't touch `SettingsType`, `SettingsBar.tsx`, or `GlobalSearchResults.tsx` at all.

## Why It Changed?
User wanted a lightweight always-reachable report action on the Settings page, not a permanent nav entry. No backend email service exists in this app (Spring Boot backend has no `JavaMailSender`/Resend integration), so — per the user's earlier explicit choice among 3 presented options — this still uses a client-side `mailto:` to the same support address the landing contact form already delivers to (`CONTACT_TO` in `landing/wrangler.jsonc`), avoiding new backend scope for a single ad-hoc task.

## Verification
`cd frontend && pnpm run build` — clean (tsc -b + vite build, no errors).
`cd frontend && pnpm run test:run` — 121/121 passed (40 test files), no regressions.
