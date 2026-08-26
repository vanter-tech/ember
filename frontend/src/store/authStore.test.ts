import { describe, test, expect, beforeEach } from 'vitest'
import { useAuthStore } from '@/store/authStore'
import { queryClient } from '@/queryClient'

describe('authStore session-boundary cache clearing', () => {
  beforeEach(() => {
    useAuthStore.setState({
      token: undefined,
      userId: undefined,
      restaurantId: undefined,
      name: undefined,
      role: undefined,
    })
    queryClient.clear()
  })

  test('setAuth clears the query cache so a new identity never sees a previous tenant\'s stale data', () => {
    queryClient.setQueryData(['restaurantSettings'], {
      branding: { businessName: 'Old Fully-Onboarded Restaurant' },
      space: { totalTables: 20 },
    })

    useAuthStore.getState().setAuth({
      token: 'new-token',
      userId: 'user-2',
      restaurantId: 'restaurant-2',
      name: 'New Admin',
      role: 'ADMIN',
    })

    expect(queryClient.getQueryData(['restaurantSettings'])).toBeUndefined()
  })

  test('logout clears the query cache', () => {
    queryClient.setQueryData(['restaurantSettings'], {
      branding: { businessName: 'Some Restaurant' },
      space: { totalTables: 5 },
    })

    useAuthStore.getState().logout()

    expect(queryClient.getQueryData(['restaurantSettings'])).toBeUndefined()
  })
})
