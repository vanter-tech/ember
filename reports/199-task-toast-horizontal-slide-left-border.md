# Report 199 — Toast horizontal slide-in + left accent border + wider card

## 1. Identification
- **Report Number:** 199
- **Current Task ID:** task-toast-restyle (3rd follow-up on the same ad-hoc UI polish item as reports 197/198)
- **Predecessor Task:** report 198 (position fix + right accent border)

## 2. Objective
Per user feedback: move the colored accent border from the right edge to the left edge, replace the toast's vertical (top-to-bottom) entrance animation with a horizontal one sliding in from right to left, and widen the card.

## 3. Modified Files
- `frontend/src/App.tsx`
- `frontend/src/index.css`

## 4. What Changed?
- **Animation:** `react-hot-toast`'s entrance/exit animation is hardcoded inside the library (`ToastBar`'s internal `getAnimationStyle`, a vertical `translate3d` keyframe with no prop to change axis — confirmed by reading the installed package source at `node_modules/.pnpm/react-hot-toast@2.6.0.../src/components/toast-bar.tsx`). `ToastBar`'s style merge order is `{...animationStyle, ...style, ...toast.style}`, so a `style.animation` passed through `<Toaster>`'s `children` render-prop (which receives the raw toast `t`, including `t.visible`) overrides the built-in one. Used that render-prop to wrap `<ToastBar toast={t} style={{ animation: t.visible ? 'toast-slide-in ...' : 'toast-slide-out ...' }} />`, with two new horizontal `@keyframes` (`toast-slide-in`/`toast-slide-out`, translating on the X axis from `100%` instead of Y) added to `frontend/src/index.css`, mirroring the library's own enter/exit timing/easing but swapping the transform axis.
- **Accent border:** `borderRight` → `borderLeft` on the `success`/`error` `toastOptions` style overrides.
- **Width:** added `maxWidth: '420px'` to the base `toastOptions.style` (library's own hardcoded default is `350px`, in `ToastBarBase`'s styled-component CSS — overridden here since `toast.style` is applied as inline style, which always wins over the library's class-based CSS).

No call-site changes — still isolated to the single `<Toaster>` mount.

## 5. Why It Changed?
Direct user correction after visually checking report 198's result: wanted the accent on the left, a right-to-left horizontal entrance instead of the default vertical drop, and a wider card. Implemented via the library's supported `children` render-prop customization point (`<ToastBar>`) rather than fragile CSS overrides of internal goober-generated class names, so it survives library updates as long as `ToastBar`'s style-merge order is unchanged.

**Verification:** `pnpm run build` (tsc -b + vite build) passes with no errors. Same known gap as reports 197/198 — no `claude-in-chrome`/browser tool available this session, so the final visual/animation result is still not click-through-confirmed.
