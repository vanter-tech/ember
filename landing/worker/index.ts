/**
 * Cloudflare Worker entry for the Ember landing site.
 *
 * The Astro `dist/` build is served by the `ASSETS` binding. The only dynamic
 * route is `POST /api/contact`, which checks a Turnstile token and forwards the
 * message by email through Resend. Everything else falls through to the static
 * assets (and `not_found_handling: "404-page"` for unknown paths).
 *
 * Config:
 *   CONTACT_TO            plaintext var in wrangler.jsonc (survives `wrangler deploy`)
 *   RESEND_API_KEY        Secret  — ember Worker -> Settings -> Variables and Secrets
 *   TURNSTILE_SECRET_KEY  Secret  — same place
 * Dashboard-added *plaintext* vars are wiped by each Workers Builds deploy, so
 * CONTACT_TO lives in wrangler.jsonc; only the two secrets are set out of band.
 *
 * The Turnstile *site* key is public and lives in the client bundle
 * (PUBLIC_TURNSTILE_SITE_KEY at build time, or hard-coded in ContactForm.tsx).
 */

interface Env {
  ASSETS: { fetch: (request: Request) => Promise<Response> };
  RESEND_API_KEY?: string;
  CONTACT_TO?: string;
  TURNSTILE_SECRET_KEY?: string;
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function json(status: number, code: string): Response {
  return new Response(JSON.stringify({ error: code }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

async function verifyTurnstile(
  token: string,
  secret: string,
  ip: string | null,
): Promise<boolean> {
  const form = new FormData();
  form.append('secret', secret);
  form.append('response', token);
  if (ip) form.append('remoteip', ip);
  try {
    const res = await fetch('https://challenges.cloudflare.com/turnstile/v0/siteverify', {
      method: 'POST',
      body: form,
    });
    const data = (await res.json()) as { success?: boolean };
    return data.success === true;
  } catch {
    return false;
  }
}

async function handleContact(request: Request, env: Env): Promise<Response> {
  let body: {
    name?: string;
    email?: string;
    message?: string;
    company?: string;
    turnstileToken?: string;
  };
  try {
    body = await request.json();
  } catch {
    return json(400, 'bad_request');
  }

  // Honeypot: real users never fill a hidden field. Pretend it worked.
  if ((body.company ?? '').trim() !== '') {
    return new Response(null, { status: 204 });
  }

  const name = (body.name ?? '').trim();
  const email = (body.email ?? '').trim();
  const message = (body.message ?? '').trim();
  if (
    !name ||
    name.length > 120 ||
    !EMAIL_RE.test(email) ||
    email.length > 200 ||
    message.length < 5 ||
    message.length > 4000
  ) {
    return json(400, 'invalid');
  }

  const missing = [
    !env.TURNSTILE_SECRET_KEY && 'TURNSTILE_SECRET_KEY',
    !env.RESEND_API_KEY && 'RESEND_API_KEY',
    !env.CONTACT_TO && 'CONTACT_TO',
  ].filter(Boolean);
  if (missing.length) {
    return new Response(JSON.stringify({ error: 'not_configured', missing }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  const token = (body.turnstileToken ?? '').trim();
  const ip = request.headers.get('CF-Connecting-IP');
  if (!token || !(await verifyTurnstile(token, env.TURNSTILE_SECRET_KEY, ip))) {
    return json(400, 'captcha_failed');
  }

  const sent = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${env.RESEND_API_KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      from: 'Ember <onboarding@resend.dev>',
      to: [env.CONTACT_TO],
      reply_to: email,
      subject: `Contacto web — ${name}`,
      text: `Nombre: ${name}\nCorreo: ${email}\n\n${message}`,
    }),
  });
  if (!sent.ok) {
    return json(502, 'send_failed');
  }

  return new Response(null, { status: 204 });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === '/api/contact') {
      if (request.method !== 'POST') {
        return new Response('Method Not Allowed', {
          status: 405,
          headers: { Allow: 'POST' },
        });
      }
      return handleContact(request, env);
    }
    return env.ASSETS.fetch(request);
  },
};
