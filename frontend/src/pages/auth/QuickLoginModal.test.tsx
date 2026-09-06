import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QuickLoginModal } from '@/pages/auth/QuickLoginModal'
import { authService } from '@/lib/api'

vi.mock('@/lib/api', async (orig) => {
  const actual = await orig<typeof import('@/lib/api')>()
  return { ...actual, authService: { login: vi.fn(), loginPin: vi.fn() } }
})

const profile = {
  email: 'juan@x.com',
  name: 'Juan Perez',
  role: 'WAITER',
  initials: 'JP',
  colorSeed: 1,
  lastUsedAt: 1,
}

const renderModal = () =>
  render(
    <MemoryRouter>
      <QuickLoginModal profile={profile} onClose={vi.fn()} />
    </MemoryRouter>
  )

describe('QuickLoginModal', () => {
  beforeEach(() => vi.clearAllMocks())

  test('shows neither field until a mode is chosen', () => {
    renderModal()
    expect(screen.queryByLabelText('PIN')).not.toBeInTheDocument()
    expect(
      screen.queryByLabelText('Ingresa tu contraseña')
    ).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeDisabled()
  })

  test('submits the PIN to loginPin', async () => {
    vi.mocked(authService.loginPin).mockResolvedValue({
      token: 't',
      role: 'WAITER',
      name: 'Juan Perez',
    })
    renderModal()
    fireEvent.click(screen.getByRole('button', { name: 'PIN' }))
    fireEvent.change(screen.getByLabelText('PIN'), { target: { value: '1234' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    await waitFor(() =>
      expect(authService.loginPin).toHaveBeenCalledWith({
        email: 'juan@x.com',
        pin: '1234',
      })
    )
  })

  test('swaps to password on 409 PIN_NOT_SET', async () => {
    vi.mocked(authService.loginPin).mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { code: 'PIN_NOT_SET' } },
    })
    renderModal()
    fireEvent.click(screen.getByRole('button', { name: 'PIN' }))
    fireEvent.change(screen.getByLabelText('PIN'), { target: { value: '1234' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    await waitFor(() =>
      expect(
        screen.getByText('No tienes un PIN configurado. Ingresa tu contraseña.')
      ).toBeVisible()
    )
    expect(screen.getByLabelText('Ingresa tu contraseña')).toBeVisible()
  })

  test('"Contraseña" toggle reveals the password field', () => {
    renderModal()
    fireEvent.click(screen.getByRole('button', { name: 'Contraseña' }))
    expect(screen.getByLabelText('Ingresa tu contraseña')).toBeVisible()
  })

  // QA_SIMULATION_REPORT.md E-21: this dialog is reachable with no PIN/password entered yet, on
  // a device explicitly shared across staff — it must not hand out the account's real email.
  test('does not display the profile email before authentication', () => {
    renderModal()
    expect(screen.queryByText('juan@x.com')).not.toBeInTheDocument()
  })
})
