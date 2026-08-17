import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { staffService } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { CreateStaffModal } from './components/CreateStaffModal'
import { StaffFilters } from './components/StaffFilters'
import { StaffGrid } from './components/StaffGrid'
import { StaffHeader } from './components/StaffHeader'
import { StaffKpis } from './components/StaffKpis'
import type { StaffFilter } from './types'

export const Staff = () => {
  const [roleFilter, setRoleFilter] = useState<StaffFilter>('ALL')
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
          Cargando personal...
        </div>
      )}
      {isError && (
        <div className="flex items-center justify-center py-16 text-sm text-destructive">
          Error al cargar el personal.
        </div>
      )}
      {!isLoading && !isError && (
        <>
          <StaffGrid
            members={filteredStaff}
            onAddRole={() => openModal('CREATE_STAFF')}
          />
          <StaffKpis members={staff} />
        </>
      )}
      <CreateStaffModal />
    </div>
  )
}
