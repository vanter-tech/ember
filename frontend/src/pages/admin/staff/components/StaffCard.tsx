import { MoreHorizontal, Plus } from 'lucide-react'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'
import { DEPARTMENT_CONFIG, type StaffMember } from '../types'

const getInitials = (name: string) =>
  name
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

interface StaffCardProps {
  member: StaffMember
  onViewProfile?: (member: StaffMember) => void
  onOpenActions?: (member: StaffMember) => void
}

export const StaffCard = ({ member, onViewProfile, onOpenActions }: StaffCardProps) => {
  const department = DEPARTMENT_CONFIG[member.department]

  return (
    <Card className="border border-border/40 bg-background py-6 shadow-sm">
      <CardContent className="flex flex-col gap-4">
        <div className="flex items-start justify-between gap-2">
          <div className="relative">
            <Avatar className="h-12 w-12">
              <AvatarImage src={member.avatarUrl} alt={member.name} />
              <AvatarFallback>{getInitials(member.name)}</AvatarFallback>
            </Avatar>
            <span
              className={cn(
                'absolute -bottom-0.5 -right-0.5 h-3.5 w-3.5 rounded-full border-2 border-background',
                member.status === 'ACTIVE' ? 'bg-emerald-500' : 'bg-zinc-300'
              )}
              title={member.status === 'ACTIVE' ? 'Activo' : 'Desconectado'}
            />
          </div>
          <Badge className={cn('border-transparent', department.badgeClassName)}>
            {department.label}
          </Badge>
        </div>

        <div className="flex flex-col gap-0.5">
          <p className="text-base font-semibold text-foreground">{member.name}</p>
          <p className="text-sm text-muted-foreground">{member.roleLabel}</p>
        </div>

        <div className="flex flex-wrap gap-2">
          {member.metadata.map((item) => (
            <span
              key={item.label}
              className="rounded-full bg-muted px-3 py-1 text-xs text-muted-foreground"
            >
              {item.label}: <span className="font-medium text-foreground">{item.value}</span>
            </span>
          ))}
        </div>

        <div className="flex items-center gap-2 pt-1">
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="flex-1"
            onClick={() => onViewProfile?.(member)}
          >
            Perfil
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            onClick={() => onOpenActions?.(member)}
            aria-label="Más opciones"
          >
            <MoreHorizontal className="h-4 w-4" />
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

interface AddStaffCardProps {
  onClick?: () => void
}

export const AddStaffCard = ({ onClick }: AddStaffCardProps) => {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex min-h-[220px] flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-border bg-transparent text-muted-foreground transition-colors hover:border-primary/40 hover:text-primary"
    >
      <Plus className="h-6 w-6" />
      <span className="text-sm font-medium">Agregar nuevo rol</span>
    </button>
  )
}
