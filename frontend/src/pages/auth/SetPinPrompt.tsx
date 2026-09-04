import { useState } from 'react'
import toast from 'react-hot-toast'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { authService } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'

export const SetPinPrompt = ({
  email,
  defaultPassword,
  onDone,
}: {
  email: string
  defaultPassword?: string
  onDone: () => void
}) => {
  const { t: tAuth } = useTranslation('auth')
  const [currentPassword, setCurrentPassword] = useState(defaultPassword ?? '')
  const [pin, setPin] = useState('')
  const [confirmPin, setConfirmPin] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    setError(null)
    if (!/^\d{4,6}$/.test(pin) || pin !== confirmPin) {
      setError(tAuth('setPinMismatch'))
      return
    }
    setBusy(true)
    try {
      await authService.setPin({ currentPassword, pin })
      toast.success(tAuth('setPinSavedToast'))
      onDone()
    } catch {
      setError(tAuth('unauthorizedToast'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onDone()}>
      <DialogContent className="sm:max-w-sm rounded-3xl p-6">
        <DialogHeader>
          <DialogTitle>{tAuth('setPinCtaTitle')}</DialogTitle>
          <p className="text-xs text-zinc-400">{email}</p>
        </DialogHeader>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            void submit()
          }}
          className="flex flex-col gap-3"
        >
          <label htmlFor="setpin-password" className="text-sm font-medium">
            {tAuth('setPinCurrentPassword')}
          </label>
          <Input
            id="setpin-password"
            aria-label={tAuth('setPinCurrentPassword')}
            type="password"
            autoFocus
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
          />
          <label htmlFor="setpin-pin" className="text-sm font-medium">
            {tAuth('setPinNewPin')}
          </label>
          <Input
            id="setpin-pin"
            aria-label={tAuth('setPinNewPin')}
            type="text"
            inputMode="numeric"
            maxLength={6}
            value={pin}
            onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
          />
          <label htmlFor="setpin-confirm" className="text-sm font-medium">
            {tAuth('setPinConfirm')}
          </label>
          <Input
            id="setpin-confirm"
            aria-label={tAuth('setPinConfirm')}
            type="text"
            inputMode="numeric"
            maxLength={6}
            value={confirmPin}
            onChange={(e) => setConfirmPin(e.target.value.replace(/\D/g, ''))}
          />
          {error && <p className="text-sm text-red-600">{error}</p>}
          <div className="flex items-center gap-2">
            <Button type="submit" disabled={busy} className="flex-1">
              {tAuth('setPinSave')}
            </Button>
            <button
              type="button"
              className="text-sm text-zinc-500 hover:underline px-3"
              onClick={() => onDone()}
            >
              {tAuth('setPinNotNow')}
            </button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
