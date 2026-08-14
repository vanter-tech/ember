import { useQuery } from '@tanstack/react-query'
import { analyticsService } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

export const TableAnalytics = () => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['analyticsTables'],
    queryFn: () => analyticsService.getTables(),
  })

  const tables = data?.tables ?? []
  const maxRevenue = Math.max(...tables.map((table) => table.revenue ?? 0), 0)

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-medium text-zinc-500">
          Análisis de mesas
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        {isLoading && <div className="text-zinc-500">Cargando mesas...</div>}
        {isError && (
          <div className="text-red-500">Error al cargar las mesas.</div>
        )}
        {data && (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <p className="text-xs text-zinc-500">Mesas activas</p>
              <p className="text-xl font-bold text-zinc-800">
                {data.activeTableCount ?? 0}
              </p>
            </div>
            <div>
              <p className="text-xs text-zinc-500">Rotación promedio</p>
              <p className="text-xl font-bold text-zinc-800">
                {(data.averageTurnoverRate ?? 0).toFixed(2)}
              </p>
            </div>
            <div>
              <p className="text-xs text-zinc-500">Duración promedio (min)</p>
              <p className="text-xl font-bold text-zinc-800">
                {(data.averageSessionDurationMinutes ?? 0).toFixed(0)}
              </p>
            </div>
          </div>
        )}
        {data && tables.length === 0 && (
          <div className="text-zinc-500">Sin mesas con actividad en este periodo.</div>
        )}
        {tables.length > 0 && (
          <div className="flex flex-col gap-3">
            {tables.map((table, index) => (
              <div key={table.tableId ?? index} className="flex flex-col gap-1">
                <div className="flex items-baseline justify-between gap-2 text-sm">
                  <span className="font-medium text-zinc-800">
                    Mesa {table.tableNumber ?? '—'}
                  </span>
                  <span className="shrink-0 text-zinc-500">
                    ${(table.revenue ?? 0).toFixed(2)} · {table.turnoverCount ?? 0} giros ·{' '}
                    {(table.averageSessionDurationMinutes ?? 0).toFixed(0)} min
                  </span>
                </div>
                <div className="relative h-2 w-full overflow-hidden rounded bg-zinc-100">
                  <div
                    className="h-full rounded bg-primary"
                    style={{
                      width: `${
                        maxRevenue > 0 ? ((table.revenue ?? 0) / maxRevenue) * 100 : 0
                      }%`,
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
