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
    <Card className="border border-border/40 bg-background shadow-sm">
      <CardHeader>
        <CardTitle className="text-base font-semibold tracking-tight text-foreground">
          Análisis de mesas
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        {isLoading && (
          <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
            Cargando mesas...
          </div>
        )}
        {isError && (
          <div className="flex items-center justify-center py-16 text-sm text-destructive">
            Error al cargar las mesas.
          </div>
        )}
        {data && (
          <div className="grid grid-cols-1 divide-y divide-border/40 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
            <div className="flex flex-col gap-1 py-3 first:pt-0 last:pb-0 sm:py-0 sm:px-6 sm:first:pl-0 sm:last:pr-0">
              <p className="text-xs text-muted-foreground">Mesas activas</p>
              <p className="text-xl font-semibold tabular-nums text-foreground">
                {data.activeTableCount ?? 0}
              </p>
            </div>
            <div className="flex flex-col gap-1 py-3 first:pt-0 last:pb-0 sm:py-0 sm:px-6 sm:first:pl-0 sm:last:pr-0">
              <p className="text-xs text-muted-foreground">Rotación promedio</p>
              <p className="text-xl font-semibold tabular-nums text-foreground">
                {(data.averageTurnoverRate ?? 0).toFixed(2)}
              </p>
            </div>
            <div className="flex flex-col gap-1 py-3 first:pt-0 last:pb-0 sm:py-0 sm:px-6 sm:first:pl-0 sm:last:pr-0">
              <p className="text-xs text-muted-foreground">Duración promedio (min)</p>
              <p className="text-xl font-semibold tabular-nums text-foreground">
                {(data.averageSessionDurationMinutes ?? 0).toFixed(0)}
              </p>
            </div>
          </div>
        )}
        {data && tables.length === 0 && (
          <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
            Sin mesas con actividad en este periodo.
          </div>
        )}
        {tables.length > 0 && (
          <div className="flex flex-col divide-y divide-border/40">
            {tables.map((table, index) => (
              <div
                key={table.tableId ?? index}
                className="flex flex-col gap-1.5 py-3 first:pt-0 last:pb-0"
              >
                <div className="flex items-baseline justify-between gap-2 text-sm">
                  <span className="font-medium text-foreground">
                    Mesa {table.tableNumber ?? '—'}
                  </span>
                  <span className="shrink-0 text-muted-foreground">
                    ${(table.revenue ?? 0).toFixed(2)} · {table.turnoverCount ?? 0} giros ·{' '}
                    {(table.averageSessionDurationMinutes ?? 0).toFixed(0)} min
                  </span>
                </div>
                <div className="relative h-1.5 w-full overflow-hidden rounded-full bg-muted">
                  <div
                    className="h-full rounded-full bg-primary"
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
