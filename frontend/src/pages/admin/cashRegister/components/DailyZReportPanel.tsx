import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { DollarSign, CreditCard, Scale, ArrowDownCircle, ArrowUpCircle } from 'lucide-react'
import { formatCurrency } from '@/lib/format'
import { useTranslation } from '@/lib/i18n'

const today = () => new Date().toISOString().slice(0, 10)

export const DailyZReportPanel = () => {
  const [date, setDate] = useState(today())
  const { t } = useTranslation('admin')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['cashShiftDailyReport', date],
    queryFn: () => cashShiftService.dailyReport(date),
  })

  const cards = data
    ? [
        { label: t('cashSalesLabel'), value: formatCurrency(data.totalCashSales ?? 0), icon: DollarSign },
        { label: t('digitalSalesLabel'), value: formatCurrency(data.totalDigitalSales ?? 0), icon: CreditCard },
        { label: t('totalVarianceLabel'), value: formatCurrency(data.totalVariance ?? 0), icon: Scale },
        { label: t('manualCashInLabel'), value: formatCurrency(data.totalCashIn ?? 0), icon: ArrowDownCircle },
        { label: t('manualCashOutLabel'), value: formatCurrency(data.totalCashOut ?? 0), icon: ArrowUpCircle },
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

      {isLoading && <div className="text-sm text-muted-foreground">{t('loadingDailyReport')}</div>}
      {isError && <div className="text-sm text-destructive">{t('loadingDailyReportError')}</div>}

      {data && (
        <>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {cards.map(({ label, value, icon: Icon }) => (
              <Card key={label} className="border border-border/40 bg-background py-8 shadow-sm">
                <CardHeader className="flex flex-row items-center justify-start gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10">
                    <Icon className="h-5 w-5 text-primary" strokeWidth={2} />
                  </div>
                  <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    {label}
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-3xl font-bold tracking-tight tabular-nums text-primary">{value}</p>
                </CardContent>
              </Card>
            ))}
          </div>

          <Card className="border border-border/40 bg-background py-6 shadow-sm">
            <CardHeader>
              <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                {t('dailyReportShiftsTitle')}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('shiftColumnLabel')}</TableHead>
                    <TableHead>{t('statusColumnLabel')}</TableHead>
                    <TableHead>{t('openedByColumnLabel')}</TableHead>
                    <TableHead>{t('closedByColumnLabel')}</TableHead>
                    <TableHead>{t('expectedColumnLabel')}</TableHead>
                    <TableHead>{t('countedColumnLabel')}</TableHead>
                    <TableHead>{t('varianceColumnLabel')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(data.shifts ?? []).length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={7} className="text-center text-sm text-muted-foreground">
                        {t('noShiftsRegistered')}
                      </TableCell>
                    </TableRow>
                  ) : (
                    (data.shifts ?? []).map((shift) => (
                      <TableRow key={shift.id}>
                        <TableCell>#{shift.shiftNumber}</TableCell>
                        <TableCell>
                          <Badge variant={shift.status === 'OPEN' ? 'default' : 'secondary'}>
                            {shift.status === 'OPEN' ? t('openStatus') : t('closedStatus')}
                          </Badge>
                        </TableCell>
                        <TableCell>{shift.openedByName}</TableCell>
                        <TableCell>{shift.closedByName ?? '—'}</TableCell>
                        <TableCell>
                          {shift.expectedCash != null ? formatCurrency(shift.expectedCash) : '—'}
                        </TableCell>
                        <TableCell>
                          {shift.countedCash != null ? formatCurrency(shift.countedCash) : '—'}
                        </TableCell>
                        <TableCell>{shift.variance != null ? formatCurrency(shift.variance) : '—'}</TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}
