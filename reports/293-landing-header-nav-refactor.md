# Report 293 — landing-header-nav-refactor

## 1. Identification
- **Report number:** 293
- **Task ID:** landing-header-nav-refactor (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 292 (landing-hero-floatingnav-and-drawer-anim)

## 2. Objective
The site header (`Nav.astro`) read as flat, undifferentiated text. Give it a brand
mark, grouped actions, an active-link state, and a scroll-aware sticky treatment.

## 3. Modified Files
- `landing/src/components/Nav.astro`

## 4. What Changed?
- **Brand mark** — the wordmark is now preceded by a `size-8` `rounded-lg bg-primary`
  chip holding a flame glyph; the chip rotates `-6deg` on logo hover.
- **Nav links** — each link is a `rounded-md px-3 py-1.5` pill:
  `text-muted-foreground hover:bg-muted` normally, `bg-muted text-foreground` +
  `aria-current="page"` when active. `isActive()` is route-based (`/planes` matches
  on the plans page); in-page anchors like `/#features` are never "current".
- **Layout** — logo/wordmark alone on the left; the nav links, the auth actions and
  the mobile drawer are wrapped in one right-aligned group (`flex items-center gap-2
  md:gap-6`) so `justify-between` pins the brand left and everything else right.
- **Grouped actions** — "Iniciar sesión" (ghost) and "Registrarme" are separated by
  a thin `h-5 w-px bg-border` divider. "Registrarme" is the primary button and now
  carries a right-arrow that nudges `translate-x-0.5` on hover.
- **Scroll-aware sticky** — the header starts `border-transparent bg-transparent`
  (floats over the hero) and, once `window.scrollY > 8`, gains
  `bg-background/80 backdrop-blur border-border shadow-sm` via a `data-scrolled`
  attribute toggled by a small `is:inline` script, with a
  `transition-[background-color,border-color,box-shadow] duration-300`.
- Vertical padding trimmed `py-4` → `py-3` (header is now ~61 px).

The mobile side (`<MobileNavDrawer client:load />` under `md:hidden`) is unchanged;
the drawer is already portaled to `body` (report 290) so the header's new
`backdrop-blur`-on-scroll does not trap it.

## 5. Why It Changed?
"El nav no me convence, está muy simple." The header had no brand identity, no
sense of where you are, and a hard border at the top that fought the new floating
hero. A logo mark + active state + a transparent-to-solid scroll transition are the
minimum for it to feel designed rather than scaffolded.

## Verification
- `cd landing && pnpm build` — green, 6 pages.
- Dev server in Chrome: at `scrollY 0` the header is transparent, borderless; after
  scrolling, `data-scrolled` is set and computed `background-color` is
  `oklab(1 0 0 / 0.8)` with the border/shadow visible. On `/planes` the "Precios"
  link has `aria-current="page"` and the filled pill; "Funcionalidades" does not. No
  horizontal page overflow.
- No file outside `landing/` touched.
