import { useEffect, useMemo, useState } from 'react'
import { Navigate, useNavigate, useSearchParams, Link } from 'react-router-dom'
import { isAxiosError } from 'axios'
import toast from 'react-hot-toast'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/store/authStore'
import { useSessionStore } from '@/store/sessionStore'
import { SessionTableService } from '@/lib/api'
import { PENDING_QR_TOKEN_KEY, sessionIdFromQrToken } from '@/lib/qrToken'
import { useTranslation } from '@/lib/i18n'

/**
 * Landing page for the table QR (`/menu/join?token=…`). The QR is scanned with the phone's own
 * camera, so the visitor arrives here with no session — possibly not even logged in.
 *
 * - Not authenticated as CUSTOMER → park the token and bounce to /login; navigateForRole brings
 *   them back here once they're in.
 * - Authenticated → ask for a display name, then POST /sessions/{id}/join with the token and
 *   swap in the tenant-scoped token it returns, exactly like the 5-digit code flow.
 */
export const MenuJoin = () => {
  const { t } = useTranslation('customer')
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const token = useAuthStore((s) => s.token)
  const role = useAuthStore((s) => s.role)
  const setAuth = useAuthStore((s) => s.setAuth)
  const setSession = useSessionStore((s) => s.setSession)

  const qrToken = useMemo(
    () => params.get('token') ?? sessionStorage.getItem(PENDING_QR_TOKEN_KEY),
    [params],
  )
  const sessionId = qrToken ? sessionIdFromQrToken(qrToken) : null

  const [name, setName] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (qrToken) sessionStorage.setItem(PENDING_QR_TOKEN_KEY, qrToken)
  }, [qrToken])

  if (!qrToken || !sessionId) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 p-6">
        <Card className="w-full max-w-sm rounded-3xl">
          <CardContent className="flex flex-col items-center gap-4 py-10 text-center">
            <p className="text-gray-500">{t('qrJoinInvalidLink')}</p>
            <Button asChild variant="outline" className="rounded-2xl">
              <Link to="/customer">{t('qrJoinBackHome')}</Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  if (!token || role !== 'CUSTOMER') {
    return <Navigate to="/login" replace />
  }

  const submit = async () => {
    if (name.trim().length === 0 || submitting) return
    setSubmitting(true)
    try {
      const data = await SessionTableService.joinSessionViaQr(sessionId, qrToken, name.trim())
      if (data.token) setAuth({ token: data.token })
      if (data.session) setSession(data.session)
      sessionStorage.removeItem(PENDING_QR_TOKEN_KEY)
      toast.success(t('joinSuccessToast'))
      navigate('/customer/menu', { replace: true })
    } catch (error) {
      sessionStorage.removeItem(PENDING_QR_TOKEN_KEY)
      const status = isAxiosError(error) ? error.response?.status : undefined
      if (status === 404) toast.error(t('qrJoinExpiredToast'))
      else if (status === 409) toast.error(t('joinBlockedOtherTableToast'))
      else toast.error(t('qrJoinGenericErrorToast'))
      navigate('/customer', { replace: true })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-6">
      <Card className="w-full max-w-sm rounded-3xl">
        <CardHeader>
          <CardTitle className="text-2xl font-bold text-[#8c1717]">
            {t('qrJoinTitle')}
          </CardTitle>
          <p className="text-sm text-gray-500">{t('qrJoinSubtitle')}</p>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <label className="flex flex-col gap-1 text-sm font-medium text-gray-700">
            {t('qrJoinNameLabel')}
            <Input
              autoFocus
              value={name}
              maxLength={50}
              placeholder={t('qrJoinNamePlaceholder')}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && submit()}
            />
          </label>
          <Button
            className="h-12 w-full text-lg font-bold hover:bg-[#6a1111]"
            disabled={name.trim().length === 0 || submitting}
            onClick={submit}
          >
            {submitting ? t('qrJoinSubmitting') : t('qrJoinSubmit')}
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
