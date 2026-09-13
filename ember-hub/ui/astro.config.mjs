// @ts-check
import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import tailwindcss from '@tailwindcss/vite';

// Static single-page build loaded directly by Tauri's WebView2 (no i18n/sitemap needed —
// single-tenant local app, Spanish-only, matches the rest of Hub/agent UI).
export default defineConfig({
  integrations: [react()],
  server: { port: 5176 },
  vite: {
    plugins: [tailwindcss()]
  }
});
