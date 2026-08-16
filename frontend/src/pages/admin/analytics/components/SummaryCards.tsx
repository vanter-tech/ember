import { useQuery } from '@tanstack/react-query'
import { analyticsService } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { DollarSign, Users, Receipt } from 'lucide-react'

export const SummaryCards = () => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['analyticsSummary'],
    queryFn: () => analyticsService.getSummary(),
  })

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-10 text-sm text-muted-foreground">
        Cargando métricas...
      </div>
    )
  }

  if (isError || !data) {
    return (
      <div className="flex items-center justify-center py-10 text-sm text-destructive">
        Error al cargar las métricas.
      </div>
    )
  }

  const cards = [
    {
      label: 'Ingresos totales',
      value: `$${(data.totalRevenue ?? 0).toFixed(2)}`,
      icon: DollarSign,
    },
    {
      label: 'Sesiones activas',
      value: data.activeSessions ?? 0,
      icon: Users,
    },
    {
      label: 'Ticket promedio',
      value: `$${(data.averageOrderValue ?? 0).toFixed(2)}`,
      icon: Receipt,
    },
  ]

  return (
    <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
      {cards.map(({ label, value, icon: Icon }) => (
        <Card
          key={label}
          className="border border-border/40 bg-background shadow-sm"
        >
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              {label}
            </CardTitle>
            <Icon className="h-4 w-4 text-muted-foreground" strokeWidth={1.5} />
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-semibold tracking-tight tabular-nums text-foreground">
              {value}
            </p>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
