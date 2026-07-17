import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Minus, Plus } from 'lucide-react'
import { useUIStore } from '@/store/uiStore'
import { useState } from 'react'
import { useMutation, useQueryClient} from '@tanstack/react-query'
import { SessionTableService } from '@/lib/api'
import toast from 'react-hot-toast'
import { QRCodeSVG } from 'qrcode.react'

export const ParticipantQrModal = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const [participants, setParticipants] = useState(1)
  const [QrToken, setQrToken] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: async ({
      tableId,
      maxParticipants,
    }: {
      tableId: string
      maxParticipants: number
    }) => {
      const newSession = await SessionTableService.createSession(
        tableId,
        maxParticipants
      )

      const realId = (newSession as any).id || newSession.sessionId 

      if (!realId) {
        throw new Error('El servidor no devolvio el ID de la session')
      }
      return await SessionTableService.getQrToken(realId)
    },
    onSuccess: (data) => {
      setQrToken(data.qrToken)
      queryClient.invalidateQueries({ queryKey: ['dashboardData'] })
      toast.success('Mesa abierta con exito')
    },
  })

  const clientJoinUrl = QrToken
  ? `${window.location.origin}/menu/join?token=${QrToken}`
  : '';

  return (
    <Dialog
      open={activeModal == 'PARTICIPANTS_QR'}
      onOpenChange={(isOpen) => !isOpen && closeModal()}
    >
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            Asignar Mesa
          </DialogTitle>
          <DialogDescription>
            <p className="text-zinc-500 text-sm mt-1">
              Configure los detalles de la mesa antes de abrirla
            </p>
          </DialogDescription>
        </DialogHeader>
        <div className="flex justify-between items-center bg-zinc-100 rounded-4xl p-2">
          <button
            className="cursor-pointer rounded-full bg-zinc-200 p-3"
            onClick={() => {
              setParticipants((prev) => Math.max(1, prev - 1))
            }}
          >
            <Minus />
          </button>
          <span className="text-2xl font-bold">{participants}</span>
          <button
            className="cursor-pointer rounded-full bg-zinc-200 p-3"
            onClick={() => {
              setParticipants((prev) => Math.max(1, prev + 1))
            }}
          >
            <Plus />
          </button>
        </div>
        <div
          className="border-dashed border-2 h-48 my-6 flex items-center justify-center
        roundex-2xl"
        >
          
          {QrToken ? (
            <QRCodeSVG
            value={clientJoinUrl}
            size={180}
            bgColor={"#ffffff"}
            fgColor={"#000"}
            level={"Q"}
            />
          ) : (
            <span className="text-zinc-400">El codigo qr aparecera aca.</span>
          )}
        </div>
        <DialogFooter>
          <Button
            className="w-full"
            type="submit"
            onClick={() => {
              mutation.mutate({
                tableId: modalPayload.tableId!,
                maxParticipants: participants,
              })
            }}
            disabled={mutation.isPending}
          >
            {mutation.isPending ? 'Guardando' : 'Abrir Mesa y Generar QR'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
