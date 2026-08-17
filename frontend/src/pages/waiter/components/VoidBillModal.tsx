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

export const VoidBillModal = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')

  const isOpen = activeModal === 'VOID_BILL'

  const mutation = useMutation({
    mutationFn: () => billingService.voidBill(modalPayload.billId, reason),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ['bill', modalPayload.sessionId] })
      toast.success('Cuenta anulada.')
      handleClose()
    },
    onError: () => toast.error('No se pudo anular la cuenta.'),
  })

  const handleClose = () => {
    setReason('')
    closeModal()
  }

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">Anular Cuenta</DialogTitle>
          <DialogDescription className="text-zinc-500 text-sm mt-1">
            La cuenta calculada se anula y la mesa queda libre para recalcularla.
          </DialogDescription>
        </DialogHeader>
        <Textarea
          placeholder="Motivo de la anulación"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
        <DialogFooter className="mt-4">
          <Button variant="outline" onClick={handleClose}>
            Cancelar
          </Button>
          <Button
            variant="destructive"
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || reason.trim().length === 0}
          >
            Anular
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
