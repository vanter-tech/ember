import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { staffService, type StaffMemberResponse } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { GlobalDeleteModal } from '@/components/GlobalDeleteModal'
import { CreateStaffModal } from './components/CreateStaffModal'
import { EditStaffModal } from './components/EditStaffModal'
import { StaffFilters } from './components/StaffFilters'
import { StaffGrid } from './components/StaffGrid'
import { StaffHeader } from './components/StaffHeader'
import { StaffKpis } from './components/StaffKpis'
import type { StaffFilter } from './types'
import { useTranslation } from '@/lib/i18n'

export const Staff = () => {
  const [roleFilter, setRoleFilter] = useState<StaffFilter>('ALL')
  const { t } = useTranslation('admin')
  const searchTerm = useUIStore((state) => state.searchTerm)
  const openModal = useUIStore((state) => state.openModal)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['staff'],
    queryFn: staffService.getAll,
  })

  const staff = useMemo(() => data ?? [], [data])

  const filteredStaff = useMemo(() => {
    const query = searchTerm.trim().toLowerCase()
    return staff.filter((member) => {
      const matchesDepartment = roleFilter === 'ALL' || member.role === roleFilter
      const matchesSearch = query === '' || (member.name ?? '').toLowerCase().includes(query)
      return matchesDepartment && matchesSearch
    })
  }, [staff, searchTerm, roleFilter])

  return (
    <div className="flex flex-col gap-8">
      <StaffHeader />
      <StaffFilters active={roleFilter} onChange={setRoleFilter} />
      {isLoading && (
        <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
          {t('loadingStaff')}
        </div>
      )}
      {isError && (
        <div className="flex items-center justify-center py-16 text-sm text-destructive">
          {t('loadingStaffError')}
        </div>
      )}
      {!isLoading && !isError && (
        <>
          <StaffGrid
            members={filteredStaff}
            onAddRole={() => openModal('CREATE_STAFF')}
            onViewProfile={(member: StaffMemberResponse) => openModal('EDIT_STAFF', member)}
            onOpenActions={(member: StaffMemberResponse) => openModal('DELETE_STAFF', member)}
          />
          <StaffKpis members={staff} />
        </>
      )}
      <CreateStaffModal />
      <EditStaffModal />
      <GlobalDeleteModal />
    </div>
  )
}
