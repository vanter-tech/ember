# Report 603 — STRIP-SHIPPED-HTML-COMMENTS

## 1. Identification
- Report: 603
- Task ID: STRIP-SHIPPED-HTML-COMMENTS
- Predecessor: LANDING-LEGAL-IDENTIFICATION (report 602)

## 2. Objective
Stop shipping internal-architecture HTML comments to production (found via manual pentest, "view source" on the app).

## 3. Modified Files
- `frontend/index.html`
- `frontend/scripts/gen-env-config.mjs`
- `landing/src/components/Compare.astro`

## 4. What Changed?
- Removed the favicon comment and the long `env-config.js` rationale comment from `frontend/index.html` (both shipped verbatim to every visitor, since Vite/esbuild does not strip HTML comments and no source maps are published).
- Moved the `env-config.js` relative-src rationale into `gen-env-config.mjs` (a build script, never shipped), merged with the Cloudflare-vs-Hub explanation already there.
- `landing/src/components/Compare.astro`: its two layout comments (`<!-- Desktop... -->`, `<!-- Mobile... -->`) become `{/* ... */}` JSX-style comments, which Astro strips at build time instead of shipping.
- Not touched: `<!--astro:end-->` markers (30 across the landing's pages) — Astro's own island hydration boundaries, not ours, required at runtime.

## 5. Why It Changed?
Info-disclosure finding, not critical (no secrets/credentials), but unnecessary: it told a visitor the app has two build variants (Cloud/Hub), the env-injection mechanism, and the reasoning behind it. Source maps were already absent in all three dist/ builds checked (frontend, landing, ember-hub/ui) — this closes the only other spot a comment was ending up in HTML.

Verification: `frontend`: `pnpm run build` clean, generated `dist/index.html` has 0 `<!--` comments, `pnpm run lint` 0 errors (15 pre-existing warnings); manually ran `gen-env-config.mjs` against the build output to confirm it still rewrites the script src correctly (Cloudflare path). `landing`: `pnpm run build` clean, `dist/**/*.html` has 0 non-`astro:end` comments left.

Scope note: this is step 1 of 2 agreed with the owner (shipped comments only). Step 2 — a source-level pass trimming comments that narrate rather than explain a non-obvious "why" — is deferred, to be scoped separately per module.
