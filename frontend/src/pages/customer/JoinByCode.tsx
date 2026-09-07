import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { isAxiosError } from 'axios'
import toast from 'react-hot-toast'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/store/authStore'
import { useSessionStore } from '@/store/sessionStore'
import { SessionTableService } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'

const CODE_LENGTH = 5

/**
 * Public landing for a diner who only has the table's 5-character code — no QR scan, no account.
 * POSTs the code to /sessions/join-as-guest, which mints a throwaway guest identity gated on a
 * valid open table, then swaps in the tenant-scoped token it returns (same as the QR guest flow).
 */
export const JoinByCode = () => {
  const { t } = useTranslation('customer')
  const navigate = useNavigate()
  const setAuth = useAuthStore((s) => s.setAuth)
  const setSession = useSessionStore((s) => s.setSession)

  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const submit = async () => {
    if (code.length !== CODE_LENGTH || submitting) return
    setSubmitting(true)
    try {
      const data = await SessionTableService.joinAsGuest({
        joinCode: code,
        name: name.trim() || undefined,
      })
      if (data.token) setAuth({ token: data.token })
      if (data.session) setSession(data.session as never)
      toast.success(t('joinSuccessToast'))
      navigate('/customer/menu', { replace: true })
    } catch (error) {
      const status = isAxiosError(error) ? error.response?.status : undefined
      if (status === 404) toast.error(t('joinCodeInvalidToast'))
      else if (status === 409) toast.error(t('joinBlockedOtherTableToast'))
      else toast.error(t('genericErrorToast'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-6">
      <Card className="w-full max-w-sm rounded-3xl">
        <CardHeader>
          <CardTitle className="text-2xl font-bold text-[#8c1717]">{t('codeJoinTitle')}</CardTitle>
          <p className="text-sm text-gray-500">{t('codeJoinSubtitle')}</p>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <Input
            autoFocus
            value={code}
            maxLength={CODE_LENGTH}
            inputMode="text"
            autoCapitalize="characters"
            placeholder={t('codeJoinCodePlaceholder')}
            className="text-center text-2xl font-bold tracking-[0.4em] uppercase"
            onChange={(e) => setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))}
            onKeyDown={(e) => e.key === 'Enter' && submit()}
          />
          <Input
            value={name}
            maxLength={50}
            placeholder={t('qrJoinNamePlaceholder')}
            aria-label={t('qrJoinGuestNameLabel')}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && submit()}
          />
          <Button
            className="h-12 w-full text-lg font-bold hover:bg-[#6a1111]"
            disabled={code.length !== CODE_LENGTH || submitting}
            onClick={submit}
          >
            {submitting ? t('qrJoinSubmitting') : t('qrJoinGuestSubmit')}
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
