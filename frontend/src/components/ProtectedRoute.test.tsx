import { describe, test, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { ProtectedRoute } from './ProtectedRoute'
import { useAuthStore } from '@/store/authStore'

const renderProtected = (allowedRoles: string[]) =>
  render(
    <MemoryRouter initialEntries={['/admin']}>
      <Routes>
        <Route path="/login" element={<div>Login page</div>} />
        <Route element={<ProtectedRoute allowedRoles={allowedRoles} />}>
          <Route path="/admin" element={<div>Admin content</div>} />
        </Route>
      </Routes>
    </MemoryRouter>
  )

describe('ProtectedRoute', () => {
  beforeEach(() => {
    useAuthStore.setState({ token: undefined, role: undefined })
  })

  // AUDIT_BLUEPRINT.md F-23 / S8-01: a token present but role undefined (e.g. a partially
  // rehydrated store) used to fall through to <Outlet/> instead of being treated as unauthenticated.
  test('redirects to /login when token is present but role is undefined', () => {
    useAuthStore.setState({ token: 'a-token', role: undefined })

    renderProtected(['ADMIN'])

    expect(screen.getByText('Login page')).toBeInTheDocument()
  })

  test('redirects to /login when there is no token', () => {
    renderProtected(['ADMIN'])

    expect(screen.getByText('Login page')).toBeInTheDocument()
  })

  test('shows a 403 message when role does not match allowedRoles', () => {
    useAuthStore.setState({ token: 'a-token', role: 'CUSTOMER' })

    renderProtected(['ADMIN'])

    expect(screen.getByText('403')).toBeInTheDocument()
  })

  test('renders the outlet when role matches allowedRoles', () => {
    useAuthStore.setState({ token: 'a-token', role: 'ADMIN' })

    renderProtected(['ADMIN'])

    expect(screen.getByText('Admin content')).toBeInTheDocument()
  })
})
