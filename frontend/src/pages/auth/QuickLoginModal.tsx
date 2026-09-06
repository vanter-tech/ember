import { useState } from 'react'
import axios from 'axios'
import toast from 'react-hot-toast'
import { useNavigate } from 'react-router-dom'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import { authService } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import {
  useQuickAccessStore,
  type QuickAccessProfile,
} from '@/store/quickAccessStore'
import { navigateForRole } from './navigateForRole'
import { useTranslation } from '@/lib/i18n'

export const QuickLoginModal = ({
  profile,
  onClose,
}: {
  profile: QuickAccessProfile
  onClose: () => void
}) => {
  const { t: tAuth } = useTranslation('auth')
  const navigate = useNavigate()
  const { setAuth } = useAuthStore()
  const { remember } = useQuickAccessStore()
  const [mode, setMode] = useState<'pin' | 'password' | null>(null)
  const [value, setValue] = useState('')
  const [hint, setHint] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const pickMode = (next: 'pin' | 'password') => {
    setMode(next)
    setValue('')
    setError(null)
    setHint(null)
  }

  const submit = async () => {
    if (!mode) return
    setBusy(true)
    setError(null)
    try {
      const res =
        mode === 'pin'
          ? await authService.loginPin({ email: profile.email, pin: value })
          : await authService.login({ email: profile.email, password: value })
      setAuth(res)
      remember({
        email: profile.email,
        name: res.name ?? profile.name,
        role: res.role ?? profile.role,
      })
      toast.success(tAuth('loginSuccessToast'))
      onClose()
      await navigateForRole(res, navigate, { tAuth })
    } catch (err) {
      const status = axios.isAxiosError(err) ? err.response?.status : undefined
      const code = axios.isAxiosError(err)
        ? (err.response?.data as { code?: string })?.code
        : undefined
      if (mode === 'pin' && (code === 'PIN_NOT_SET' || status === 409)) {
        setMode('password')
        setValue('')
        setHint(tAuth('quickLoginPinNotSetHint'))
      } else if (mode === 'pin' && (code === 'PIN_LOCKED' || status === 423)) {
        setMode('password')
        setValue('')
        setHint(tAuth('quickLoginPinLockedHint'))
      } else if (mode === 'pin') {
        setError(tAuth('quickLoginPinIncorrect'))
      } else {
        setError(tAuth('unauthorizedToast'))
      }
    } finally {
      setBusy(false)
    }
  }

  const fieldLabel =
    mode === 'pin' ? tAuth('quickLoginPinLabel') : tAuth('passwordPlaceholder')

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-sm rounded-3xl p-6">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-3">
            <span
              className="flex h-10 w-10 items-center justify-center rounded-full text-white font-bold"
              style={{ backgroundColor: `hsl(${profile.colorSeed} 55% 45%)` }}
            >
              {profile.initials}
            </span>
            {/* QA_SIMULATION_REPORT.md E-21: this dialog opens before any PIN/password is
                verified, on a device this feature is explicitly built to be shared across staff
                (report 341/343) — showing the raw email here handed a real employee address to
                anyone who clicks a chip. Name + avatar + role is enough to confirm "that's me". */}
            <span className="flex flex-col">
              <span className="text-base font-semibold">{profile.name}</span>
              <span className="text-xs text-zinc-400 uppercase tracking-wide">{profile.role}</span>
            </span>
          </DialogTitle>
          <DialogDescription>{tAuth('quickLoginDialogDescription')}</DialogDescription>
        </DialogHeader>
        {hint && <p className="text-sm text-amber-600">{hint}</p>}
        <div className="grid grid-cols-2 gap-2">
          {(['password', 'pin'] as const).map((m) => (
            <button
              key={m}
              type="button"
              aria-pressed={mode === m}
              onClick={() => pickMode(m)}
              className={cn(
                'rounded-xl border px-3 py-2 text-sm font-medium transition-colors',
                mode === m
                  ? 'border-[#920703] bg-[#920703]/5 text-[#920703]'
                  : 'border-input text-zinc-600 hover:bg-zinc-50'
              )}
            >
              {m === 'pin'
                ? tAuth('quickLoginPinLabel')
                : tAuth('quickLoginPasswordLabel')}
            </button>
          ))}
        </div>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            void submit()
          }}
          className="flex flex-col gap-3"
        >
          {mode && (
            <>
              <label htmlFor="quicklogin-field" className="text-sm font-medium">
                {fieldLabel}
              </label>
              <Input
                id="quicklogin-field"
                aria-label={fieldLabel}
                type={mode === 'pin' ? 'text' : 'password'}
                inputMode={mode === 'pin' ? 'numeric' : undefined}
                maxLength={mode === 'pin' ? 6 : undefined}
                autoFocus
                value={value}
                onChange={(e) =>
                  setValue(
                    mode === 'pin'
                      ? e.target.value.replace(/\D/g, '')
                      : e.target.value
                  )
                }
                placeholder={
                  mode === 'pin'
                    ? tAuth('quickLoginPinPlaceholder')
                    : tAuth('passwordPlaceholder')
                }
              />
              {error && <p className="text-sm text-red-600">{error}</p>}
            </>
          )}
          <Button type="submit" disabled={busy || !mode || value.length < 4}>
            {tAuth('quickLoginSubmit')}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  )
}
