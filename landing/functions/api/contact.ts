interface Env {
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

export const onRequestPost: PagesFunction<Env> = async ({ request, env }) => {
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

	if (!env.TURNSTILE_SECRET_KEY || !env.RESEND_API_KEY || !env.CONTACT_TO) {
		return json(500, 'not_configured');
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
};
