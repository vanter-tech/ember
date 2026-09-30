import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Button } from '@/components/ui/button'
import { cashShiftService } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'

// Must match CashShiftDeadlineService.ALLOWED_PROLONG_MINUTES on the backend (which enforces it).
const PROLONG_OPTIONS_MINUTES = [30, 60, 120, 180, 240] as const

const formatMinutes = (minutes: number) => (minutes < 60 ? `${minutes} min` : `${minutes / 60} h`)

/**
 * Lets the accountant extend the open shift by a chosen amount instead of waiting for the periodic
 * reminder modal (which only offers one hour). A shift from a previous business day can only be
 * closed, the same rule the reminder applies, so the control is disabled for it.
 */
export const ProlongShiftControl = ({ shiftId, stale }: { shiftId: number; stale: boolean }) => {
  const { t } = useTranslation('waiter')
  const queryClient = useQueryClient()
  const [minutes, setMinutes] = useState<number>(60)

  const mutation = useMutation({
    mutationFn: () => cashShiftService.prolong(shiftId, minutes),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cashShiftCurrent'] })
      toast.success(t('cashShiftProlongedByToast', { time: formatMinutes(minutes) }))
    },
    onError: () => toast.error(t('cashShiftProlongErrorToast')),
  })

  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center gap-2">
        <select
          aria-label={t('cashShiftProlongSelectLabel')}
          className="h-9 rounded-md border border-input bg-background px-3 text-sm disabled:opacity-50"
          value={minutes}
          disabled={stale || mutation.isPending}
          onChange={(e) => setMinutes(Number(e.target.value))}
        >
          {PROLONG_OPTIONS_MINUTES.map((m) => (
            <option key={m} value={m}>
              {formatMinutes(m)}
            </option>
          ))}
        </select>
        <Button variant="outline" disabled={stale || mutation.isPending} onClick={() => mutation.mutate()}>
          {t('cashShiftProlongSelectedButton')}
        </Button>
      </div>
      {stale && <p className="text-xs text-muted-foreground">{t('cashShiftProlongStaleHint')}</p>}
    </div>
  )
}
