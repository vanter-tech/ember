# Report 201 — bugfix-comanda-modifiers-menu-style

## 1. Identification
- **Report Number:** 201
- **Task ID:** bugfix-comanda-modifiers-menu-style (ad-hoc UI follow-up, not in the milestone backlog)
- **Predecessor Task:** report 200 (comanda modifiers display)

## 2. Objective
Restyle the modifiers block added in report 200 into a menu-like list (one row per modifier option, with its price delta) on a full-card-width red background, per user feedback.

## 3. Modified Files
- `frontend/src/pages/customer/ComandaView.tsx`

## 4. What Changed?
Replaced the plain comma-joined `<span>` of modifier names (in both the draft/Participants card and the sent-order/Historial card) with a `w-full` block using `bg-[#8c1717]` (the page's existing brand red, already used for prices/badges), rounded corners, and one white text row per modifier showing `optionName` and, when positive, `+$priceDelta`.

## 5. Why It Changed?
User feedback after report 200: wanted the modifiers presented "like a menu" (itemized rows) rather than a single comma-separated line, with a red background spanning the card width for visual emphasis. Reused the page's existing `#8c1717` brand red rather than introducing a new color token, for consistency with the rest of `ComandaView.tsx`.

## Verification
`cd frontend && pnpm run build` — PASS (`tsc -b && vite build`, no errors).
