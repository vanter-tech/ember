import { useEffect, useState } from 'react';
import { FRONTEND_URL } from '../lib/constants';

export default function StickyMobileCTA() {
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
      aria-label="Acciones rápidas"
      className="fixed inset-x-0 bottom-0 z-50 flex gap-3 border-t-[3px] border-foreground bg-background p-3 shadow-brutal md:hidden"
    >
      <a
        href={`${FRONTEND_URL}/login`}
        className="flex-1 border-[3px] border-foreground bg-background px-4 py-3 text-center text-sm font-black uppercase tracking-wide"
      >
        Iniciar sesión
      </a>
      <a
        href={`${FRONTEND_URL}/register`}
        className="flex-1 border-[3px] border-foreground bg-accent px-4 py-3 text-center text-sm font-black uppercase tracking-wide text-background shadow-brutal-sm"
      >
        Registrarme
      </a>
    </div>
  );
}
