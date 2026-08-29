import { useState, type FormEvent } from 'react';

interface FormErrors {
  name?: string;
  email?: string;
  message?: string;
  form?: string;
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function ContactForm() {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState('');
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitting, setSubmitting] = useState(false);

  const validate = (): FormErrors => {
    const next: FormErrors = {};
    if (!name.trim()) next.name = 'Ingresá tu nombre.';
    if (!email.trim()) next.email = 'Ingresá tu correo.';
    else if (!EMAIL_RE.test(email)) next.email = 'Ingresá un correo válido.';
    if (!message.trim()) next.message = 'Contanos brevemente qué necesitás.';
    return next;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const validationErrors = validate();
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    setErrors({});
    setSubmitting(true);
    try {
      const res = await fetch('/api/contact', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: name.trim(), email: email.trim(), message: message.trim() }),
      });
      if (!res.ok) throw new Error(String(res.status));
      window.location.href = '/thank-you';
    } catch {
      setSubmitting(false);
      setErrors({ form: 'No pudimos enviar tu mensaje. Intentá de nuevo.' });
    }
  };

  const fieldClass = (hasError: boolean) =>
    [
      'w-full border-[3px] bg-background px-4 py-3 font-medium outline-none focus-visible:ring-2 focus-visible:ring-accent',
      hasError ? 'border-accent' : 'border-foreground',
    ].join(' ');

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5">
      <div>
        <label htmlFor="contact-name" className="font-mono text-xs font-bold uppercase tracking-widest">
          Nombre
        </label>
        <input
          id="contact-name"
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          aria-invalid={Boolean(errors.name)}
          aria-describedby={errors.name ? 'contact-name-error' : undefined}
          className={`mt-2 ${fieldClass(Boolean(errors.name))}`}
        />
        {errors.name && (
          <p id="contact-name-error" role="alert" className="mt-2 text-sm font-bold text-accent">
            {errors.name}
          </p>
        )}
      </div>

      <div>
        <label htmlFor="contact-email" className="font-mono text-xs font-bold uppercase tracking-widest">
          Correo
        </label>
        <input
          id="contact-email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          aria-invalid={Boolean(errors.email)}
          aria-describedby={errors.email ? 'contact-email-error' : undefined}
          className={`mt-2 ${fieldClass(Boolean(errors.email))}`}
        />
        {errors.email && (
          <p id="contact-email-error" role="alert" className="mt-2 text-sm font-bold text-accent">
            {errors.email}
          </p>
        )}
      </div>

      <div>
        <label htmlFor="contact-message" className="font-mono text-xs font-bold uppercase tracking-widest">
          Mensaje
        </label>
        <textarea
          id="contact-message"
          rows={4}
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          aria-invalid={Boolean(errors.message)}
          aria-describedby={errors.message ? 'contact-message-error' : undefined}
          className={`mt-2 resize-none ${fieldClass(Boolean(errors.message))}`}
        />
        {errors.message && (
          <p id="contact-message-error" role="alert" className="mt-2 text-sm font-bold text-accent">
            {errors.message}
          </p>
        )}
      </div>

      {errors.form && (
        <p role="alert" className="border-[3px] border-accent bg-accent/10 p-3 text-sm font-bold text-accent">
          {errors.form}
        </p>
      )}

      <button
        type="submit"
        disabled={submitting}
        className="flex items-center justify-center gap-3 border-[3px] border-foreground bg-accent px-6 py-4 text-sm font-black uppercase tracking-wide text-background shadow-brutal disabled:cursor-not-allowed disabled:opacity-70"
      >
        {submitting && (
          <span
            aria-hidden="true"
            className="h-4 w-4 animate-spin rounded-[50%] border-2 border-background/40 border-t-background"
          />
        )}
        {submitting ? 'Enviando…' : 'Enviar mensaje'}
      </button>
    </form>
  );
}
