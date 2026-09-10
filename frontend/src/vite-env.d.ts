/// <reference types="vite/client" />

interface ImportMetaEnv {
  // Set by `pnpm build:hub` (and the Tauri Hub shell). "true" ⇒ the Hub-bundled SPA.
  readonly VITE_HUB_BUILD?: string
  // Public download URL for the Ember Agent Windows installer (.exe). Falls back to the
  // downloads.ember.vanter.net default in PrintingSettings when unset.
  readonly VITE_AGENT_DOWNLOAD_URL?: string
}
