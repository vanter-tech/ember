import type { StaffRole } from '@/lib/api'

export type StaffFilter = 'ALL' | StaffRole

export const STAFF_FILTERS: { value: StaffFilter; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'KITCHEN', label: 'Cocina' },
  { value: 'WAITER', label: 'Comedor' },
  { value: 'ADMIN', label: 'Administración' },
]

export const ROLE_LABELS: Record<StaffRole, string> = {
  KITCHEN: 'Cocina',
  WAITER: 'Comedor',
  ADMIN: 'Administración',
}

export const ROLE_BADGE_CLASSNAMES: Record<StaffRole, string> = {
  KITCHEN: 'bg-orange-100 text-orange-700',
  WAITER: 'bg-blue-100 text-blue-700',
  ADMIN: 'bg-violet-100 text-violet-700',
}
