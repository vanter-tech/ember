import { AddStaffCard, StaffCard } from './StaffCard'
import type { StaffMember } from '../types'

interface StaffGridProps {
  members: StaffMember[]
  onAddRole?: () => void
  onViewProfile?: (member: StaffMember) => void
  onOpenActions?: (member: StaffMember) => void
}

export const StaffGrid = ({ members, onAddRole, onViewProfile, onOpenActions }: StaffGridProps) => {
  if (members.length === 0) {
    return (
      <div className="flex items-center justify-center rounded-xl border border-dashed border-border py-16 text-sm text-muted-foreground">
        Ningún empleado coincide con la búsqueda o el filtro seleccionado.
      </div>
    )
  }

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {members.map((member) => (
        <StaffCard
          key={member.id}
          member={member}
          onViewProfile={onViewProfile}
          onOpenActions={onOpenActions}
        />
      ))}
      <AddStaffCard onClick={onAddRole} />
    </div>
  )
}
