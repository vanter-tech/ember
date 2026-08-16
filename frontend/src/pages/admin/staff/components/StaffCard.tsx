import { MoreHorizontal, Plus } from 'lucide-react'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'
import type { StaffMemberResponse } from '@/lib/api'
import { ROLE_BADGE_CLASSNAMES, ROLE_LABELS } from '../types'

const getInitials = (name: string) =>
  name
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

interface StaffCardProps {
  member: StaffMemberResponse
  onViewProfile?: (member: StaffMemberResponse) => void
  onOpenActions?: (member: StaffMemberResponse) => void
}

export const StaffCard = ({ member, onViewProfile, onOpenActions }: StaffCardProps) => {
  const metadata = [
    member.shift ? { label: 'Turno', value: member.shift } : null,
    member.contractType ? { label: 'Contrato', value: member.contractType } : null,
    member.location ? { label: 'Ubicación', value: member.location } : null,
    member.efficiencyPercentage != null
      ? { label: 'Eficiencia', value: `${member.efficiencyPercentage}%` }
      : null,
  ].filter((item): item is { label: string; value: string } => item !== null)

  return (
    <Card className="border border-border/40 bg-background py-6 shadow-sm">
      <CardContent className="flex flex-col gap-4">
        <div className="flex items-start justify-between gap-2">
          <div className="relative">
            <Avatar className="h-12 w-12">
              <AvatarFallback>{getInitials(member.name)}</AvatarFallback>
            </Avatar>
            <span
              className={cn(
                'absolute -bottom-0.5 -right-0.5 h-3.5 w-3.5 rounded-full border-2 border-background',
                member.active ? 'bg-emerald-500' : 'bg-zinc-300'
              )}
              title={member.active ? 'Activo' : 'Inactivo'}
            />
          </div>
          <Badge className={cn('border-transparent', ROLE_BADGE_CLASSNAMES[member.role])}>
            {ROLE_LABELS[member.role]}
          </Badge>
        </div>

        <div className="flex flex-col gap-0.5">
          <p className="text-base font-semibold text-foreground">{member.name}</p>
          <p className="text-sm text-muted-foreground">{member.jobTitle || member.email}</p>
        </div>

        {metadata.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {metadata.map((item) => (
              <span
                key={item.label}
                className="rounded-full bg-muted px-3 py-1 text-xs text-muted-foreground"
              >
                {item.label}: <span className="font-medium text-foreground">{item.value}</span>
              </span>
            ))}
          </div>
        )}

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
