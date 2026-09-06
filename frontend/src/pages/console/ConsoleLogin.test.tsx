import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import ConsoleLogin from '@/pages/console/ConsoleLogin'
import { platformAuthService } from '@/lib/platformApi'

vi.mock('@/lib/platformApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/platformApi')>()
  return {
    ...actual,
    platformAuthService: { ...actual.platformAuthService, login: vi.fn() },
  }
})

const wrap = (ui: ReactNode) => render(<MemoryRouter>{ui}</MemoryRouter>)

describe('ConsoleLogin', () => {
  beforeEach(() => vi.clearAllMocks())

  test('shows the Spanish CTA and submits credentials', async () => {
    vi.mocked(platformAuthService.login).mockResolvedValue({ token: 't', name: 'Op' })
    wrap(<ConsoleLogin />)

    expect(screen.getByRole('button', { name: 'Iniciar sesión' })).toBeVisible()
    fireEvent.change(screen.getByPlaceholderText('Ingresá tu email'), {
      target: { value: 'op@ember.local' },
    })
    fireEvent.change(screen.getByPlaceholderText('Ingresá tu contraseña'), {
      target: { value: 'x' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    await waitFor(() => expect(platformAuthService.login).toHaveBeenCalled())
  })
})
