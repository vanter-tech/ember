// True only in the Hub-bundled SPA (on-prem, waiter-driven, no customer flow).
//
// `VITE_HUB_BUILD` is the canonical signal — `pnpm build:hub` sets it, and so will the Tauri
// shell (Hub v2), which loads from tauri://localhost where BASE_URL is "/". The BASE_URL check
// stays as a fallback for the current `vite build --base=/app/` output served by Spring Boot,
// and can be dropped once Tauri fully replaces that path.
//
// It is a function, read at render time — never at module load — so tests flip it with a plain
// `vi.mock('@/lib/isHubBuild', () => ({ isHubBuild: () => true }))`, no module-graph reset.
export const isHubBuild = (): boolean =>
  import.meta.env.VITE_HUB_BUILD === 'true' || import.meta.env.BASE_URL !== '/'
