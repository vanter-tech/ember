import type { ReactNode } from 'react'
import { describe, test, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ItemsFloatingIsland } from '@/pages/customer/components/ItemsFloatingIsland'
import { useSessionStore } from '@/store/sessionStore'
import { useAuthStore } from '@/store/authStore'

const wrap = (ui: ReactNode) => render(<MemoryRouter>{ui}</MemoryRouter>)

describe('ItemsFloatingIsland', () => {
  beforeEach(() => {
    useSessionStore.setState({ id: undefined, tableId: undefined, items: undefined })
    useAuthStore.setState({ userId: undefined })
  })

  // QA_SIMULATION_REPORT.md E-03: `items` is `undefined` on a cold session store (e.g. a
  // customer's first-ever visit to /customer/menu, no persisted session yet). The old
  // `state.items || []` selector allocated a fresh array on every render in that case, which
  // React/Zustand read as an ever-changing snapshot and looped until "Maximum update depth
  // exceeded" crashed the whole customer app (caught only by the page's ErrorBoundary). This must
  // render (as null — no items to show) instead of throwing.
  test('does not crash and renders nothing when the session store has no items yet', () => {
    expect(() => wrap(<ItemsFloatingIsland />)).not.toThrow()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  test('shows the current user\'s draft item count once items are set', () => {
    useAuthStore.setState({ userId: 'user-1' })
    useSessionStore.setState({
      tableId: 'table-1',
      items: [
        { id: 'i-1', participantId: 'user-1', name: 'Tacos', price: 10 },
        { id: 'i-2', participantId: 'user-2', name: 'Burger', price: 8 },
      ] as never,
    })

    wrap(<ItemsFloatingIsland />)

    expect(screen.getByRole('button')).toBeInTheDocument()
  })
})
