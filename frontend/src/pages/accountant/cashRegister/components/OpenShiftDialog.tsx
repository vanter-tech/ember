import { useState } from 'react'
import { Button } from '@/components/ui/button'
import toast from 'react-hot-toast'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'
import { extractPlanGateError } from '@/lib/planGate'
import { DenominationCounter } from './DenominationCounter'
import type { DenominationCount } from '@/lib/denominations'

export const OpenShiftDialog = () => {
  const { t } = useTranslation('waiter')
  const { t: tCommon } = useTranslation('common')
  const { activeModal, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const [breakdown, setBreakdown] = useState<DenominationCount[]>([])
  const [total, setTotal] = useState(0)

  const handleCounterChange = (nextBreakdown: DenominationCount[], nextTotal: number) => {
    setBreakdown(nextBreakdown)
    setTotal(nextTotal)
  }

  const mutation = useMutation({
    mutationFn: () => cashShiftService.open(total, breakdown),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cashShiftCurrent'] })
      toast.success(t('shiftOpenedToast'))
      setBreakdown([])
      setTotal(0)
      closeModal()
    },
    onError: (error) => {
      const gate = extractPlanGateError(error)
      toast.error(
        gate
          ? tCommon('planGateUpgradeToast', { plan: gate.requiredPlan ?? '' })
          : t('shiftOpenErrorToast'),
      )
    },
  })

  return (
    <Dialog open={activeModal === 'OPEN_SHIFT'} onOpenChange={(isOpen) => !isOpen && closeModal()}>
      <DialogContent className="sm:max-w-lg rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">{t('openShiftTitle')}</DialogTitle>
        </DialogHeader>

        <DenominationCounter onChange={handleCounterChange} />

        <DialogFooter className="mt-5">
          <Button type="button" variant="outline" onClick={closeModal} disabled={mutation.isPending}>
            {t('cancelButton')}
          </Button>
          <Button onClick={() => mutation.mutate()} disabled={mutation.isPending}>
            {mutation.isPending ? t('openingLabel') : t('openCajaButton')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
