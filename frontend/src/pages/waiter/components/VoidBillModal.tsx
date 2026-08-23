import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { useUIStore } from '@/store/uiStore'
import { billingService } from '@/lib/api'
import toast from 'react-hot-toast'
import { useTranslation } from '@/lib/i18n'

export const VoidBillModal = () => {
  const { t } = useTranslation('waiter')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')

  const isOpen = activeModal === 'VOID_BILL'

  const mutation = useMutation({
    mutationFn: () => billingService.voidBill(modalPayload.billId, reason),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ['bill', modalPayload.sessionId] })
      toast.success(t('billVoidedToast'))
      handleClose()
    },
    onError: () => toast.error(t('billVoidErrorToast')),
  })

  const handleClose = () => {
    setReason('')
    closeModal()
  }

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">{t('voidBillLabel')}</DialogTitle>
          <DialogDescription className="text-zinc-500 text-sm mt-1">
            {t('voidBillDescription')}
          </DialogDescription>
        </DialogHeader>
        <Textarea
          placeholder={t('voidReasonPlaceholder')}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
        <DialogFooter className="mt-4">
          <Button variant="outline" onClick={handleClose}>
            {t('cancelButton')}
          </Button>
          <Button
            variant="destructive"
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || reason.trim().length === 0}
          >
            {t('voidButton')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
