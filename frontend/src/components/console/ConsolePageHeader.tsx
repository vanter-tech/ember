import type { ReactNode } from 'react'

export function ConsolePageHeader({
  title,
  action,
}: {
  title: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h1 className="text-2xl font-semibold text-zinc-900">{title}</h1>
      {action}
    </div>
  )
}
