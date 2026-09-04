import { describe, test, expect, beforeEach } from 'vitest'
import { useQuickAccessStore } from '@/store/quickAccessStore'

const reset = () => useQuickAccessStore.setState({ profiles: [], pinDismissed: [] })

describe('quickAccessStore', () => {
  beforeEach(reset)

  test('remember adds a profile with derived initials and lastUsedAt', () => {
    useQuickAccessStore.getState().remember({ email: 'juan.perez@x.com', name: 'Juan Perez', role: 'WAITER' })
    const [p] = useQuickAccessStore.getState().profiles
    expect(p.email).toBe('juan.perez@x.com')
    expect(p.initials).toBe('JP')
    expect(p.lastUsedAt).toBeGreaterThan(0)
  })

  test('remember upserts by email (case-insensitive), not duplicate', () => {
    const s = useQuickAccessStore.getState()
    s.remember({ email: 'a@x.com', name: 'A', role: 'WAITER' })
    s.remember({ email: 'A@X.com', name: 'A Updated', role: 'ADMIN' })
    const list = useQuickAccessStore.getState().profiles
    expect(list).toHaveLength(1)
    expect(list[0].name).toBe('A Updated')
    expect(list[0].role).toBe('ADMIN')
  })

  test('caps at 6, evicting the oldest lastUsedAt', () => {
    const s = useQuickAccessStore.getState()
    for (let i = 0; i < 7; i++) {
      s.remember({ email: `u${i}@x.com`, name: `U ${i}`, role: 'WAITER' })
      // force distinct timestamps
      useQuickAccessStore.setState({
        profiles: useQuickAccessStore.getState().profiles.map((p) =>
          p.email === `u${i}@x.com` ? { ...p, lastUsedAt: 1000 + i } : p),
      })
    }
    const list = useQuickAccessStore.getState().profiles
    expect(list).toHaveLength(6)
    expect(list.find((p) => p.email === 'u0@x.com')).toBeUndefined()
  })

  test('forget removes by email; clear empties', () => {
    const s = useQuickAccessStore.getState()
    s.remember({ email: 'a@x.com', name: 'A', role: 'WAITER' })
    s.remember({ email: 'b@x.com', name: 'B', role: 'WAITER' })
    s.forget('a@x.com')
    expect(useQuickAccessStore.getState().profiles.map((p) => p.email)).toEqual(['b@x.com'])
    s.clear()
    expect(useQuickAccessStore.getState().profiles).toHaveLength(0)
  })

  test('dismissPinPrompt records the email once', () => {
    const s = useQuickAccessStore.getState()
    s.dismissPinPrompt('a@x.com')
    s.dismissPinPrompt('a@x.com')
    expect(useQuickAccessStore.getState().pinDismissed).toEqual(['a@x.com'])
  })
})
