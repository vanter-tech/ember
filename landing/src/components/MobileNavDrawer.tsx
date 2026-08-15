import { useEffect, useState } from 'react';
import { FRONTEND_URL, NAV_LINKS } from '../lib/constants';

export default function MobileNavDrawer() {
  const [open, setOpen] = useState(false);

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

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="Abrir menú"
        aria-expanded={open}
        className="flex h-11 w-11 flex-col items-center justify-center gap-1.5 border-[3px] border-foreground bg-background"
      >
        <span className="h-0.5 w-6 bg-foreground" />
        <span className="h-0.5 w-6 bg-foreground" />
        <span className="h-0.5 w-6 bg-foreground" />
      </button>

      {open && (
        <div className="fixed inset-0 z-50">
          <button
            type="button"
            aria-label="Cerrar menú"
            onClick={() => setOpen(false)}
            className="absolute inset-0 bg-foreground/60"
          />

          <div className="absolute inset-y-0 right-0 flex w-[80%] max-w-sm flex-col gap-8 border-l-[3px] border-foreground bg-background p-6">
            <button
              type="button"
              onClick={() => setOpen(false)}
              aria-label="Cerrar menú"
              className="ml-auto flex h-11 w-11 items-center justify-center border-[3px] border-foreground text-2xl font-black"
            >
              &times;
            </button>

            <nav className="flex flex-col gap-6">
              {NAV_LINKS.map((link) => (
                <a
                  key={link.href}
                  href={link.href}
                  onClick={() => setOpen(false)}
                  className="font-mono text-lg uppercase tracking-wide"
                >
                  {link.label}
                </a>
              ))}
            </nav>

            <div className="mt-auto flex flex-col gap-4">
              <a
                href={`${FRONTEND_URL}/login`}
                className="border-[3px] border-foreground bg-background px-4 py-3 text-center font-black uppercase"
              >
                Iniciar sesión
              </a>
              <a
                href={`${FRONTEND_URL}/register`}
                className="border-[3px] border-foreground bg-accent px-4 py-3 text-center font-black uppercase text-background shadow-brutal"
              >
                Registrarme
              </a>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
