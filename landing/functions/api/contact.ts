interface Env {
  CONTACT_WEBHOOK_URL?: string;
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const onRequestPost: PagesFunction<Env> = async ({ request, env }) => {
  let body: { name?: string; email?: string; message?: string };
  try {
    body = await request.json();
  } catch {
    return new Response('bad json', { status: 400 });
  }
  const name = (body.name ?? '').trim();
  const email = (body.email ?? '').trim();
  const message = (body.message ?? '').trim();
  if (!name || !message || !EMAIL_RE.test(email)) {
    return new Response('invalid', { status: 400 });
  }
  if (env.CONTACT_WEBHOOK_URL) {
    await fetch(env.CONTACT_WEBHOOK_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text: `Ember contacto\nNombre: ${name}\nCorreo: ${email}\n\n${message}` }),
    });
  }
  return new Response(null, { status: 204 });
};
