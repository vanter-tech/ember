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

export const ChargeTableModal = () => {
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
      toast.success('Cuenta solicitada.')
      handleClose()
    },
    onError: () => {
      toast.error('No se pudo calcular la cuenta.')
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
            Cobrar Mesa
          </DialogTitle>
          <DialogDescription className="text-zinc-500 text-sm mt-1">
            Elige cómo se dividirá la cuenta entre los participantes.
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
            <span className="font-semibold block">Por consumo</span>
            <span className="text-sm text-zinc-500">
              Cada participante paga lo que pidió.
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
            <span className="font-semibold block">Partes iguales</span>
            <span className="text-sm text-zinc-500">
              El total se divide entre {modalPayload?.participantCount ?? 1}{' '}
              participante(s).
            </span>
          </button>
        </div>
        <DialogFooter>
          <Button
            className="w-full"
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending}
          >
            {mutation.isPending ? 'Calculando...' : 'Confirmar y calcular cuenta'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
