import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { useUIStore } from '@/store/uiStore'
import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { billingService, type SplitMethod } from '@/lib/api'
import toast from 'react-hot-toast'
import { useTranslation } from '@/lib/i18n'

export const ChargeTableModal = () => {
  const { t } = useTranslation('waiter')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const [splitMethod, setSplitMethod] = useState<SplitMethod>('BY_CONSUMPTION')

  const isOpen = activeModal === 'CHARGE_TABLE'

  const mutation = useMutation({
    mutationFn: () =>
      billingService.requestBilling(
        modalPayload.sessionId,
        splitMethod,
        splitMethod === 'EQUAL_PARTS' ? modalPayload.participantCount : undefined
      ),
    onSuccess: () => {
      toast.success(t('billRequestedToast'))
      handleClose()
    },
    onError: () => {
      toast.error(t('billCalculateErrorToast'))
    },
  })

  const handleClose = () => {
    setSplitMethod('BY_CONSUMPTION')
    closeModal()
  }

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('chargeMesaLabel')}
          </DialogTitle>
          <DialogDescription className="text-zinc-500 text-sm mt-1">
            {t('chargeMesaDescription')}
          </DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-3">
          <button
            type="button"
            onClick={() => setSplitMethod('BY_CONSUMPTION')}
            className={`text-left rounded-2xl border-2 p-4 transition-colors ${
              splitMethod === 'BY_CONSUMPTION'
                ? 'border-[#8B0000] bg-[#8B0000]/5'
                : 'border-zinc-200'
            }`}
          >
            <span className="font-semibold block">{t('byConsumptionLabel')}</span>
            <span className="text-sm text-zinc-500">
              {t('byConsumptionDescription')}
            </span>
          </button>
          <button
            type="button"
            onClick={() => setSplitMethod('EQUAL_PARTS')}
            className={`text-left rounded-2xl border-2 p-4 transition-colors ${
              splitMethod === 'EQUAL_PARTS'
                ? 'border-[#8B0000] bg-[#8B0000]/5'
                : 'border-zinc-200'
            }`}
          >
            <span className="font-semibold block">{t('equalPartsLabel')}</span>
            <span className="text-sm text-zinc-500">
              {t('equalPartsDescription', { count: modalPayload?.participantCount ?? 1 })}
            </span>
          </button>
        </div>
        <DialogFooter>
          <Button
            className="w-full"
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending}
          >
            {mutation.isPending ? t('calculatingLabel') : t('confirmChargeButton')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
