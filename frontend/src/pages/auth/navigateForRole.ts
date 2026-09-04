import type { NavigateFunction } from 'react-router-dom'
import toast from 'react-hot-toast'
import type { LoginResponse } from '@/lib/api'
import { SessionTableService } from '@/lib/api'
import { useAuthStore } from '../../store/authStore'
import { useSessionStore } from '@/store/sessionStore'
import type { useTranslation } from '@/lib/i18n'

type TAuth = ReturnType<typeof useTranslation<'auth'>>['t']

export async function navigateForRole(
  response: LoginResponse,
  navigate: NavigateFunction,
  { tAuth }: { tAuth: TAuth },
): Promise<void> {
  const { setAuth } = useAuthStore.getState()
  switch (response.role) {
    case 'ADMIN':
      navigate('/admin', { replace: true })
      break
    case 'CUSTOMER': {
      // A customer's login token is tenant-less. If they left a table open (the session id
      // survives logout in its own persisted store), re-attach to it and swap in a token
      // re-scoped to that restaurant instead of bouncing them to the home screen.
      const openSessionId = useSessionStore.getState().id
      if (openSessionId) {
        try {
          const resumed = await SessionTableService.resumeSession(openSessionId)
          if (resumed.token) setAuth({ token: resumed.token })
          if (resumed.session) useSessionStore.getState().setSession(resumed.session)
          toast.success(tAuth('sessionResumedToast'))
          navigate('/customer/menu', { replace: true })
          break
        } catch {
          useSessionStore.getState().clearSession()
        }
      }
      navigate('/customer/home', { replace: true })
      break
    }
    case 'WAITER':
      navigate('/waiter', { replace: true })
      break
    case 'KITCHEN':
      navigate('/kitchen', { replace: true })
      break
    default:
      break
  }
}
