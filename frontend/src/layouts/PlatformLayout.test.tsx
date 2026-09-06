import { describe, test, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { PlatformLayout } from '@/layouts/PlatformLayout'
import { usePlatformAuthStore } from '@/store/platformAuthStore'

const wrap = (initial = '/console') =>
  render(
    <MemoryRouter initialEntries={[initial]}>
      <Routes>
        <Route path="/console" element={<PlatformLayout />}>
          <Route index element={<div>DASH</div>} />
          <Route path="restaurants" element={<div>REST</div>} />
        </Route>
        <Route path="/console/login" element={<div>LOGIN</div>} />
      </Routes>
    </MemoryRouter>
  )

describe('PlatformLayout', () => {
  beforeEach(() => {
    usePlatformAuthStore.setState({
      token: 't',
      operatorId: 'o',
      name: 'Operador Uno',
      email: 'op@ember.local',
    })
  })

  test('renders the sidebar nav and the routed content', () => {
    wrap()
    expect(screen.getByRole('link', { name: 'Dashboard' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Restaurantes' })).toBeVisible()
    expect(screen.getByText('DASH')).toBeVisible()
  })

  test('"Cerrar sesión" clears auth and navigates to login', () => {
    wrap()
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))
    expect(usePlatformAuthStore.getState().token).toBeUndefined()
    expect(screen.getByText('LOGIN')).toBeVisible()
  })
})
