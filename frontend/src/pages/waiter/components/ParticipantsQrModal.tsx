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
import { useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { SessionTableService } from '@/lib/api'
import { isHubBuild } from '@/lib/isHubBuild'
import toast from 'react-hot-toast'
import { QRCodeSVG } from 'qrcode.react'
import { useTranslation } from '@/lib/i18n'

// Keeps `list` exactly `length` entries long, preserving what the waiter already typed.
const resize = (list: string[], length: number): string[] =>
  Array.from({ length }, (_, i) => list[i] ?? '')

export const ParticipantQrModal = () => {
  const { t } = useTranslation('waiter')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const navigate = useNavigate()
  const [participants, setParticipants] = useState(1)
  const [seatNames, setSeatNames] = useState<string[]>([''])
  const [QrToken, setQrToken] = useState<string | null>(null)
  const [joinCode, setJoinCode] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const setCount = (next: number) => {
    const clamped = Math.max(1, next)
    setParticipants(clamped)
    setSeatNames((prev) => resize(prev, clamped))
  }

  const mutation = useMutation({
    mutationFn: async ({
      tableId,
      maxParticipants,
    }: {
      tableId: string
      maxParticipants: number
    }) => {
      if (isHubBuild()) {
        const session = await SessionTableService.createSession(
          tableId,
          maxParticipants,
          seatNames.map((n) => n.trim())
        )
        return { sessionId: session.sessionId as string }
      }

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
      queryClient.invalidateQueries({ queryKey: ['dashboardData'] })
      toast.success(t('tableOpenedToast'))

      if (isHubBuild()) {
        const { sessionId } = data as { sessionId: string }
        handleClose()
        navigate(`/waiter/tables/${sessionId}`)
        return
      }

      const qr = data as { qrToken: string; joinCode?: string }
      setQrToken(qr.qrToken)
      setJoinCode(qr.joinCode ?? '')
    },
  })

  const clientJoinUrl = QrToken
    ? `${window.location.origin}/menu/join?token=${QrToken}`
    : ''

  const handleClose = () => {
    setJoinCode(null)
    setQrToken(null)
    setParticipants(1)
    setSeatNames([''])
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
            aria-label={t('removeSeatTitle')}
            className="cursor-pointer rounded-full bg-zinc-200 p-3"
            onClick={() => setCount(participants - 1)}
          >
            <Minus />
          </button>
          <span className="text-2xl font-bold">{participants}</span>
          <button
            aria-label={t('addSeatLabel')}
            className="cursor-pointer rounded-full bg-zinc-200 p-3"
            onClick={() => setCount(participants + 1)}
          >
            <Plus />
          </button>
        </div>

        {isHubBuild() ? (
          <div className="flex flex-col gap-2 my-4">
            <p className="text-zinc-500 text-sm">{t('seatNamesLabel')}</p>
            {seatNames.map((value, i) => (
              <input
                key={i}
                value={value}
                maxLength={50}
                placeholder={t('seatNamePlaceholder', { n: i + 1 })}
                onChange={(e) =>
                  setSeatNames((prev) =>
                    prev.map((v, j) => (j === i ? e.target.value : v))
                  )
                }
                className="w-full rounded-2xl border-2 border-zinc-200 px-4 py-2 outline-none focus:border-[#8c1717]"
              />
            ))}
          </div>
        ) : (
          <>
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
                  <span className="text-lg font-bold">{t('joinCodeLabel')}</span>
                  <span className="text-[#8c1717] text-3xl font-bold">
                    {joinCode}
                  </span>
                </div>
              )}
            </div>
          </>
        )}

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
            {mutation.isPending
              ? t('qrSavingLabel')
              : isHubBuild()
                ? t('openTableButton')
                : t('openTableGenerateQrButton')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
