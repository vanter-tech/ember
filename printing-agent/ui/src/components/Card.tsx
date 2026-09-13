import type { ComponentType, ReactNode } from 'react';

export const cardShellClass = 'rounded-3xl border border-border shadow-md bg-background';

export function IconBadge({
  icon: Icon,
  size = 'md'
}: {
  icon: ComponentType<{ className?: string }>;
  size?: 'md' | 'lg';
}) {
  const wrapper = size === 'lg' ? 'h-12 w-12' : 'h-9 w-9';
  const iconSize = size === 'lg' ? 'h-6 w-6' : 'h-5 w-5';
  return (
    <span className={`inline-flex items-center justify-center shrink-0 rounded-full bg-primary/10 text-primary ${wrapper}`}>
      <Icon className={iconSize} />
    </span>
  );
}

export default function Card({
  icon: Icon,
  title,
  children,
  className = ''
}: {
  icon: ComponentType<{ className?: string }>;
  title: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`${cardShellClass} p-4 flex flex-col min-w-0 ${className}`}>
      <div className="flex items-center gap-3 mb-3 shrink-0">
        <IconBadge icon={Icon} />
        <h2 className="font-semibold text-lg">{title}</h2>
      </div>
      {children}
    </section>
  );
}
