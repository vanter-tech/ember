import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { FRONTEND_URL, NAV_LINKS } from '../lib/constants';
import { useTranslations, localizePath } from '../i18n/utils';
import type { Lang } from '../i18n/ui';

export default function MobileNavDrawer({ lang = 'es' }: { lang?: Lang }) {
  const t = useTranslations(lang);
  const [open, setOpen] = useState(false);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (!open) return;

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };

    document.addEventListener('keydown', onKeyDown);
    document.body.style.overflow = 'hidden';

    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = '';
    };
  }, [open]);

  const bar = 'block h-0.5 w-5 rounded-full bg-foreground transition-all duration-300 motion-reduce:transition-none';

  const overlay = (
    <div
      className={`fixed inset-0 z-[60] transition-opacity duration-300 motion-reduce:transition-none ${
        open ? 'opacity-100' : 'pointer-events-none opacity-0'
      }`}
      inert={!open}
    >
      <button
        type="button"
        aria-label={t('nav.closeMenu')}
        onClick={() => setOpen(false)}
        className="absolute inset-0 bg-black/50"
      />

      <div
        className={`absolute inset-y-0 right-0 flex w-[80%] max-w-sm flex-col gap-8 border-l border-border bg-background p-6 shadow-xl transition-transform duration-300 ease-out motion-reduce:transition-none ${
          open ? 'translate-x-0' : 'translate-x-full'
        }`}
      >
        <button
          type="button"
          onClick={() => setOpen(false)}
          aria-label={t('nav.closeMenu')}
          className="ml-auto flex h-10 w-10 items-center justify-center rounded-lg border border-border text-2xl text-foreground transition-colors hover:bg-muted"
        >
          &times;
        </button>

        <nav className="flex flex-col gap-1">
          {NAV_LINKS.map((link) => (
            <a
              key={link.href}
              href={localizePath(link.href, lang)}
              onClick={() => setOpen(false)}
              className="-mx-2 rounded-md px-2 py-2.5 text-lg font-medium text-foreground transition-colors hover:bg-muted"
            >
              {t(link.key)}
            </a>
          ))}
        </nav>

        <div className="mt-auto flex flex-col gap-3">
          <a
            href={`${FRONTEND_URL}/login`}
            className="rounded-lg border border-border bg-background px-4 py-3 text-center font-medium text-foreground transition-colors hover:bg-muted"
          >
            {t('nav.login')}
          </a>
          <a
            href={`${FRONTEND_URL}/register`}
            className="rounded-lg bg-primary px-4 py-3 text-center font-medium text-primary-foreground shadow-sm transition-colors hover:bg-primary/90"
          >
            {t('nav.register')}
          </a>
        </div>
      </div>
    </div>
  );

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={open ? t('nav.closeMenu') : t('nav.openMenu')}
        aria-expanded={open}
        className="flex h-10 w-10 flex-col items-center justify-center gap-1.5 rounded-lg border border-border bg-background"
      >
        <span className={`${bar} ${open ? 'translate-y-2 rotate-45' : ''}`} />
        <span className={`${bar} ${open ? 'opacity-0' : ''}`} />
        <span className={`${bar} ${open ? '-translate-y-2 -rotate-45' : ''}`} />
      </button>

      {mounted ? createPortal(overlay, document.body) : null}
    </>
  );
}
