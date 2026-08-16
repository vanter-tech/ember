import { Plus, Search } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

interface StaffHeaderProps {
  search: string
  onSearchChange: (value: string) => void
  onAddEmployee?: () => void
}

export const StaffHeader = ({ search, onSearchChange, onAddEmployee }: StaffHeaderProps) => {
  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex flex-col gap-1">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">
          Gestión de Personal
        </h1>
        <p className="text-sm text-muted-foreground">
          Control administrativo y roles del equipo.
        </p>
      </div>
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
        <div className="relative">
          <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(event) => onSearchChange(event.target.value)}
            placeholder="Buscar empleado..."
            className="pl-10 sm:w-64"
          />
        </div>
        <Button type="button" onClick={onAddEmployee} className="gap-1.5">
          <Plus className="h-4 w-4" />
          Nuevo empleado
        </Button>
      </div>
    </div>
  )
}
