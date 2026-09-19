import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams, Link } from 'react-router-dom'
import { isAxiosError } from 'axios'
import toast from 'react-hot-toast'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/store/authStore'
import { useSessionStore } from '@/store/sessionStore'
import { SessionTableService, type LoginResponse } from '@/lib/api'
import { PENDING_QR_TOKEN_KEY, sessionIdFromQrToken } from '@/lib/qrToken'
import { useTranslation } from '@/lib/i18n'

/**
 * Landing page for the table QR (`/menu/join?token=…`). The QR is scanned with the phone's own
 * camera, so the visitor arrives here with no session — possibly not even logged in.
 *
 * - Not authenticated as CUSTOMER → park the token and bounce to /login; navigateForRole brings
 *   them back here once they're in.
 * - Authenticated as CUSTOMER → join immediately using the account's own name (no name-entry
 *   screen — that's guest-only territory), via POST /sessions/{id}/join with the token, swapping
 *   in the tenant-scoped token it returns, exactly like the 5-digit code flow.
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

  const accountName = useAuthStore((s) => s.name)
  const [name, setName] = useState(accountName ?? '')
  const [submitting, setSubmitting] = useState(false)
  const [guestMode, setGuestMode] = useState(false)
  const [joinFailed, setJoinFailed] = useState(false)

  const isAuthenticatedCustomer = !!token && role === 'CUSTOMER'

  const finishJoin = (data: { token?: string; session?: unknown } & Partial<LoginResponse>) => {
    // Guests get the full identity back (they never logged in); an account holder's response has
    // only the re-scoped token, so keep their stored identity and swap the token alone.
    const { session, ...auth } = data
    if (data.token) setAuth(data.role ? auth : { token: data.token })
    if (session) setSession(session as never)
    sessionStorage.removeItem(PENDING_QR_TOKEN_KEY)
    toast.success(t('joinSuccessToast'))
    navigate('/customer/menu', { replace: true })
  }

  const reportJoinError = (error: unknown) => {
    const status = isAxiosError(error) ? error.response?.status : undefined
    if (status === 404) toast.error(t('qrJoinExpiredToast'))
    else if (status === 409) toast.error(t('joinBlockedOtherTableToast'))
    else toast.error(t('qrJoinGenericErrorToast'))
  }

  const submit = async (submittedName: string) => {
    if (!sessionId || !qrToken || submittedName.trim().length === 0 || submitting) return
    setSubmitting(true)
    setJoinFailed(false)
    try {
      finishJoin(await SessionTableService.joinSessionViaQr(sessionId, qrToken, submittedName.trim()))
    } catch (error) {
      reportJoinError(error)
      // Only a genuinely dead QR (404) has nothing left to retry — anything else (already seated
      // elsewhere, full table, a transient network blip) should keep the user on this screen so
      // they can just try again, instead of silently dropping the join and bouncing them to the
      // account home with no menu and no explanation.
      if (isAxiosError(error) && error.response?.status === 404) {
        sessionStorage.removeItem(PENDING_QR_TOKEN_KEY)
        navigate('/customer', { replace: true })
      } else {
        setJoinFailed(true)
      }
    } finally {
      setSubmitting(false)
    }
  }

  useEffect(() => {
    if (qrToken) sessionStorage.setItem(PENDING_QR_TOKEN_KEY, qrToken)
  }, [qrToken])

  useEffect(() => {
    // A real account already has a name — join immediately instead of asking, matching the bug
    // report's expectation that the name-entry screen only ever appears for a guest.
    if (isAuthenticatedCustomer && accountName) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- kicks off the one-time auto-join on mount
      submit(accountName)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticatedCustomer, accountName])

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

  if (isAuthenticatedCustomer && accountName) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 p-6">
        <Card className="w-full max-w-sm rounded-3xl">
          <CardContent className="flex flex-col items-center gap-4 py-10 text-center">
            {joinFailed ? (
              <>
                <p className="text-gray-500">{t('qrJoinGenericErrorToast')}</p>
                <Button
                  className="h-12 w-full text-lg font-bold hover:bg-[#6a1111]"
                  disabled={submitting}
                  onClick={() => submit(accountName)}
                >
                  {submitting ? t('qrJoinSubmitting') : t('qrJoinSubmit')}
                </Button>
              </>
            ) : (
              <p className="text-gray-500">{t('qrJoinSubmitting')}</p>
            )}
          </CardContent>
        </Card>
      </div>
    )
  }

  if (!isAuthenticatedCustomer) {
    const guestSubmit = async () => {
      if (submitting) return
      setSubmitting(true)
      try {
        finishJoin(
          await SessionTableService.joinAsGuest({ qrToken, name: name.trim() || undefined }),
        )
      } catch (error) {
        reportJoinError(error)
      } finally {
        setSubmitting(false)
      }
    }

    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 p-6">
        <Card className="w-full max-w-sm rounded-3xl">
          <CardHeader>
            <CardTitle className="text-2xl font-bold text-[#8c1717]">{t('qrJoinTitle')}</CardTitle>
            <p className="text-sm text-gray-500">
              {guestMode ? t('qrJoinGuestNameLabel') : t('qrJoinChoiceSubtitle')}
            </p>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {guestMode ? (
              <>
                <Input
                  autoFocus
                  value={name}
                  maxLength={50}
                  placeholder={t('qrJoinNamePlaceholder')}
                  onChange={(e) => setName(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && guestSubmit()}
                />
                <Button
                  className="h-12 w-full text-lg font-bold hover:bg-[#6a1111]"
                  disabled={submitting}
                  onClick={guestSubmit}
                >
                  {submitting ? t('qrJoinSubmitting') : t('qrJoinGuestSubmit')}
                </Button>
                <Button variant="ghost" className="w-full" onClick={() => setGuestMode(false)}>
                  {t('joinModalBackButton')}
                </Button>
              </>
            ) : (
              <>
                <Button
                  className="h-12 w-full text-lg font-bold hover:bg-[#6a1111]"
                  onClick={() => navigate('/login', { replace: true })}
                >
                  {t('qrJoinSignInCta')}
                </Button>
                <Button
                  variant="outline"
                  className="h-12 w-full text-lg font-bold"
                  onClick={() => setGuestMode(true)}
                >
                  {t('qrJoinGuestCta')}
                </Button>
              </>
            )}
          </CardContent>
        </Card>
      </div>
    )
  }

  // Rare edge case: authenticated as CUSTOMER but the account has no name on file — the only
  // remaining path that still needs to ask, since the backend requires a non-blank userName.
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
              onKeyDown={(e) => e.key === 'Enter' && submit(name)}
            />
          </label>
          <Button
            className="h-12 w-full text-lg font-bold hover:bg-[#6a1111]"
            disabled={name.trim().length === 0 || submitting}
            onClick={() => submit(name)}
          >
            {submitting ? t('qrJoinSubmitting') : t('qrJoinSubmit')}
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
