import { useEffect, useState } from 'react';

const CONSENT_KEY = 'ember-cookie-consent';

export default function CookieBanner() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (localStorage.getItem(CONSENT_KEY) !== 'accepted') {
      setVisible(true);
    }
  }, []);

  const accept = () => {
    localStorage.setItem(CONSENT_KEY, 'accepted');
    setVisible(false);
  };

  if (!visible) return null;

  return (
    <div
      role="region"
      aria-label="Aviso de cookies"
      className="fixed inset-x-4 bottom-4 z-50 flex flex-col gap-4 border-[3px] border-foreground bg-background p-4 shadow-brutal sm:inset-x-auto sm:left-4 sm:max-w-sm sm:flex-row sm:items-center"
    >
      <p className="text-sm font-medium">
        Usamos cookies esenciales para el funcionamiento del sitio. Consulta nuestra{' '}
        <a href="/privacy" className="underline">
          Política de Privacidad
        </a>
        .
      </p>
      <button
        type="button"
        onClick={accept}
        className="shrink-0 border-[3px] border-foreground bg-accent px-4 py-2 text-sm font-black uppercase tracking-wide text-background shadow-brutal-sm"
      >
        Aceptar
      </button>
    </div>
  );
}
