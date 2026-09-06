import type { HubStatus } from '@/lib/platformApi'

const CONFIG: Record<HubStatus, { dot: string; label: string }> = {
  ONLINE: { dot: 'bg-green-500', label: 'ONLINE' },
  STALE: { dot: 'bg-amber-500', label: 'STALE' },
  OFFLINE: { dot: 'bg-zinc-400', label: 'OFFLINE' },
  NEVER: { dot: 'bg-transparent', label: '—' },
}

const UNKNOWN = { dot: 'bg-transparent', label: '—' }

// `status` can be undefined/unexpected when the backend is a version behind the console
// (the DTO field didn't exist yet) — degrade to "—" instead of crashing the whole page.
export function HubBadge({ status }: { status: HubStatus | null | undefined }) {
  const { dot, label } = (status && CONFIG[status]) || UNKNOWN
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-zinc-600">
      <span className={`h-2 w-2 rounded-full ${dot}`} />
      {label}
    </span>
  )
}
