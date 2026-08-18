import { useState, useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { useUIStore } from '@/store/uiStore'
import { billingService } from '@/lib/api'
import toast from 'react-hot-toast'
import { useTranslation } from '@/lib/i18n'

export const RefundPaymentModal = () => {
  const { t } = useTranslation('waiter')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const [amount, setAmount] = useState('')
  const [reason, setReason] = useState('')

  const isOpen = activeModal === 'REFUND_PAYMENT'

  const { data: payments } = useQuery({
    queryKey: ['billPayments', modalPayload?.billId],
    queryFn: () => billingService.listPayments(modalPayload.billId),
    enabled: isOpen && !!modalPayload?.billId,
  })

  // Prefer an exact payment-id match when the caller already has one in hand (ShiftHistoryTable.tsx,
  // the waiter cash-register payments list) — a participant can have more than one payment on the
  // same bill (e.g. a full refund returned their split to UNPAID and they paid again), and matching
  // by name alone risks picking an already-fully-refunded payment. Fall back to a name match only
  // when no id was given (the live waiter-table view only has a participant name at that point).
  const payment = modalPayload?.paymentId
    ? payments?.find((p) => p.id === modalPayload.paymentId)
    : payments?.find((p) => p.participantName === modalPayload?.participantName)

  useEffect(() => {
    if (payment) {
      setAmount(String(payment.remaining ?? 0))
    }
    // Only re-sync when the SELECTED payment actually changes (undefined -> id, or a different
    // id), not on every background refetch of the same payment — otherwise a stale-time refetch
    // (e.g. window refocus) would silently overwrite whatever amount the user already typed.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [payment?.id])

  const mutation = useMutation({
    mutationFn: () =>
      billingService.refundPayment(payment!.id!, amount ? Number(amount) : undefined, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['billPayments', modalPayload.billId] })
      // This modal is also opened from the waiter cash-register page (CashRegister.tsx), whose
      // payments list comes from ['cashShiftDetail', shift.id] — a different query key this modal
      // doesn't know the id for, and which has no WebSocket subscription to self-update. Invalidate
      // the whole family by key prefix so that page's payments list refetches too.
      queryClient.invalidateQueries({ queryKey: ['cashShiftDetail'] })
      toast.success('Reembolso registrado.')
      handleClose()
    },
    onError: () => toast.error('No se pudo registrar el reembolso. ¿Hay una caja abierta?'),
  })

  const handleClose = () => {
    setAmount('')
    setReason('')
    closeModal()
  }

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">{t('refundPaymentTitle')}</DialogTitle>
          <DialogDescription className="text-zinc-500 text-sm mt-1">
            {modalPayload?.participantName} {t('availableBalanceLabel', { amount: payment?.remaining !== undefined ? `$${payment.remaining.toFixed(2)}` : '—' })}
          </DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-3">
          <Input
            type="number"
            step="0.01"
            placeholder={t('refundAmountPlaceholder')}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
          <Textarea
            placeholder={t('refundReasonPlaceholder')}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </div>
        <DialogFooter className="mt-4">
          <Button variant="outline" onClick={handleClose}>
            {t('cancelButton')}
          </Button>
          <Button
            variant="destructive"
            onClick={() => mutation.mutate()}
            disabled={
              mutation.isPending ||
              !payment ||
              (payment?.remaining ?? 0) <= 0 ||
              reason.trim().length === 0
            }
          >
            {t('refundButton')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
