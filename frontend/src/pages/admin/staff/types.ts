// `StaffMemberResponse.role` (generated from the backend's full `Role` enum) is
// `"CUSTOMER" | "WAITER" | "KITCHEN" | "ADMIN" | undefined` — broader than what
// GET /admin/staff can actually return (it always excludes CUSTOMER). `StaffRole`
// is the narrower UI-domain type this page actually filters/labels on.
export type StaffRole = 'WAITER' | 'KITCHEN' | 'ADMIN'

export type StaffFilter = 'ALL' | StaffRole

export const STAFF_FILTERS: { value: StaffFilter; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'KITCHEN', label: 'Cocina' },
  { value: 'WAITER', label: 'Comedor' },
  { value: 'ADMIN', label: 'Administración' },
]

export const ROLE_LABELS: Record<string, string> = {
  KITCHEN: 'Cocina',
  WAITER: 'Comedor',
  ADMIN: 'Administración',
}

export const ROLE_BADGE_CLASSNAMES: Record<string, string> = {
  KITCHEN: 'bg-orange-100 text-orange-700',
  WAITER: 'bg-blue-100 text-blue-700',
  ADMIN: 'bg-violet-100 text-violet-700',
}
