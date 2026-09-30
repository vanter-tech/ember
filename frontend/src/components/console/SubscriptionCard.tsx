import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import {
  platformRestaurantService,
  type BillingPeriod,
  type PlatformRestaurantDetail,
} from '@/lib/platformApi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

const NO_PERIOD = 'NONE'

const toDateInput = (iso?: string | null) => (iso ? iso.slice(0, 10) : '')
const toIso = (date: string) => (date ? `${date}T00:00:00Z` : null)

const PERIOD_LABEL: Record<BillingPeriod, string> = {
  MONTHLY: 'Mensual',
  SEMESTRAL: 'Semestral',
  ANNUAL: 'Anual',
}

/** Mirrors BillingPeriod.endFrom in the backend: monthly is a flat 30 days. */
const addPeriod = (from: Date, period: BillingPeriod) => {
  const d = new Date(from)
  if (period === 'MONTHLY') d.setUTCDate(d.getUTCDate() + 30)
  else d.setUTCMonth(d.getUTCMonth() + (period === 'SEMESTRAL' ? 6 : 12))
  return d
}

/** Operator-side form for the dates a restaurant admin sees under Settings > Plan. */
export const SubscriptionCard = ({
  restaurant,
  onSaved,
}: {
  restaurant: PlatformRestaurantDetail
  onSaved: () => void
}) => {
  const [start, setStart] = useState(toDateInput(restaurant.planStartedAt))
  const [period, setPeriod] = useState<string>(restaurant.billingPeriod ?? NO_PERIOD)
  const [end, setEnd] = useState(toDateInput(restaurant.planPeriodEnd))

  const endBeforeStart = !!start && !!end && end <= start

  const [renewPeriod, setRenewPeriod] = useState<BillingPeriod>(restaurant.billingPeriod ?? 'MONTHLY')
  const [confirmingRenew, setConfirmingRenew] = useState(false)

  // Same rule as the backend: extend from the current end, or from today when already overdue.
  const currentEnd = restaurant.planPeriodEnd ? new Date(restaurant.planPeriodEnd) : null
  const [now] = useState(() => new Date())
  const renewFrom = currentEnd && currentEnd > now ? currentEnd : now
  const nextPayment = addPeriod(renewFrom, renewPeriod).toLocaleDateString('es', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  })

  const renew = useMutation({
    mutationFn: () => platformRestaurantService.renewSubscription(restaurant.id, renewPeriod),
    onSuccess: () => {
      toast.success('Suscripción renovada')
      setConfirmingRenew(false)
      onSaved()
    },
    onError: () => toast.error('No se pudo renovar la suscripción'),
  })

  const save = useMutation({
    mutationFn: () =>
      platformRestaurantService.updateSubscription(restaurant.id, {
        planStartedAt: toIso(start),
        billingPeriod: period === NO_PERIOD ? null : (period as BillingPeriod),
        planPeriodEnd: toIso(end),
      }),
    onSuccess: () => {
      toast.success('Suscripción guardada')
      onSaved()
    },
    onError: () => toast.error('No se pudo guardar la suscripción'),
  })

  return (
    <Card className="rounded-2xl">
      <CardHeader>
        <CardTitle className="text-base">Suscripción</CardTitle>
      </CardHeader>
      <CardContent className="grid grid-cols-1 gap-4 text-sm sm:grid-cols-3">
        <div className="flex flex-col gap-3 rounded-xl border border-zinc-200 p-4 sm:col-span-3">
          <div className="flex flex-wrap items-end gap-3">
            <div className="flex flex-col gap-1.5">
              <Label>Renovar por</Label>
              <Select
                value={renewPeriod}
                onValueChange={(v) => {
                  setRenewPeriod(v as BillingPeriod)
                  setConfirmingRenew(false)
                }}
              >
                <SelectTrigger className="w-40" aria-label="Período de renovación">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(Object.keys(PERIOD_LABEL) as BillingPeriod[]).map((p) => (
                    <SelectItem key={p} value={p}>
                      {PERIOD_LABEL[p]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {!confirmingRenew && <Button onClick={() => setConfirmingRenew(true)}>Renovar</Button>}
          </div>
          {confirmingRenew && (
            <div className="flex flex-wrap items-center gap-3">
              <span>
                Próximo pago: <strong>{nextPayment}</strong>
              </span>
              <Button disabled={renew.isPending} onClick={() => renew.mutate()}>
                Confirmar renovación
              </Button>
              <Button variant="ghost" disabled={renew.isPending} onClick={() => setConfirmingRenew(false)}>
                Cancelar
              </Button>
            </div>
          )}
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="plan-start">Inicio del plan</Label>
          <Input id="plan-start" type="date" value={start} onChange={(e) => setStart(e.target.value)} />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label>Período</Label>
          <Select value={period} onValueChange={setPeriod}>
            <SelectTrigger className="w-full" aria-label="Período">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NO_PERIOD}>Sin definir</SelectItem>
              <SelectItem value="MONTHLY">Mensual</SelectItem>
              <SelectItem value="SEMESTRAL">Semestral</SelectItem>
              <SelectItem value="ANNUAL">Anual</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="plan-end">Fin del período (próximo pago)</Label>
          <Input id="plan-end" type="date" value={end} onChange={(e) => setEnd(e.target.value)} />
        </div>
        {endBeforeStart && (
          <p className="text-sm text-red-600 sm:col-span-3">El fin del período debe ser posterior al inicio.</p>
        )}
        <div className="sm:col-span-3">
          <Button disabled={save.isPending || endBeforeStart} onClick={() => save.mutate()}>
            Guardar suscripción
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
