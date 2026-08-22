import { AddStaffCard, StaffCard } from './StaffCard'
import type { StaffMemberResponse } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'

interface StaffGridProps {
  members: StaffMemberResponse[]
  onAddRole?: () => void
  onViewProfile?: (member: StaffMemberResponse) => void
  onOpenActions?: (member: StaffMemberResponse) => void
}

export const StaffGrid = ({ members, onAddRole, onViewProfile, onOpenActions }: StaffGridProps) => {
  const { t } = useTranslation('admin')
  if (members.length === 0) {
    return (
      <div className="flex items-center justify-center rounded-xl border border-dashed border-border py-16 text-sm text-muted-foreground">
        {t('noStaffMatchesFilter')}
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
