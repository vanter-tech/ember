import type { ReactNode } from 'react'
import { describe, test, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { FloatingNav } from './FloatingNav'
import { useAuthStore } from '@/store/authStore'
import { useSessionStore } from '@/store/sessionStore'

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )

describe('FloatingNav leave-table confirmation', () => {
  beforeEach(() => {
    useAuthStore.setState({ role: 'CUSTOMER', userId: 'user-1', token: 'tok' })
    useSessionStore.setState({ id: 'sess-1', participants: [{ userId: 'user-1', name: 'Ana' }] as never })
  })

  test('warns that the table will be freed when leaving as the only participant with no order', () => {
    useSessionStore.setState({ items: [] as never })
    wrap(<FloatingNav />)

    fireEvent.click(screen.getByTitle('Abandonar mesa'))

    expect(
      screen.getByText(/la mesa quedará libre al salir/i),
    ).toBeVisible()
  })

  test('does not warn when other participants remain', () => {
    useSessionStore.setState({
      participants: [
        { userId: 'user-1', name: 'Ana' },
        { userId: 'user-2', name: 'Beto' },
      ] as never,
      items: [] as never,
    })
    wrap(<FloatingNav />)

    fireEvent.click(screen.getByTitle('Abandonar mesa'))

    expect(screen.queryByText(/la mesa quedará libre al salir/i)).not.toBeInTheDocument()
  })

  test('does not warn when the sole participant has already ordered something billable', () => {
    useSessionStore.setState({
      items: [{ id: 'i1', status: 'PENDING' }] as never,
    })
    wrap(<FloatingNav />)

    fireEvent.click(screen.getByTitle('Abandonar mesa'))

    expect(screen.queryByText(/la mesa quedará libre al salir/i)).not.toBeInTheDocument()
  })
})
