import { describe, test, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { JoinByCode } from '@/pages/customer/JoinByCode'
import { useAuthStore } from '@/store/authStore'
import { useSessionStore } from '@/store/sessionStore'
import { SessionTableService } from '@/lib/api'

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/join']}>
      <Routes>
        <Route path="/join" element={<JoinByCode />} />
        <Route path="/customer/menu" element={<div>MENU PAGE</div>} />
      </Routes>
    </MemoryRouter>,
  )

describe('JoinByCode (/join)', () => {
  beforeEach(() => {
    useAuthStore.setState({ token: undefined, role: undefined })
    useSessionStore.setState({ id: undefined })
    vi.restoreAllMocks()
  })

  test('submit is disabled until the code is 5 characters', async () => {
    renderPage()
    const button = screen.getByRole('button', { name: /entrar/i })
    expect(button).toBeDisabled()

    await userEvent.type(screen.getByPlaceholderText('Código'), 'ab12')
    expect(button).toBeDisabled()

    await userEvent.type(screen.getByPlaceholderText('Código'), 'c')
    expect(button).toBeEnabled()
  })

  test('joins as guest with the upper-cased code and optional name', async () => {
    const spy = vi
      .spyOn(SessionTableService, 'joinAsGuest')
      .mockResolvedValue({ session: { id: 'sess-9' }, token: 'scoped' } as never)

    renderPage()
    await userEvent.type(screen.getByPlaceholderText('Código'), 'ab12c')
    await userEvent.type(screen.getByPlaceholderText('Ej. Ana'), 'Ana')
    await userEvent.click(screen.getByRole('button', { name: /entrar/i }))

    await waitFor(() =>
      expect(spy).toHaveBeenCalledWith({ joinCode: 'AB12C', name: 'Ana' }),
    )
    expect(screen.getByText('MENU PAGE')).toBeInTheDocument()
    expect(useAuthStore.getState().token).toBe('scoped')
  })

  test('sends name undefined when left blank', async () => {
    const spy = vi
      .spyOn(SessionTableService, 'joinAsGuest')
      .mockResolvedValue({ session: { id: 'sess-9' }, token: 'scoped' } as never)

    renderPage()
    await userEvent.type(screen.getByPlaceholderText('Código'), 'xyz99')
    await userEvent.click(screen.getByRole('button', { name: /entrar/i }))

    await waitFor(() =>
      expect(spy).toHaveBeenCalledWith({ joinCode: 'XYZ99', name: undefined }),
    )
  })
})
