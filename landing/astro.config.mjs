// @ts-check
import { defineConfig } from 'astro/config';

import react from '@astrojs/react';
import tailwindcss from '@tailwindcss/vite';

import sitemap from '@astrojs/sitemap';

// https://astro.build/config
export default defineConfig({
  site: 'https://ember.vanter.net',
  integrations: [react(), sitemap()],
  server: { port: 5174 },
  vite: {
    plugins: [tailwindcss()]
  }
});