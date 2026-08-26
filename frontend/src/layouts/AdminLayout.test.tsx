import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { vi, describe, test, expect } from 'vitest'
import { AdminLayout } from '@/layouts/AdminLayout'
import { SettingsService } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  SettingsService: { getSettings: vi.fn(), updateSettings: vi.fn() },
}))

function renderAdminLayout() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/admin/settings']}>
        <Routes>
          <Route path="/admin" element={<AdminLayout />}>
            <Route path="settings" element={<div>Real settings page</div>} />
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
