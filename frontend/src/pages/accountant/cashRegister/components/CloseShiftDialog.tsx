import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import toast from 'react-hot-toast'
import axios from 'axios'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { cashShiftService, type CashShiftResponse } from '@/lib/api'
import { formatCurrency } from '@/lib/format'
import { useTranslation } from '@/lib/i18n'
import { DenominationCounter } from './DenominationCounter'
import type { DenominationCount } from '@/lib/denominations'

// Matches CashShiftService.closeShift's "Cannot close cash shift: N table(s) still have an
// open session" detail (backend) so the toast can surface the open-table count without
// showing the raw English backend string to Spanish users.
const extractOpenTablesCount = (detail: unknown): number | null => {
  if (typeof detail !== 'string') return null
  const match = detail.match(/^Cannot close cash shift: (\d+) table/)
  return match ? Number(match[1]) : null
}

export const CloseShiftDialog = () => {
  const { t } = useTranslation('waiter')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const shiftId = modalPayload?.shiftId as number | undefined
  const [breakdown, setBreakdown] = useState<DenominationCount[]>([])
  const [total, setTotal] = useState(0)
  const [notes, setNotes] = useState('')
  const [result, setResult] = useState<CashShiftResponse | null>(null)

  const handleCounterChange = (nextBreakdown: DenominationCount[], nextTotal: number) => {
    setBreakdown(nextBreakdown)
    setTotal(nextTotal)
  }

  const handleOpenChange = (isOpen: boolean) => {
    if (!isOpen) {
      setBreakdown([])
      setTotal(0)
      setNotes('')
      setResult(null)
      closeModal()
    }
  }

  const mutation = useMutation({
    mutationFn: () => cashShiftService.close(shiftId!, total, breakdown, notes.trim() || undefined),
    onSuccess: (closed) => {
      queryClient.invalidateQueries({ queryKey: ['cashShiftCurrent'] })
      setResult(closed)
    },
    onError: (error) => {
      const count = axios.isAxiosError(error)
        ? extractOpenTablesCount(error.response?.data?.detail)
        : null
      if (count !== null) {
        // The shift can't close until these tables are closed by a waiter — the accountant has
        // no floor view to send them to, so just dismiss and report the count (the stale-shift
        // alert re-appears once the tables are actually closed).
        toast.error(t('shiftCloseTablesOpenToast', { count }))
        handleOpenChange(false)
      } else {
        toast.error(t('shiftCloseErrorToast'))
      }
    },
  })

  return (
    <Dialog open={activeModal === 'CLOSE_SHIFT'} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-lg rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">{t('closeShiftTitle')}</DialogTitle>
        </DialogHeader>

        {!result ? (
          <div className="flex flex-col gap-5">
            <DialogDescription>{t('closeShiftDescription')}</DialogDescription>
            <DenominationCounter onChange={handleCounterChange} />
            <div className="flex flex-col gap-2">
              <label htmlFor="close-shift-notes" className="text-sm font-medium">
                {t('closeNotesLabel')}
              </label>
              <Textarea
                id="close-shift-notes"
                className="rounded-xl"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder={t('closeNotesPlaceholder')}
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={closeModal} disabled={mutation.isPending}>
                {t('cancelButton')}
              </Button>
              <Button onClick={() => mutation.mutate()} disabled={mutation.isPending || !shiftId}>
                {mutation.isPending ? t('closingShiftLabel') : t('confirmCountButton')}
              </Button>
            </DialogFooter>
          </div>
        ) : (
          <div className="flex flex-col gap-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="text-xs text-muted-foreground">{t('expectedLabel')}</p>
                <p className="text-lg font-bold">{formatCurrency(result.expectedCash ?? 0)}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">{t('countedLabel')}</p>
                <p className="text-lg font-bold">{formatCurrency(result.countedCash ?? 0)}</p>
              </div>
              <div className="col-span-2">
                <p className="text-xs text-muted-foreground">{t('differenceLabel')}</p>
                <p
                  className={`text-lg font-bold ${
                    (result.variance ?? 0) === 0
                      ? 'text-primary'
                      : (result.variance ?? 0) > 0
                        ? 'text-emerald-600'
                        : 'text-destructive'
                  }`}
                >
                  {formatCurrency(result.variance ?? 0)}
                </p>
              </div>
            </div>
            <DialogFooter>
              <Button onClick={() => handleOpenChange(false)}>{t('closeButton')}</Button>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
