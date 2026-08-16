export type StaffDepartment = 'KITCHEN' | 'DINING_ROOM' | 'ADMINISTRATION' | 'CLEANING'

export type StaffFilter = 'ALL' | StaffDepartment

export type StaffStatus = 'ACTIVE' | 'OFFLINE'

export interface StaffMetadataItem {
  label: string
  value: string
}

export interface StaffMember {
  id: string
  name: string
  avatarUrl?: string
  department: StaffDepartment
  roleLabel: string
  status: StaffStatus
  metadata: [StaffMetadataItem, StaffMetadataItem]
  pendingHours: number
}

export const DEPARTMENT_CONFIG: Record<
  StaffDepartment,
  { label: string; badgeClassName: string }
> = {
  KITCHEN: { label: 'Cocina', badgeClassName: 'bg-orange-100 text-orange-700' },
  DINING_ROOM: { label: 'Comedor', badgeClassName: 'bg-blue-100 text-blue-700' },
  ADMINISTRATION: { label: 'Administración', badgeClassName: 'bg-violet-100 text-violet-700' },
  CLEANING: { label: 'Limpieza', badgeClassName: 'bg-emerald-100 text-emerald-700' },
}

export const STAFF_FILTERS: { value: StaffFilter; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'KITCHEN', label: 'Cocina' },
  { value: 'DINING_ROOM', label: 'Comedor' },
  { value: 'ADMINISTRATION', label: 'Administración' },
  { value: 'CLEANING', label: 'Limpieza' },
]
