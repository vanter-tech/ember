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
    return <div className="text-zinc-500">Cargando métricas...</div>
  }

  if (isError || !data) {
    return <div className="text-red-500">Error al cargar las métricas.</div>
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
    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
      {cards.map(({ label, value, icon: Icon }) => (
        <Card key={label}>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="text-sm font-medium text-zinc-500">
              {label}
            </CardTitle>
            <Icon className="h-4 w-4 text-zinc-400" strokeWidth={1.5} />
          </CardHeader>
          <CardContent>
            <p className="text-2xl font-bold text-zinc-800">{value}</p>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
