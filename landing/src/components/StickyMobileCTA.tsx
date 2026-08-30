import { useEffect, useState } from 'react';
import { FRONTEND_URL } from '../lib/constants';
import { useTranslations } from '../i18n/utils';
import type { Lang } from '../i18n/ui';

export default function StickyMobileCTA({ lang = 'es' }: { lang?: Lang }) {
  const t = useTranslations(lang);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const onScroll = () => {
      setVisible(window.scrollY > window.innerHeight * 0.9);
    };

    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  if (!visible) return null;

  return (
    <div
      role="region"
      aria-label={lang === 'en' ? 'Quick actions' : 'Acciones rápidas'}
      className="fixed inset-x-0 bottom-0 z-50 flex gap-3 border-t border-border bg-background/95 p-3 shadow-lg backdrop-blur md:hidden"
    >
      <a
        href={`${FRONTEND_URL}/login`}
        className="flex-1 rounded-lg border border-border bg-background px-4 py-3 text-center text-sm font-medium text-foreground transition-colors hover:bg-muted"
      >
        {t('sticky.login')}
      </a>
      <a
        href={`${FRONTEND_URL}/register`}
        className="flex-1 rounded-lg bg-primary px-4 py-3 text-center text-sm font-medium text-primary-foreground shadow-sm transition-colors hover:bg-primary/90"
      >
        {t('sticky.register')}
      </a>
    </div>
  );
}
