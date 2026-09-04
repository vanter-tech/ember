import { describe, test, expect, vi, beforeEach } from 'vitest'
import { navigateForRole } from '@/pages/auth/navigateForRole'
import { useSessionStore } from '@/store/sessionStore'

const tAuth = (k: string) => k

describe('navigateForRole', () => {
  beforeEach(() => {
    useSessionStore.setState({ id: undefined } as never)
  })

  test('ADMIN → /admin', async () => {
    const navigate = vi.fn()
    await navigateForRole({ role: 'ADMIN' } as never, navigate, { tAuth })
    expect(navigate).toHaveBeenCalledWith('/admin', { replace: true })
  })

  test('WAITER → /waiter', async () => {
    const navigate = vi.fn()
    await navigateForRole({ role: 'WAITER' } as never, navigate, { tAuth })
    expect(navigate).toHaveBeenCalledWith('/waiter', { replace: true })
  })

  test('KITCHEN → /kitchen', async () => {
    const navigate = vi.fn()
    await navigateForRole({ role: 'KITCHEN' } as never, navigate, { tAuth })
    expect(navigate).toHaveBeenCalledWith('/kitchen', { replace: true })
  })

  test('CUSTOMER with no open session → /customer/home', async () => {
    const navigate = vi.fn()
    await navigateForRole({ role: 'CUSTOMER' } as never, navigate, { tAuth })
    expect(navigate).toHaveBeenCalledWith('/customer/home', { replace: true })
  })
})
