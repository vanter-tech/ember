import type { ButtonHTMLAttributes } from 'react';

type Variant = 'primary' | 'outline';

const VARIANT_CLASS: Record<Variant, string> = {
  primary: 'bg-primary text-primary-foreground hover:opacity-90',
  outline: 'border border-border hover:bg-muted'
};

export default function Button({
  variant = 'outline',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-full px-4 py-1.5 text-sm font-medium cursor-pointer disabled:cursor-not-allowed disabled:opacity-50 transition-colors ${VARIANT_CLASS[variant]} ${className}`}
      {...props}
    />
  );
}
