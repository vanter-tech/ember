import type { ReactNode } from 'react';

const VARIANTS = {
  success: 'bg-emerald-100 text-emerald-800',
  warning: 'bg-amber-100 text-amber-800',
  danger: 'bg-red-100 text-red-800',
  neutral: 'bg-muted text-muted-foreground'
} as const;

export default function Badge({
  variant = 'neutral',
  children
}: {
  variant?: keyof typeof VARIANTS;
  children: ReactNode;
}) {
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium whitespace-nowrap ${VARIANTS[variant]}`}>
      {children}
    </span>
  );
}
