/// <reference types="vite/client" />

interface ImportMetaEnv {
  // Set by `pnpm build:hub` (and the Tauri Hub shell). "true" ⇒ the Hub-bundled SPA.
  readonly VITE_HUB_BUILD?: string
}
