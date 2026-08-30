# Report 315 — landing: real content for /info/manual and /info/videos

## 1. Identification
- **Report number:** 315
- **Current Task:** flesh out the Info section (manual + videos copy)
- **Predecessor Task:** report 314 (landing-sitemap-i18n)
- **Branch:** `feat/hpd-14-monitoring` (ad-hoc landing work, track of reports 287–314)

## 2. Objective
`/info/manual` was a "Borrador" with one-line stubs; `/info/videos` had bare
"Próximamente" placeholders. Both were indexable. Write real content: an expanded
per-module reference manual (+ a Troubleshooting section) and polished video-card
copy, plus a recording-script document for the maintainer.

## 3. Modified Files
- `landing/src/i18n/ui.ts` — rewrote `manual.lede`, expanded `manual.s1..s9` bodies,
  added `manual.s10` (Troubleshooting), polished `videos.lede` + `videos.1..6`; all
  in **es and en**.
- `landing/src/pages/info/manual.astro` — added the `solucion-problemas` id;
  section body now renders one `<p>` per `\n`-separated line (single-line sections
  unchanged, the multi-item Troubleshooting section reads as a list).
- `landing/docs/video-guiones.md` — new: recording scripts for the 6 videos +
  hosting guidance. Production aid, not shipped to the site.

## 4. What Changed?
**Manual** — each of the 9 modules went from a one-sentence stub to a 3–5 sentence
reference at a conceptual / workflow altitude (what it does, which role, the
typical flow, one key detail), grounded in real features (setup wizard, 4 roles,
live floor map, QR / 5-char code, KDS states + overdue flag, cash close with
reconciliation + overdue-shift warning + extend, print agent for network /
Windows-queue / ESC-POS / driver printers, catalog + settings with per-section
guided tours, analytics by period / product / table / server). Deliberately no
button names or menu paths — those rot without screenshots. New **s10
Troubleshooting**: QR join, printer not printing, order not reaching the kitchen,
"cash shift overdue", and missing module/action (role access) — five short items,
rendered as separate paragraphs.

**Videos** — `videos.lede` reframed (honest "we publish as they're ready" + points
to the manual meanwhile); the 6 card descriptions rewritten to say concretely what
each clip will show. No structural change, no `noindex` (per the maintainer's
choice) — the `embeds[]` array in `videos.astro` still holds 6 `null`s with the
existing comment explaining how to publish one.

**`docs/video-guiones.md`** — per video: objective, target length (60–120 s),
on-screen steps, spoken narration, notes; plus general recording guidance (demo
data only, resolution, pace) and a **hosting** section: recommend a YouTube channel
(public or unlisted), how to get the `youtube.com/embed/ID` URL, where to paste it
(`videos.astro` `embeds[]`), why video files must not live in the repo (size, no
CDN, Pages deploy limit), and alternatives (Vimeo — already supported; Cloudflare
Stream — paid). Notes that `VideoObject` JSON-LD is a later add-on once videos
exist.

## 5. Why It Changed?
Thin placeholder pages that are indexable dilute site quality and give Google
nothing to rank; a real manual is also the kind of substantive content that can
earn long-tail traffic. Keeping the manual conceptual rather than click-by-click
keeps it accurate and low-maintenance until there are screenshots. The video page
stays honest about not having videos yet while pointing users to the written
coverage, and the maintainer now has everything needed to record and self-publish.

## Verification
- `cd landing && pnpm run build` — green, 20 pages.
- `grep` on `dist/`: no "Borrador" / "Draft — it will grow…" anywhere.
- `/info/manual` (es + en): 10 `<h2>` sections, last one
  "Solución de problemas" / "Troubleshooting"; node check confirms that section
  renders as 5 separate `<p>` items while single-line sections stay one `<p>`.
