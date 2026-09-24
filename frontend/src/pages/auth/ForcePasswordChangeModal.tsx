import { useState } from 'react'
import axios from 'axios'
import toast from 'react-hot-toast'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { PasswordInput } from '@/components/PasswordInput'
import { userProfileService } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { useTranslation } from '@/lib/i18n'

/** Blocks the whole app (F-25) whenever a platform operator has reset the caller's password —
 *  `ProtectedRoute` renders this instead of `<Outlet/>` while `mustChangePassword` is true. Not
 *  dismissible: no close button, outside click or Escape, since there is nothing safe to fall
 *  back to (the caller is stuck on a temp password until they set a real one). */
export const ForcePasswordChangeModal = () => {
  const { t: tAuth } = useTranslation('auth')
  const setAuth = useAuthStore((s) => s.setAuth)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    setError(null)
    if (newPassword !== confirmPassword) {
      setError(tAuth('passwordMismatchError'))
      return
    }
    setBusy(true)
    try {
      const res = await userProfileService.changePassword({ currentPassword, newPassword })
      setAuth(res)
      toast.success(tAuth('forcePasswordChangeSuccessToast'))
    } catch (err) {
      const detail = axios.isAxiosError(err)
        ? (err.response?.data as { detail?: string } | undefined)?.detail
        : undefined
      setError(detail ?? tAuth('forcePasswordChangeErrorToast'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={() => {}}>
      <DialogContent
        className="sm:max-w-sm rounded-3xl p-6"
        showCloseButton={false}
        onInteractOutside={(e) => e.preventDefault()}
        onEscapeKeyDown={(e) => e.preventDefault()}
      >
        <DialogHeader>
          <DialogTitle>{tAuth('forcePasswordChangeTitle')}</DialogTitle>
          <DialogDescription>{tAuth('forcePasswordChangeDescription')}</DialogDescription>
        </DialogHeader>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            void submit()
          }}
          className="flex flex-col gap-3"
        >
          <div className="flex flex-col gap-1">
            <label htmlFor="force-pwd-current" className="text-sm font-medium">
              {tAuth('currentPasswordLabel')}
            </label>
            <PasswordInput
              id="force-pwd-current"
              autoFocus
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="force-pwd-new" className="text-sm font-medium">
              {tAuth('newPasswordLabel')}
            </label>
            <PasswordInput
              id="force-pwd-new"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="force-pwd-confirm" className="text-sm font-medium">
              {tAuth('confirmPasswordLabel')}
            </label>
            <PasswordInput
              id="force-pwd-confirm"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />
          </div>
          {error && <p className="text-sm text-red-600">{error}</p>}
          <Button
            type="submit"
            disabled={busy || !currentPassword || !newPassword || !confirmPassword}
          >
            {tAuth('forcePasswordChangeSubmit')}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  )
}
