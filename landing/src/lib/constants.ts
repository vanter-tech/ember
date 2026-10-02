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

// Windows installer downloads. An explicit PUBLIC_*_DOWNLOAD_URL always wins;
// otherwise both point at the public downloads host. Kept as -latest so the
// link never needs a version bump.
export const AGENT_DOWNLOAD_URL =
  import.meta.env.PUBLIC_AGENT_DOWNLOAD_URL ??
  'https://downloads.ember.vanter.net/EmberAgentSetup-latest.exe';

export const HUB_DOWNLOAD_URL =
  import.meta.env.PUBLIC_HUB_DOWNLOAD_URL ??
  'https://downloads.ember.vanter.net/EmberHubSetup-latest.exe';

// WhatsApp contact line (digits only, country code first — wa.me format).
export const WHATSAPP_NUMBER = '50557684337';
export const WHATSAPP_DISPLAY = '+505 5768-4337';

export const whatsappUrl = (message: string) =>
  `https://wa.me/${WHATSAPP_NUMBER}?text=${encodeURIComponent(message)}`;

// Counts a WhatsApp click as a Lead for the Meta Pixel (same event the contact form fires).
export const WHATSAPP_TRACK_ATTR = "window.fbq&&window.fbq('track','Lead')";

export const NAV_LINKS = [
  { href: '/funcionalidades', key: 'nav.features' },
  { href: '/planes', key: 'nav.pricing' },
  { href: '/info', key: 'nav.info' },
  { href: '/contacto', key: 'nav.contact' }
];
