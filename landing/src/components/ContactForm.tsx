import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useTranslations, localizePath } from '../i18n/utils';
import type { Lang } from '../i18n/ui';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Turnstile SITE key — public by design (it ships in the HTML). The matching
// SECRET key lives only as a Worker secret (TURNSTILE_SECRET_KEY). An env
// override is honoured so a fork/preview can point at its own widget.
const SITE_KEY =
  (import.meta.env.PUBLIC_TURNSTILE_SITE_KEY as string | undefined) ||
  '0x4AAAAAAEsaAHQ6XDMni_IM';
const HAS_CAPTCHA = Boolean(SITE_KEY);

interface TurnstileApi {
  render: (
    el: HTMLElement,
    opts: {
      sitekey: string;
      callback: (token: string) => void;
      'error-callback'?: () => void;
      'expired-callback'?: () => void;
      theme?: 'auto' | 'light' | 'dark';
      appearance?: 'always' | 'execute' | 'interaction-only';
    },
  ) => string;
  reset: (id?: string) => void;
}

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

let scriptPromise: Promise<void> | null = null;
function loadTurnstile(): Promise<void> {
  if (typeof window === 'undefined') return Promise.resolve();
  if (window.turnstile) return Promise.resolve();
  if (scriptPromise) return scriptPromise;
  scriptPromise = new Promise<void>((resolve, reject) => {
    const s = document.createElement('script');
    s.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
    s.async = true;
    s.defer = true;
    s.onload = () => resolve();
    s.onerror = () => reject(new Error('turnstile failed to load'));
    document.head.appendChild(s);
  });
  return scriptPromise;
}

interface FormErrors {
  name?: string;
  email?: string;
  message?: string;
  form?: string;
}

export default function ContactForm({ lang = 'es' }: { lang?: Lang }) {
  const t = useTranslations(lang);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState('');
  const [company, setCompany] = useState(''); // honeypot
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitting, setSubmitting] = useState(false);

  const widgetRef = useRef<HTMLDivElement>(null);
  const widgetId = useRef<string | null>(null);
  const token = useRef('');

  useEffect(() => {
    if (!HAS_CAPTCHA) return;
    let cancelled = false;
    loadTurnstile()
      .then(() => {
        if (cancelled || !widgetRef.current || !window.turnstile || widgetId.current) return;
        widgetId.current = window.turnstile.render(widgetRef.current, {
          sitekey: SITE_KEY as string,
          theme: 'auto',
          appearance: 'interaction-only',
          callback: (tk) => {
            token.current = tk;
          },
          'error-callback': () => {
            token.current = '';
          },
          'expired-callback': () => {
            token.current = '';
          },
        });
      })
      .catch(() => {
        /* network-blocked; submit will surface the captcha error */
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const validate = (): FormErrors => {
    const next: FormErrors = {};
    if (!name.trim()) next.name = t('cform.name.err');
    if (!email.trim()) next.email = t('cform.email.err');
    else if (!EMAIL_RE.test(email)) next.email = t('cform.email.invalid');
    if (!message.trim()) next.message = t('cform.message.err');
    return next;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const validationErrors = validate();
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }
    if (HAS_CAPTCHA && !token.current) {
      setErrors({ form: t('cform.captcha') });
      return;
    }

    setErrors({});
    setSubmitting(true);
    try {
      const res = await fetch('/api/contact', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: name.trim(),
          email: email.trim(),
          message: message.trim(),
          company: company.trim(),
          turnstileToken: token.current,
        }),
      });
      if (!res.ok) throw new Error(String(res.status));
      window.location.href = localizePath('/gracias', lang);
    } catch {
      setSubmitting(false);
      token.current = '';
      if (widgetId.current && window.turnstile) window.turnstile.reset(widgetId.current);
      setErrors({ form: t('cform.error') });
    }
  };

  const fieldClass = (hasError: boolean) =>
    [
      'mt-1.5 w-full rounded-lg border bg-background px-3.5 py-2.5 text-sm text-foreground shadow-sm outline-none transition-colors focus:ring-2 focus:ring-primary/20',
      hasError ? 'border-destructive focus:border-destructive' : 'border-border focus:border-primary',
    ].join(' ');

  const labelClass = 'text-sm font-medium text-foreground';

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5">
      <div className="grid gap-5 sm:grid-cols-2">
        <div>
          <label htmlFor="contact-name" className={labelClass}>
            {t('cform.name')}
          </label>
          <input
            id="contact-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoComplete="name"
            aria-invalid={Boolean(errors.name)}
            aria-describedby={errors.name ? 'contact-name-error' : undefined}
            className={fieldClass(Boolean(errors.name))}
          />
          {errors.name && (
            <p id="contact-name-error" role="alert" className="mt-1.5 text-sm text-destructive">
              {errors.name}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="contact-email" className={labelClass}>
            {t('cform.email')}
          </label>
          <input
            id="contact-email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            aria-invalid={Boolean(errors.email)}
            aria-describedby={errors.email ? 'contact-email-error' : undefined}
            className={fieldClass(Boolean(errors.email))}
          />
          {errors.email && (
            <p id="contact-email-error" role="alert" className="mt-1.5 text-sm text-destructive">
              {errors.email}
            </p>
          )}
        </div>
      </div>

      <div>
        <label htmlFor="contact-message" className={labelClass}>
          {t('cform.message')}
        </label>
        <textarea
          id="contact-message"
          rows={5}
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          aria-invalid={Boolean(errors.message)}
          aria-describedby={errors.message ? 'contact-message-error' : undefined}
          className={`resize-y ${fieldClass(Boolean(errors.message))}`}
        />
        {errors.message && (
          <p id="contact-message-error" role="alert" className="mt-1.5 text-sm text-destructive">
            {errors.message}
          </p>
        )}
      </div>

      {/* Honeypot — visually hidden, off the tab order, ignored by real users. */}
      <div aria-hidden="true" className="absolute -left-[9999px] h-0 w-0 overflow-hidden">
        <label htmlFor="contact-company">Company</label>
        <input
          id="contact-company"
          type="text"
          tabIndex={-1}
          autoComplete="off"
          value={company}
          onChange={(e) => setCompany(e.target.value)}
        />
      </div>

      {HAS_CAPTCHA && <div ref={widgetRef} />}

      {errors.form && (
        <p
          role="alert"
          className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive"
        >
          {errors.form}
        </p>
      )}

      <button
        type="submit"
        disabled={submitting}
        className="inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-5 py-3 text-sm font-medium text-primary-foreground shadow-sm transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-70"
      >
        {submitting && (
          <span
            aria-hidden="true"
            className="size-4 animate-spin rounded-full border-2 border-primary-foreground/40 border-t-primary-foreground"
          />
        )}
        {submitting ? t('cform.submitting') : t('cform.submit')}
      </button>
    </form>
  );
}
