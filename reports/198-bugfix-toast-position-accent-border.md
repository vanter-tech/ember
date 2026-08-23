# Report 198 — Toast position fix + right accent border

## 1. Identification
- **Report Number:** 198
- **Current Task ID:** task-toast-restyle (follow-up fix, same ad-hoc UI polish item as report 197)
- **Predecessor Task:** report 197 (initial toast restyle)

## 2. Objective
Fix a wrong assumption from report 197 (toast appearing top-center instead of top-right) and add a colored right-edge accent border (green success / red error) per user feedback after seeing the first result.

## 3. Modified Files
- `frontend/src/App.tsx`

## 4. What Changed?
On the same `<Toaster/>` mount (`App.tsx:54`):
- Added `position="top-right"` — report 197 incorrectly assumed react-hot-toast's default position was already top-right; the library's actual default is `top-center`, which is what the user saw.
- Added per-type `style.borderRight`: `4px solid #16a34a` for `success`, `4px solid var(--destructive)` for `error`. react-hot-toast merges `toastOptions.success/error.style` on top of the shared base `style` object, and since `borderRight` is added after the base `border` shorthand in the merged object, it renders after it in the inline style string and overrides only the right edge — the other three sides keep the neutral `var(--border)` from the base style.

## 5. Why It Changed?
Direct user correction after visually checking the previous change: toast still appeared top-center, and they wanted a colored right-edge accent (green/red) rather than only a colored icon, for faster at-a-glance recognition of success vs. error.

**Verification:** `pnpm run build` (tsc -b + vite build) passes with no errors. Same known gap as report 197 — no `claude-in-chrome`/browser tool available this session, so the final visual result (position + colored edge, light/dark) is still not click-through-confirmed.
