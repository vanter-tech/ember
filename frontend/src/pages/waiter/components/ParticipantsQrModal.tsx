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
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { SessionTableService } from '@/lib/api'
import toast from 'react-hot-toast'
import { QRCodeSVG } from 'qrcode.react'
import { useTranslation } from '@/lib/i18n'

export const ParticipantQrModal = () => {
  const { t } = useTranslation('waiter')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const [participants, setParticipants] = useState(1)
  const [QrToken, setQrToken] = useState<string | null>(null)
  const [joinCode, setJoinCode] = useState<string | null>(null)
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

      const realId = newSession.sessionId

      if (!realId) {
        throw new Error('El servidor no devolvio el ID de la session')
      }
      const qrData = await SessionTableService.getQrToken(realId)
      return { qrToken: qrData.qrToken, joinCode: newSession.joinCode }
    },
    onSuccess: (data) => {
      setQrToken(data.qrToken)
      setJoinCode(data.joinCode ?? '')
      queryClient.invalidateQueries({ queryKey: ['dashboardData'] })
      toast.success(t('tableOpenedToast'))
    },
  })

  const clientJoinUrl = QrToken
    ? `${window.location.origin}/menu/join?token=${QrToken}`
    : ''

  const handleClose = () => {
    setJoinCode(null)
    setQrToken(null)
    setParticipants(1)
    closeModal()
  }

  return (
    <Dialog
      open={activeModal == 'PARTICIPANTS_QR'}
      onOpenChange={(isOpen) => {
        if (!isOpen) return handleClose()
      }}
    >
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('assignTableLabel')}
          </DialogTitle>
          <DialogDescription className="text-zinc-500 text-sm mt-1">
            {t('assignTableDescription')}
          </DialogDescription>
        </DialogHeader>
        <p className="text-zinc-500 text-sm">
          {t('selectParticipantCountLabel')}
        </p>
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
              bgColor={'#ffffff'}
              fgColor={'#000'}
              level={'Q'}
            />
          ) : (
            <span className="text-zinc-400">{t('qrPlaceholderLabel')}</span>
          )}
        </div>
        <div
          className="flex items-center justify-center
        roundex-2xl"
        >
          {joinCode && (
            <div className="flex w-full flex-col items-center bg-zinc-100 rounded-4xl p-3 gap-2">
              <span className="text-lg font-bold">
                {t('joinCodeLabel')}
              </span>
              <span className="text-[#8c1717] text-3xl font-bold">
                {joinCode}
              </span>
            </div>
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
            {mutation.isPending ? t('qrSavingLabel') : t('openTableGenerateQrButton')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
