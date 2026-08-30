# Report 316 — landing: click-to-open video modal on /info/videos

## 1. Identification
- **Report number:** 316
- **Current Task:** /info/videos — modal video viewer
- **Predecessor Task:** report 315 (landing-info-manual-videos-content)
- **Branch:** `feat/hpd-14-monitoring` (ad-hoc landing work, track of reports 287–315)

## 2. Objective
`/info/videos` embedded a fixed `<iframe>` per card (6 iframes on the page). Replace
that with a thumbnail grid where each card opens the video in a modal on click, so
nothing loads from YouTube until the user asks for it, and the player is bigger.
Chrome matches the site's tokens.

## 3. Modified Files
- `landing/src/pages/info/videos.astro` — data model, card markup, `<dialog>` modal,
  inline open/close script, scoped dialog styles
- `landing/src/i18n/ui.ts` — `videos.play` / `videos.close` (es + en)
- `landing/docs/video-guiones.md` — updated the "Dónde subir" steps for the new
  `ids` array / modal behavior

## 4. What Changed?
- **Data model:** `embeds: (string|null)[]` (embed URLs) → `ids: (string|null)[]`.
  A `resolveVideo()` helper accepts a bare YouTube ID, a full YouTube URL
  (`watch?v=` / `youtu.be` / `embed`), a Vimeo URL, or falls back to using the
  string as-is. For YouTube it derives the thumbnail
  (`i.ytimg.com/vi/<id>/hqdefault.jpg`) and a **`youtube-nocookie.com/embed/<id>?autoplay=1&rel=0`**
  URL. All six entries stay `null` for now → the card keeps its unchanged
  "Próximamente" placeholder.
- **Card:** a resolved video renders as a `<button data-video-open>` filling the
  `aspect-video` box — the YouTube thumbnail (`object-cover`), a `bg-black/20`
  hover-darken layer, and a centered `bg-primary` play circle that scales up on
  hover. `aria-label` = `"<play>: <title>"`.
- **Modal:** one native `<dialog id="video-modal">` per page —
  `rounded-xl border border-border bg-card shadow-lg`, a header bar
  (`border-b border-border`, title + `×` close button styled like the nav/FAQ icon
  buttons), and a `bg-black` `aspect-video` slot. `::backdrop` tinted
  `rgb(0 0 0 / 0.6)` via an `is:global` style. Native `<dialog>` provides the focus
  trap, scroll lock and Esc-to-close.
- **Script** (`is:inline`, bound on `astro:page-load` like `Nav.astro`): on
  open, builds an `<iframe>` with `document.createElement` (no innerHTML) and calls
  `showModal()`. On close it removes the iframe to stop playback — and does so on
  **every** close path (`close` event, `cancel` event, an explicit Escape
  `keydown` handler, the `×` button, and a backdrop click), not only the `close`
  event, which was observed not to fire reliably under automation.
- `videos.play` / `videos.close` i18n keys added (es + en).

## 5. Why It Changed?
Six always-loaded YouTube iframes are heavy and set third-party state on page
load. Click-to-load in `nocookie` mode means the page ships zero YouTube requests
until a viewer chooses to watch, the grid stays light, and the player gets the full
modal width. Native `<dialog>` keeps the accessibility handling out of custom code.

## Verification
- `cd landing && pnpm run build` — green, 20 pages. With all `ids` null, the built
  `/info/videos` page contains **0** `<iframe>` elements and 6 "Próximamente"
  placeholders; the `<dialog>` and script are present for when IDs are added.
- Browser test (temporary real YouTube ID in slot 1, reverted before commit):
  card showed the thumbnail + primary play button; clicking opened the modal
  centered over a dimmed backdrop with the token-styled header and a working
  `nocookie` player; **Esc** and the **×** button both closed the modal *and*
  removed the iframe (`open === false`, `frame` has no `<iframe>`), confirming
  playback stops; reopening worked.
