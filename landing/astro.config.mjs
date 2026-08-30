// @ts-check
import { defineConfig } from 'astro/config';

import react from '@astrojs/react';
import tailwindcss from '@tailwindcss/vite';

import sitemap from '@astrojs/sitemap';

// https://astro.build/config
export default defineConfig({
  site: 'https://ember.vanter.net',
  i18n: {
    locales: ['es', 'en'],
    defaultLocale: 'es',
    routing: { prefixDefaultLocale: false }
  },
  integrations: [
    react(),
    sitemap({
      // Emit <xhtml:link rel="alternate" hreflang> pairs for the es (unprefixed)
      // and en (/en/) versions of each page.
      i18n: {
        defaultLocale: 'es',
        locales: { es: 'es', en: 'en' }
      },
      // Keep 404 pages out of the sitemap.
      filter: (page) => !page.includes('/404')
    })
  ],
  server: { port: 5174 },
  vite: {
    plugins: [tailwindcss()]
  }
});