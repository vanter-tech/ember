const frontendUrl = import.meta.env.PUBLIC_FRONTEND_URL;

// Every register/login CTA points at this origin. An explicit PUBLIC_FRONTEND_URL
// always wins; otherwise a production build targets the hosted SPA and a dev
// build targets the local Vite server. Warn when PROD is falling back so an
// unexpected deploy target is visible in the build log.
const FALLBACK_FRONTEND_URL = import.meta.env.PROD
  ? 'https://app.ember.vanter.net'
  : 'http://localhost:5173';

if (import.meta.env.PROD && !frontendUrl) {
  console.warn(
    `\n⚠️  PUBLIC_FRONTEND_URL is not set — every register/login CTA will point to ${FALLBACK_FRONTEND_URL}.\n`,
  );
}

export const FRONTEND_URL = frontendUrl ?? FALLBACK_FRONTEND_URL;

export const NAV_LINKS = [
  { href: '/funcionalidades', key: 'nav.features' },
  { href: '/planes', key: 'nav.pricing' },
  { href: '/info', key: 'nav.info' },
  { href: '/contacto', key: 'nav.contact' }
];
