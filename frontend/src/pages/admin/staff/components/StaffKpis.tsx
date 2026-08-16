import { Clock, UserCheck, Users } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { StaffMember } from '../types'

interface StaffKpisProps {
  members: StaffMember[]
}

export const StaffKpis = ({ members }: StaffKpisProps) => {
  const totalStaff = members.length
  const activeNow = members.filter((member) => member.status === 'ACTIVE').length
  const pendingHours = members.reduce((sum, member) => sum + member.pendingHours, 0)

  const cards = [
    { label: 'Personal total', value: totalStaff, icon: Users },
    { label: 'Activos ahora', value: activeNow, icon: UserCheck },
    { label: 'Horas pendientes', value: `${pendingHours}h`, icon: Clock },
  ]

  return (
    <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
      {cards.map(({ label, value, icon: Icon }) => (
        <Card key={label} className="border border-border/40 bg-background py-6 shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {label}
            </CardTitle>
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-primary/10">
              <Icon className="h-4 w-4 text-primary" strokeWidth={2} />
            </div>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-bold tracking-tight tabular-nums text-primary">
              {value}
            </p>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
