import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { vi, describe, test, expect } from 'vitest'
import { AdminLayout } from '@/layouts/AdminLayout'
import { SettingsService } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  SettingsService: { getSettings: vi.fn(), updateSettings: vi.fn() },
  cashShiftService: {
    current: vi.fn().mockResolvedValue(null),
    detail: vi.fn(),
    prolong: vi.fn(),
  },
}))

function renderAdminLayout() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/admin/settings']}>
        <Routes>
          <Route path="/admin" element={<AdminLayout />}>
            <Route path="settings" element={<div>Real settings page</div>} />
            <Route path="analytics" element={<div>Analytics page</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('AdminLayout onboarding gate', () => {
  test('shows the wizard instead of the route when onboarding is incomplete', async () => {
    vi.mocked(SettingsService.getSettings).mockResolvedValue({
      branding: {},
      space: { totalTables: 0 },
    } as never)

    renderAdminLayout()

    await screen.findByText('Bienvenido a Ember')
    expect(screen.queryByText('Real settings page')).not.toBeInTheDocument()
  })

  test('keeps the wizard mounted through the tables step when the backend defaults totalTables to 10, then lands on analytics', async () => {
    vi.mocked(SettingsService.getSettings).mockResolvedValue({
      branding: {},
      space: { totalTables: 10 },
    } as never)
    vi.mocked(SettingsService.updateSettings).mockImplementation(async (payload) => {
      vi.mocked(SettingsService.getSettings).mockResolvedValue(payload as never)
    })
    const user = userEvent.setup()

    renderAdminLayout()

    await screen.findByText('Bienvenido a Ember')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))
    await user.type(screen.getByLabelText('Nombre Comercial'), 'Ember Grill')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    await screen.findByText('Número de mesas')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    await screen.findByText('¡Listo!')
    await user.click(screen.getByRole('button', { name: 'Ir al panel' }))

    await screen.findByText('Analytics page')
  })

  test('shows the normal route when onboarding is already complete', async () => {
    vi.mocked(SettingsService.getSettings).mockResolvedValue({
      branding: { businessName: 'Ember Grill' },
      space: { totalTables: 5 },
    } as never)

    renderAdminLayout()

    await screen.findByText('Real settings page')
    expect(screen.queryByText('Bienvenido a Ember')).not.toBeInTheDocument()
  })

  test('does not force the wizard when the settings fetch fails', async () => {
    vi.mocked(SettingsService.getSettings).mockRejectedValue(new Error('network'))

    renderAdminLayout()

    await screen.findByText('Real settings page')
    expect(screen.queryByText('Bienvenido a Ember')).not.toBeInTheDocument()
  })
})
