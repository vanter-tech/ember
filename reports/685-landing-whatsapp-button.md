# Report 685 — LANDING-WHATSAPP-BUTTON

## 1. Identification
- **Report number:** 685
- **Task ID:** LANDING-WHATSAPP-BUTTON (ad hoc, Facebook Ads campaign support)
- **Predecessor task:** report 684 — CONSOLE-DEMO-PLAN

## 2. Objective
Give campaign visitors a lower-friction way to ask for a demo than writing an email: a WhatsApp button on every landing page, counted as a Lead by the Meta Pixel.

## 3. Modified Files
- `landing/src/components/WhatsAppButton.astro` (new)
- `landing/src/layouts/Layout.astro`
- `landing/src/lib/constants.ts`
- `landing/src/i18n/ui.ts`
- `landing/src/pages/contacto.astro`
- `PROGRESS.md`

## 4. What Changed?
- New `WhatsAppButton.astro`: round green floating button (bottom-right, `bottom-20` on mobile so it clears the home page's sticky bar, `md:bottom-6` on desktop), rendered once in `Layout.astro` so it appears on every page in ES and EN. Opens `wa.me/50557684337` in a new tab with a pre-filled message ("Hola, quiero una demo de Ember" / EN equivalent).
- `constants.ts`: `WHATSAPP_NUMBER`, `WHATSAPP_DISPLAY`, `whatsappUrl()` and `WHATSAPP_TRACK_ATTR` (an inline `onclick` that calls `fbq('track','Lead')` when the Pixel is loaded). An inline attribute was used instead of a script so it survives the `ClientRouter` page swaps without stacking duplicate listeners.
- `contacto.astro`: new WhatsApp channel card (first of the channel grid, now 4 columns on large screens) and the "Pedir una demo" button now opens WhatsApp instead of a `mailto:`. Both fire the same Lead event.
- `ui.ts`: new keys `cpage.wa.title`, `cpage.wa.body`, `wa.label`, `wa.message` in ES and EN.
- Verification: landing `pnpm run build` exit 0, 28 pages; the built HTML contains `wa.me/50557684337` and the Lead tracking on the home, contact (floating button + card + demo button = 3), EN contact and info pages, and the old `mailto` demo link is gone.

## 5. Why It Changed?
Writing an email is a lot of friction for a restaurant owner reaching the site from a Facebook ad; WhatsApp is the usual channel in Nicaragua. The click fires the same `Lead` event as the contact form so the campaign counts both paths. A click is a weaker signal than a submitted form and can inflate the Lead count slightly; the real number of conversations is in WhatsApp itself.

Not verified: the button was not looked at in a browser (layout/overlap on mobile, dark theme) and the Lead event was not checked in Meta's Test Events tool. It goes live when this reaches `main` (Cloudflare builds the landing).
