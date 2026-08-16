import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
} from 'recharts'
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

  const buckets = data?.buckets ?? []

  const chartData = buckets.map((bucket) => ({
    label: new Date(
      bucket.bucketStart ?? bucket.bucketEnd ?? ''
    ).toLocaleDateString('es', BUCKET_LABEL_FORMAT[granularity]),
    revenue: bucket.revenue ?? 0,
  }))

  return (
    <Card className="border border-border/40 bg-background shadow-sm">
      <CardHeader className="flex flex-row items-center justify-between gap-4">
        <CardTitle className="text-base font-semibold tracking-tight text-foreground">
          Ventas en el tiempo
        </CardTitle>
        <div className="flex items-center gap-1 rounded-full bg-muted/60 p-1">
          {GRANULARITY_OPTIONS.map((option) => (
            <Button
              key={option.value}
              type="button"
              size="sm"
              variant={granularity === option.value ? 'default' : 'ghost'}
              className={cn(
                'rounded-full',
                granularity !== option.value && 'text-muted-foreground'
              )}
              onClick={() => setGranularity(option.value)}
            >
              {option.label}
            </Button>
          ))}
        </div>
      </CardHeader>
      <CardContent>
        {isLoading && (
          <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
            Cargando ventas...
          </div>
        )}
        {isError && (
          <div className="flex items-center justify-center py-16 text-sm text-destructive">
            Error al cargar las ventas.
          </div>
        )}
        {data && chartData.length === 0 && (
          <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
            Sin ventas registradas.
          </div>
        )}
        {chartData.length > 0 && (
          <div className="h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart
                data={chartData}
                margin={{ top: 8, right: 8, left: 0, bottom: 0 }}
              >
                <defs>
                  <linearGradient id="salesRevenueFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--primary)" stopOpacity={0.25} />
                    <stop offset="100%" stopColor="var(--primary)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid
                  vertical={false}
                  stroke="var(--border)"
                  strokeOpacity={0.5}
                />
                <XAxis
                  dataKey="label"
                  axisLine={false}
                  tickLine={false}
                  tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }}
                />
                <Tooltip
                  cursor={{ stroke: 'var(--border)', strokeWidth: 1 }}
                  contentStyle={{
                    borderRadius: 8,
                    border: '1px solid var(--border)',
                    fontSize: 12,
                  }}
                  formatter={(value) => [`$${Number(value ?? 0).toFixed(2)}`, 'Ingresos']}
                />
                <Area
                  type="monotone"
                  dataKey="revenue"
                  stroke="var(--primary)"
                  strokeWidth={2}
                  fill="url(#salesRevenueFill)"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
