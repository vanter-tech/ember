const frontendUrl = import.meta.env.PUBLIC_FRONTEND_URL;

// Every register/login CTA points at this origin. If a production build runs
// without it, the whole site ships links to localhost — surface it loudly in
// the build log instead of failing silently.
if (import.meta.env.PROD && !frontendUrl) {
  console.warn(
    '\n⚠️  PUBLIC_FRONTEND_URL is not set — every register/login CTA will point to http://localhost:5173.\n',
  );
}

export const FRONTEND_URL = frontendUrl ?? 'http://localhost:5173';

export const NAV_LINKS = [
  { href: '/funcionalidades', key: 'nav.features' },
  { href: '/planes', key: 'nav.pricing' },
  { href: '/info', key: 'nav.info' },
  { href: '/contacto', key: 'nav.contact' }
];
