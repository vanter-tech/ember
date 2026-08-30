import { useEffect, useState } from 'react';
import { useTranslations, localizePath } from '../i18n/utils';
import type { Lang } from '../i18n/ui';

const CONSENT_KEY = 'ember-cookie-consent';

export default function CookieBanner({ lang = 'es' }: { lang?: Lang }) {
  const t = useTranslations(lang);
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
      aria-label={lang === 'en' ? 'Cookie notice' : 'Aviso de cookies'}
      className="fixed inset-x-4 bottom-4 z-50 flex flex-col gap-4 rounded-lg border border-border bg-card p-4 shadow-md sm:inset-x-auto sm:left-4 sm:max-w-sm sm:flex-row sm:items-center"
    >
      <p className="text-sm text-muted-foreground">
        {t('cookie.text')}{' '}
        <a
          href={localizePath('/privacy', lang)}
          className="font-medium text-foreground underline underline-offset-2"
        >
          {t('cookie.link')}
        </a>
        .
      </p>
      <button
        type="button"
        onClick={accept}
        className="shrink-0 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm transition-colors hover:bg-primary/90"
      >
        {t('cookie.accept')}
      </button>
    </div>
  );
}
