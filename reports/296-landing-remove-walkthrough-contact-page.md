# Report 296 — landing-remove-walkthrough-contact-page

## 1. Identification
- **Report number:** 296
- **Task ID:** landing-remove-walkthrough-contact-page (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 295 (landing-scrollytelling-walkthrough)

## 2. Objective
Drop the scroll-zoom walkthrough (report 295) — the maintainer didn't want it — and
move "Contáctanos" onto its own route, like `/planes` and `/funcionalidades`.

## 3. Modified Files
- **Deleted** `landing/src/components/Walkthrough.astro`
- **Deleted** `landing/src/components/ContactSection.astro`
- **Deleted** `landing/src/components/ContactForm.tsx`
- **Deleted** `landing/functions/api/contact.ts` (and the now-empty `functions/`)
- **Deleted** `landing/src/pages/thank-you.astro`
- **New** `landing/src/pages/contacto.astro`
- `landing/src/pages/index.astro` — `<Walkthrough />` and `<ContactSection />` (and
  their imports) removed; home is now Hero → CTA → Footer.
- `landing/src/lib/constants.ts` — `NAV_LINKS` gains `{ href: '/contacto', label:
  'Contacto' }`.
- `landing/src/components/Footer.astro` — the "Contacto" link `mailto:…` →
  `/contacto`.

## 4. What Changed?
### Walkthrough removed
`Walkthrough.astro` and its `index.astro` usage are gone. `Hero.png` is still used
by `Hero.astro`; nothing else referenced the component.

### `/contacto` (info, no form)
`Nav` → a header (`Hablemos` eyebrow, `h1`, lede) → a `sm:grid-cols-2
lg:grid-cols-3` of three info cards, each with an icon chip: **Correo**
(`ventas@ember.vanter.com` `mailto:` + a one-line note), **Oficina** (Vanter S.A. /
RUC / Quito address), **Horario** (Mon–Fri 9–18 GMT−5) → a `bg-muted/40` CTA strip
"¿Preferís vernos en acción?" with a "Pedir una demo" `mailto:` button (prefilled
subject) → `Footer`. Standard `Layout` SEO (`title` "Contacto — Ember").

The maintainer asked for info rather than a form, so the page has **no
`ContactForm`**, and then asked to clean up the now-dead form files:
`ContactForm.tsx`, `landing/functions/api/contact.ts` (the whole `functions/` dir)
and `thank-you.astro` are **deleted**. Grep confirmed only `ContactForm.tsx`
referenced `/api/contact` and `/thank-you`, so nothing dangles.
**Deploy note:** this undoes the landing side of HPD-10 (report 282) — the
`/api/contact` Pages Function and its `CONTACT_WEBHOOK_URL` env var are no longer
used; the Cloudflare Pages project no longer needs that variable. The old
`ContactSection.astro` wrapper (only ever used on the home) is also deleted.
"Contacto" is added to the header nav so the three sub-pages (Funcionalidades /
Precios / Contacto) are all reachable there, and the footer link points at the page
instead of opening a mail client.

## 5. Why It Changed?
"No me convence, elimina todo eso" → the walkthrough is out. "La parte de
contáctanos debe de estar su propia vista con path" → contact follows the same
sub-page pattern as plans and features. "No busco poner un formulario, me gustaría
más mostrar info" → the page shows contact channels and details instead of a form.
The home keeps getting leaner (Hero + CTA + Footer); everything substantive now
lives on a routed page reachable from the nav.

## Verification
- `cd landing && pnpm build` — green. **7 pages** (`index`, `funcionalidades`,
  `planes`, `contacto`, `privacy`, `terms`, `404` — `thank-you` removed).
- `grep -rn "ContactForm|thank-you|/api/contact" src functions` → no matches after
  the deletions (build confirms nothing dangles).
- Dev server in Chrome: `/contacto` renders the three info cards (Correo / Oficina /
  Horario) + the demo CTA strip and **no `<form>`**; the nav "Contacto" link shows
  the active pill. The home has no `.walkthrough` and no contact section. No
  horizontal page overflow.
- No file outside `landing/` touched.
