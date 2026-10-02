import { useEffect, useRef, useState } from 'react';
import { useTranslations, localizePath } from '../i18n/utils';
import type { Lang } from '../i18n/ui';

const CONSENT_KEY = 'ember-cookie-consent';
export const CONSENT_EVENT = 'ember:cookie-consent';

const SECTIONS = ['essential', 'plausible', 'meta', 'withdraw'] as const;

export default function CookieBanner({ lang = 'es' }: { lang?: Lang }) {
  const t = useTranslations(lang);
  const [visible, setVisible] = useState(false);
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    try {
      if (localStorage.getItem(CONSENT_KEY) !== 'accepted') setVisible(true);
    } catch {
      setVisible(true);
    }
  }, []);

  const accept = () => {
    try {
      localStorage.setItem(CONSENT_KEY, 'accepted');
    } catch {
      // Storage blocked: the pixel still loads for this page view.
    }
    window.dispatchEvent(new Event(CONSENT_EVENT));
    dialogRef.current?.close();
    setVisible(false);
  };

  if (!visible) return null;

  return (
    <>
      <div
        role="region"
        aria-label={t('cookie.aria')}
        className="fixed inset-x-4 bottom-20 z-50 md:bottom-4 flex flex-col gap-4 rounded-lg border border-border bg-card p-4 shadow-md sm:inset-x-auto sm:left-4 sm:max-w-md"
      >
        <p className="text-sm text-muted-foreground">{t('cookie.text')}</p>
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={accept}
            className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm transition-colors hover:bg-primary/90"
          >
            {t('cookie.accept')}
          </button>
          <button
            type="button"
            onClick={() => dialogRef.current?.showModal()}
            className="rounded-lg px-3 py-2 text-sm font-medium text-foreground underline underline-offset-2 transition-colors hover:bg-muted"
          >
            {t('cookie.info')}
          </button>
        </div>
      </div>

      <dialog
        ref={dialogRef}
        aria-labelledby="cookie-modal-title"
        onClick={(e) => {
          if (e.target === dialogRef.current) dialogRef.current?.close();
        }}
        className="m-auto max-h-[85vh] w-[min(92vw,40rem)] overflow-y-auto rounded-xl border border-border bg-card p-0 text-foreground shadow-lg backdrop:bg-black/60"
      >
        <div className="sticky top-0 flex items-center justify-between gap-4 border-b border-border bg-card px-5 py-3">
          <h2 id="cookie-modal-title" className="text-sm font-semibold text-card-foreground">
            {t('cookie.modal.title')}
          </h2>
          <button
            type="button"
            onClick={() => dialogRef.current?.close()}
            aria-label={t('cookie.modal.close')}
            className="flex size-8 items-center justify-center rounded-md text-xl leading-none text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          >
            &times;
          </button>
        </div>
        <div className="space-y-5 px-5 py-5">
          <p className="text-sm text-muted-foreground">{t('cookie.modal.intro')}</p>
          {SECTIONS.map((key) => (
            <section key={key}>
              <h3 className="text-sm font-semibold text-card-foreground">
                {t(`cookie.modal.${key}.title`)}
              </h3>
              <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
                {t(`cookie.modal.${key}.body`)}
              </p>
            </section>
          ))}
          <a
            href={localizePath('/privacy', lang)}
            className="inline-block text-sm font-medium text-foreground underline underline-offset-2"
          >
            {t('cookie.modal.privacy')}
          </a>
        </div>
        <div className="flex justify-end gap-2 border-t border-border px-5 py-3">
          <button
            type="button"
            onClick={() => dialogRef.current?.close()}
            className="rounded-lg px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-muted"
          >
            {t('cookie.modal.close')}
          </button>
          <button
            type="button"
            onClick={accept}
            className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm transition-colors hover:bg-primary/90"
          >
            {t('cookie.accept')}
          </button>
        </div>
      </dialog>
    </>
  );
}
