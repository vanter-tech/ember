# Report 87 — EMB-LP-04

## Identification
- Report number: 87
- Task ID: EMB-LP-04
- Predecessor Task: EMB-LP-03 (report 86)

## Objective
Define the brutalist Tailwind 4 theme tokens for the `landing` package: palette, hard-shadow, zero-radius, and font-stack tokens for later components to consume.

## Modified Files
- `landing/src/styles/global.css`

## What Changed?
Added a Tailwind 4 `@theme` block after the existing `@import "tailwindcss"`:
- Colors: `--color-background: #f5f5f0`, `--color-foreground: #0a0a0a`, `--color-accent: #8c1717`.
- All `--radius-*` scale steps set to `0px`.
- `--shadow-brutal: 6px 6px 0 0 #000` and a smaller `--shadow-brutal-sm: 3px 3px 0 0 #000`.
- `--border-width-thick: 3px` / `--border-width-heavy: 4px`.
- `--font-sans` (Inter/Geist fallback chain) and `--font-mono` (system monospace chain).

## Why It Changed?
`landing` has no `tailwind.config.*` (Tailwind 4 CSS-first config, per EMB-LP-01), so theme tokens belong in `global.css`'s `@theme` block. This is the palette/shadow/radius/type foundation the spec calls for; EMB-LP-05 onward will consume these tokens instead of hardcoding values.
