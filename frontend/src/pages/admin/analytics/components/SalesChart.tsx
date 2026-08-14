import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { analyticsService, type SalesGranularity } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

const GRANULARITY_OPTIONS: { value: SalesGranularity; label: string }[] = [
  { value: 'day', label: 'Día' },
  { value: 'week', label: 'Semana' },
  { value: 'month', label: 'Mes' },
  { value: 'year', label: 'Año' },
]

const BUCKET_LABEL_FORMAT: Record<SalesGranularity, Intl.DateTimeFormatOptions> = {
  day: { day: '2-digit', month: '2-digit' },
  week: { day: '2-digit', month: '2-digit' },
  month: { month: 'short', year: '2-digit' },
  year: { year: 'numeric' },
}

export const SalesChart = () => {
  const [granularity, setGranularity] = useState<SalesGranularity>('day')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['analyticsSales', granularity],
    queryFn: () => analyticsService.getSales(granularity),
  })

  const maxRevenue = data
    ? Math.max(...data.buckets.map((bucket) => bucket.revenue), 0)
    : 0

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between gap-4">
        <CardTitle className="text-sm font-medium text-zinc-500">
          Ventas en el tiempo
        </CardTitle>
        <div className="flex gap-1">
          {GRANULARITY_OPTIONS.map((option) => (
            <Button
              key={option.value}
              type="button"
              size="sm"
              variant={granularity === option.value ? 'default' : 'outline'}
              onClick={() => setGranularity(option.value)}
            >
              {option.label}
            </Button>
          ))}
        </div>
      </CardHeader>
      <CardContent>
        {isLoading && <div className="text-zinc-500">Cargando ventas...</div>}
        {isError && (
          <div className="text-red-500">Error al cargar las ventas.</div>
        )}
        {data && data.buckets.length === 0 && (
          <div className="text-zinc-500">Sin ventas registradas.</div>
        )}
        {data && data.buckets.length > 0 && (
          <div className="flex h-48 items-end gap-1">
            {data.buckets.map((bucket) => {
              const heightPct =
                maxRevenue > 0 ? (bucket.revenue / maxRevenue) * 100 : 0
              const label = new Date(bucket.bucketStart).toLocaleDateString(
                'es',
                BUCKET_LABEL_FORMAT[granularity]
              )
              return (
                <div
                  key={bucket.bucketStart}
                  className="flex flex-1 flex-col items-center justify-end gap-1"
                  title={`${label}: $${bucket.revenue.toFixed(2)}`}
                >
                  <div
                    className={cn(
                      'w-full min-h-[2px] rounded-t bg-primary',
                      bucket.revenue === 0 && 'bg-zinc-200'
                    )}
                    style={{ height: `${heightPct}%` }}
                  />
                  <span className="text-[10px] text-zinc-400 whitespace-nowrap">
                    {label}
                  </span>
                </div>
              )
            })}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
