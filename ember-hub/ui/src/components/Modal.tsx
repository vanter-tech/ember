import type { ReactNode } from 'react';

/** Minimal in-window modal (no browser `confirm()`/`alert()`); the parent controls when it renders. */
export default function Modal({
  title,
  children,
  footer
}: {
  title: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="w-full max-w-md rounded-3xl border border-border bg-background shadow-lg p-5 flex flex-col gap-4"
      >
        <h2 className="font-semibold text-lg">{title}</h2>
        <div className="text-sm flex flex-col gap-3">{children}</div>
        {footer && <div className="flex flex-wrap justify-end gap-2">{footer}</div>}
      </div>
    </div>
  );
}
