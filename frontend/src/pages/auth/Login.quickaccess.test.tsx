import { describe, test, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { Login } from '@/pages/auth/Login'
import { useQuickAccessStore } from '@/store/quickAccessStore'

vi.mock('@/pages/auth/QuickLoginModal', () => ({ QuickLoginModal: () => null }))

const renderLogin = () =>
  render(
    <MemoryRouter>
      <Login />
    </MemoryRouter>
  )

describe('Login quick-access chips', () => {
  beforeEach(() => useQuickAccessStore.setState({ profiles: [] }))

  test('no chips section when store empty; form is visible', () => {
    renderLogin()
    expect(screen.queryByText('Inicio rápido')).not.toBeInTheDocument()
    expect(
      screen.getByPlaceholderText('Ingresa tu correo electrónico')
    ).toBeVisible()
  })

  test('renders a chip per stored profile and hides the form by default', () => {
    useQuickAccessStore.setState({
      profiles: [
        {
          email: 'juan@x.com',
          name: 'Juan Perez',
          role: 'WAITER',
          initials: 'JP',
          colorSeed: 10,
          lastUsedAt: 1,
        },
      ],
    })
    renderLogin()
    expect(screen.getByText('Juan Perez')).toBeVisible()
    expect(
      screen.queryByPlaceholderText('Ingresa tu correo electrónico')
    ).not.toBeVisible()
    expect(screen.getByText('Usar otra cuenta')).toBeVisible()
  })
})
