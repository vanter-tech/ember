import type { ReactNode } from 'react';

const VARIANTS = {
  success: 'bg-emerald-100 text-emerald-800',
  warning: 'bg-amber-100 text-amber-800',
  danger: 'bg-red-100 text-red-800',
  neutral: 'bg-muted text-muted-foreground'
} as const;

// max-w-full + break-words: a long status such as "Conexión perdida, reintentando en 10s" wraps
// inside its cell instead of poking out of the card (it used to be whitespace-nowrap).
export default function Badge({
  variant = 'neutral',
  children
}: {
  variant?: keyof typeof VARIANTS;
  children: ReactNode;
}) {
  return (
    <span className={`inline-flex items-center max-w-full px-2.5 py-0.5 rounded-2xl text-xs font-medium leading-snug break-words ${VARIANTS[variant]}`}>
      {children}
    </span>
  );
}
