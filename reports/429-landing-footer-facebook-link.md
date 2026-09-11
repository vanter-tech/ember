# Report 429

**Task ID:** ad-hoc — Landing footer: Facebook link
**Predecessor Task:** report 428 — Settings "Reporta un problema" floating action

## Objective
Add a Facebook icon link to the landing page footer, pointing to the Ember Facebook page.

## Modified Files
- `landing/src/components/Footer.astro`
- `landing/src/i18n/ui.ts`

## What Changed?
`Footer.astro`'s first column (Ember logo + tagline) gains a circular icon link below the tagline — an inline Facebook glyph `<svg>` (no icon library dependency, matching the rest of the landing's inline-SVG icon convention), `target="_blank" rel="noreferrer"`, `href="https://www.facebook.com/profile.php?id=61594385545934"`, `aria-label={t('footer.facebook')}`. New i18n key `footer.facebook` in both `es`/`en` blocks of `ui.ts` ("Síguenos en Facebook" / "Follow us on Facebook").

## Why It Changed?
User provided the restaurant's Facebook page URL and asked for a footer link so visitors can find the social page from the landing site.

## Verification
`cd landing && pnpm run build` — clean, 28 pages built (unchanged page count — no new routes).
