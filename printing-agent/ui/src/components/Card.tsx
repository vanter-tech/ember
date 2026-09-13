import type { ComponentType, ReactNode } from 'react';

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
    <section className={`rounded-lg border border-border p-4 flex flex-col min-w-0 ${className}`}>
      <div className="flex items-center gap-2 mb-3 shrink-0">
        <Icon className="h-5 w-5 text-primary" />
        <h2 className="font-semibold text-lg">{title}</h2>
      </div>
      {children}
    </section>
  );
}
