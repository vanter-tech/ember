import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { DollarSign, CreditCard, Scale, ArrowDownCircle, ArrowUpCircle } from 'lucide-react'
import { formatCurrency } from '@/lib/format'

const today = () => new Date().toISOString().slice(0, 10)

export const DailyZReportPanel = () => {
  const [date, setDate] = useState(today())

  const { data, isLoading, isError } = useQuery({
    queryKey: ['cashShiftDailyReport', date],
    queryFn: () => cashShiftService.dailyReport(date),
  })

  const cards = data
    ? [
        { label: 'Ventas en efectivo', value: formatCurrency(data.totalCashSales), icon: DollarSign },
        { label: 'Ventas digitales', value: formatCurrency(data.totalDigitalSales), icon: CreditCard },
        { label: 'Diferencia total', value: formatCurrency(data.totalVariance), icon: Scale },
        { label: 'Entradas manuales', value: formatCurrency(data.totalCashIn), icon: ArrowDownCircle },
        { label: 'Salidas manuales', value: formatCurrency(data.totalCashOut), icon: ArrowUpCircle },
      ]
    : []

  return (
    <div className="flex flex-col gap-6">
      <Input
        type="date"
        value={date}
        onChange={(e) => setDate(e.target.value)}
        className="w-fit rounded-xl"
      />

      {isLoading && <div className="text-sm text-muted-foreground">Cargando corte diario...</div>}
      {isError && <div className="text-sm text-destructive">Error al cargar el corte diario.</div>}

      {data && (
        <div className="grid grid-cols-1 gap-6 md:grid-cols-3 lg:grid-cols-5">
          {cards.map(({ label, value, icon: Icon }) => (
            <Card key={label} className="border border-border/40 bg-background py-6 shadow-sm">
              <CardHeader className="flex flex-row items-center justify-start gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-full bg-primary/10">
                  <Icon className="h-4 w-4 text-primary" strokeWidth={2} />
                </div>
                <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {label}
                </CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-2xl font-bold tracking-tight tabular-nums text-primary">{value}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
