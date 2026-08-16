import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { STAFF_FILTERS, type StaffFilter } from '../types'

interface StaffFiltersProps {
  active: StaffFilter
  onChange: (value: StaffFilter) => void
}

export const StaffFilters = ({ active, onChange }: StaffFiltersProps) => {
  return (
    <div className="flex w-fit flex-wrap items-center gap-1 rounded-full bg-muted/60 p-1">
      {STAFF_FILTERS.map((filter) => (
        <Button
          key={filter.value}
          type="button"
          size="sm"
          variant={active === filter.value ? 'default' : 'ghost'}
          className={cn('rounded-full', active !== filter.value && 'text-muted-foreground')}
          onClick={() => onChange(filter.value)}
        >
          {filter.label}
        </Button>
      ))}
    </div>
  )
}
