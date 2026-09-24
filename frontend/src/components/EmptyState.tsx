import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

interface EmptyStateProps {
  icon: LucideIcon
  title: string
  description?: string
  action?: ReactNode
}

/** Centered placeholder for a list/grid with zero items — a brand-new tenant otherwise sees a
 *  blank page on its very first admin/kitchen/waiter screens, which reads as broken rather than
 *  "nothing here yet". Callers own their own copy/icon/action so this stays namespace-agnostic. */
export const EmptyState = ({ icon: Icon, title, description, action }: EmptyStateProps) => (
  <div className="flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-zinc-200 px-6 py-16 text-center">
    <Icon strokeWidth={1.5} size={40} className="text-zinc-300" />
    <div className="flex flex-col gap-1">
      <p className="text-base font-semibold text-zinc-700">{title}</p>
      {description && <p className="max-w-sm text-sm text-zinc-400">{description}</p>}
    </div>
    {action}
  </div>
)
