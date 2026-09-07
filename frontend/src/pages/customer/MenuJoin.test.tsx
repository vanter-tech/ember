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
      </Routes>
    </MemoryRouter>,
  )

describe('MenuJoin (QR landing)', () => {
  beforeEach(() => {
    sessionStorage.clear()
    useAuthStore.setState({ token: undefined, role: undefined })
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
      .mockResolvedValue({ session: { id: 'sess-42' }, token: 'scoped' } as never)

    renderAt(`/menu/join?token=${QR_TOKEN}`)
    await userEvent.click(screen.getByRole('button', { name: /invitado/i }))
    await userEvent.click(screen.getByRole('button', { name: /^Entrar$/ }))

    await waitFor(() =>
      expect(spy).toHaveBeenCalledWith({ qrToken: QR_TOKEN, name: undefined }),
    )
    expect(screen.getByText('MENU PAGE')).toBeInTheDocument()
    expect(sessionStorage.getItem(PENDING_QR_TOKEN_KEY)).toBeNull()
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
})
