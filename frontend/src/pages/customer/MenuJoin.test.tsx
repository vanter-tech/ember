import { describe, test, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { MenuJoin } from '@/pages/customer/MenuJoin'
import { useAuthStore } from '@/store/authStore'
import { useSessionStore } from '@/store/sessionStore'
import { SessionTableService } from '@/lib/api'
import { PENDING_QR_TOKEN_KEY } from '@/lib/qrToken'

// A well-formed JWT whose payload base64-encodes { "sub": "sess-42" }.
const QR_TOKEN = `h.${btoa('{"sub":"sess-42"}')}.s`

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/menu/join" element={<MenuJoin />} />
        <Route path="/login" element={<div>LOGIN PAGE</div>} />
        <Route path="/customer/menu" element={<div>MENU PAGE</div>} />
        <Route path="/customer" element={<div>ACCOUNT HOME</div>} />
      </Routes>
    </MemoryRouter>,
  )

describe('MenuJoin (QR landing)', () => {
  beforeEach(() => {
    sessionStorage.clear()
    useAuthStore.setState({ token: undefined, role: undefined, name: undefined })
    useSessionStore.setState({ id: undefined })
    vi.restoreAllMocks()
  })

  test('shows an invalid-link message when there is no token', () => {
    renderAt('/menu/join')
    expect(screen.getByText(/no es válido o ya expiró/i)).toBeInTheDocument()
  })

  test('unauthenticated: parks the token and offers sign-in or guest entry', async () => {
    renderAt(`/menu/join?token=${QR_TOKEN}`)
    expect(sessionStorage.getItem(PENDING_QR_TOKEN_KEY)).toBe(QR_TOKEN)
    expect(screen.getByRole('button', { name: /invitado/i })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /iniciar sesión/i }))
    expect(screen.getByText('LOGIN PAGE')).toBeInTheDocument()
  })

  test('unauthenticated: guest entry joins via joinAsGuest', async () => {
    const spy = vi
      .spyOn(SessionTableService, 'joinAsGuest')
      .mockResolvedValue({
        session: { id: 'sess-42' },
        token: 'scoped',
        userId: 'guest-1',
        name: 'Ana',
        role: 'CUSTOMER',
      } as never)

    renderAt(`/menu/join?token=${QR_TOKEN}`)
    await userEvent.click(screen.getByRole('button', { name: /invitado/i }))
    await userEvent.click(screen.getByRole('button', { name: /^Entrar$/ }))

    await waitFor(() =>
      expect(spy).toHaveBeenCalledWith({ qrToken: QR_TOKEN, name: undefined }),
    )
    expect(screen.getByText('MENU PAGE')).toBeInTheDocument()
    expect(sessionStorage.getItem(PENDING_QR_TOKEN_KEY)).toBeNull()
    // Without role/userId the /customer route guard bounces the guest to /login.
    expect(useAuthStore.getState()).toMatchObject({ role: 'CUSTOMER', userId: 'guest-1' })
  })

  test('authenticated: submitting the name joins via the QR token', async () => {
    useAuthStore.setState({ token: 'login-token', role: 'CUSTOMER' })
    const spy = vi
      .spyOn(SessionTableService, 'joinSessionViaQr')
      .mockResolvedValue({ session: { id: 'sess-42' }, token: 'scoped-token' } as never)

    renderAt(`/menu/join?token=${QR_TOKEN}`)
    await userEvent.type(screen.getByPlaceholderText('Ej. Ana'), 'Ana')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    await waitFor(() =>
      expect(spy).toHaveBeenCalledWith('sess-42', QR_TOKEN, 'Ana'),
    )
    expect(sessionStorage.getItem(PENDING_QR_TOKEN_KEY)).toBeNull()
  })

  test('authenticated with an account name: joins immediately, no name form shown', async () => {
    useAuthStore.setState({ token: 'login-token', role: 'CUSTOMER', name: 'Ana' })
    const spy = vi
      .spyOn(SessionTableService, 'joinSessionViaQr')
      .mockResolvedValue({ session: { id: 'sess-42' }, token: 'scoped-token' } as never)

    renderAt(`/menu/join?token=${QR_TOKEN}`)

    expect(screen.queryByPlaceholderText('Ej. Ana')).not.toBeInTheDocument()
    await waitFor(() => expect(spy).toHaveBeenCalledWith('sess-42', QR_TOKEN, 'Ana'))
    await waitFor(() => expect(screen.getByText('MENU PAGE')).toBeInTheDocument())
  })

  test('authenticated with an account name: a recoverable failure offers a retry, not an eternal spinner', async () => {
    useAuthStore.setState({ token: 'login-token', role: 'CUSTOMER', name: 'Ana' })
    const spy = vi.spyOn(SessionTableService, 'joinSessionViaQr').mockRejectedValue({
      isAxiosError: true,
      response: { status: 409 },
    })

    renderAt(`/menu/join?token=${QR_TOKEN}`)
    await waitFor(() => expect(spy).toHaveBeenCalledTimes(1))

    const retryButton = await screen.findByRole('button', { name: 'Entrar' })
    spy.mockResolvedValueOnce({ session: { id: 'sess-42' }, token: 'scoped-token' } as never)
    await userEvent.click(retryButton)

    await waitFor(() => expect(spy).toHaveBeenCalledTimes(2))
    expect(spy).toHaveBeenLastCalledWith('sess-42', QR_TOKEN, 'Ana')
  })

  test('authenticated: a recoverable join error (409) keeps the user on the join screen', async () => {
    useAuthStore.setState({ token: 'login-token', role: 'CUSTOMER' })
    vi.spyOn(SessionTableService, 'joinSessionViaQr').mockRejectedValue({
      isAxiosError: true,
      response: { status: 409 },
    })

    renderAt(`/menu/join?token=${QR_TOKEN}`)
    await userEvent.type(screen.getByPlaceholderText('Ej. Ana'), 'Ana')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    await waitFor(() => expect(SessionTableService.joinSessionViaQr).toHaveBeenCalled())
    // Stays on the join screen instead of being bounced to the account home — the pending token
    // is kept so a retry (e.g. once the table frees up) can still use it.
    expect(screen.queryByText('ACCOUNT HOME')).not.toBeInTheDocument()
    expect(sessionStorage.getItem(PENDING_QR_TOKEN_KEY)).toBe(QR_TOKEN)
  })

  test('authenticated: an expired QR (404) clears the token and goes home', async () => {
    useAuthStore.setState({ token: 'login-token', role: 'CUSTOMER' })
    vi.spyOn(SessionTableService, 'joinSessionViaQr').mockRejectedValue({
      isAxiosError: true,
      response: { status: 404 },
    })

    renderAt(`/menu/join?token=${QR_TOKEN}`)
    await userEvent.type(screen.getByPlaceholderText('Ej. Ana'), 'Ana')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    await waitFor(() => expect(screen.getByText('ACCOUNT HOME')).toBeInTheDocument())
    expect(sessionStorage.getItem(PENDING_QR_TOKEN_KEY)).toBeNull()
  })
})
