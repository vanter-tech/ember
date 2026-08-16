import { useMemo, useState } from 'react'
import { StaffFilters } from './components/StaffFilters'
import { StaffGrid } from './components/StaffGrid'
import { StaffHeader } from './components/StaffHeader'
import { StaffKpis } from './components/StaffKpis'
import { MOCK_STAFF } from './mock-data'
import type { StaffFilter } from './types'

export const Staff = () => {
  const [search, setSearch] = useState('')
  const [department, setDepartment] = useState<StaffFilter>('ALL')

  const filteredStaff = useMemo(() => {
    const query = search.trim().toLowerCase()
    return MOCK_STAFF.filter((member) => {
      const matchesDepartment = department === 'ALL' || member.department === department
      const matchesSearch = query === '' || member.name.toLowerCase().includes(query)
      return matchesDepartment && matchesSearch
    })
  }, [search, department])

  return (
    <div className="flex flex-col gap-8">
      <StaffHeader search={search} onSearchChange={setSearch} />
      <StaffFilters active={department} onChange={setDepartment} />
      <StaffGrid members={filteredStaff} />
      <StaffKpis members={MOCK_STAFF} />
    </div>
  )
}
