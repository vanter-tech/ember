# Report 303 — landing-cta-simplified

## 1. Identification
- **Report number:** 303
- **Task ID:** landing-cta-card (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 302 (landing-dark-mode)

## 2. Objective
Replace the full-width solid-red `CTASection` band above the footer with a wide
contained card whose background is a blurred SaaS screenshot.

## 3. Modified Files
- `landing/src/components/CTASection.astro`

## 4. What Changed?
The `<section>` is `bg-background` (was `bg-primary`) with the `border-t` divider.
Inside a `max-w-6xl` wrapper sits a **wide contained card**:
`relative isolate overflow-hidden rounded-2xl border border-border bg-card shadow-sm
px-6 py-20 md:px-12 md:py-28 text-center`.
- **Background:** the `Hero.png` `<Image>` (`alt=""`, `aria-hidden`) `absolute
  inset-0 -z-10 h-full w-full scale-125 object-cover blur-2xl`, at `opacity: .28`
  (`.14` in dark), over a `bg-card/45` scrim (`~82%` card in dark) so the text
  stays legible — a soft, blurred product screenshot behind the copy.
- **Content:** a small centred `h-1 w-12 bg-primary` accent dash, `text-primary`
  eyebrow, `text-foreground` `h2`, `text-muted-foreground` lede, a `bg-primary`
  "Registrarme gratis" button + a `text-primary` "o mirá los planes →" link to
  `/planes`.

All token-based → correct in dark. (Iterations this session: solid red band →
contained card → card + ember glow → glow removed → card removed → **wide card with
a blurred SaaS-screenshot background**, the final form.)

**Follow-up (report 304-era):** the card was darkened a touch — a `.cta-darken`
overlay div, `rgb(0 0 0 / 0.08)` in light and `rgb(0 0 0 / 0.35)` in dark, above
the image + scrim and below the content.

`CTASection` is also mounted on `/planes` and `/funcionalidades`, so those pages get
the same treatment.

## 5. Why It Changed?
"No me convence… ese gran cuadro rojo… ¿alguna alternativa?" → contained card.
"Otra alternativa que no sea con ese degradado." → glow removed. "¿Y si le
quitamos el cuadro?" → card removed. "Es mejor regresarle el cuadro pero que sea
más ancho y de fondo tenga alguna imagen del saas difuminada." → this: a wide card
with a blurred `Hero.png` behind the copy. A full-bleed red slab was a lot of red
right before the (also red-accented) footer.

## Verification
- `cd landing && pnpm build` — green, 10 pages.
- Dev server in Chrome: the home's pre-footer CTA is a wide `max-w-6xl` bordered
  card with a blurred product screenshot behind the copy (`opacity ~.28` light /
  `.24` dark under a card scrim); text legible; "Registrarme gratis" + "o mirá los
  planes →". Renders correctly in light and dark (toggled). No horizontal page
  overflow.
- No file outside `landing/` touched.
