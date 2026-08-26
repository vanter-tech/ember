import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { vi, describe, test, expect, beforeEach } from 'vitest'
import { AdminOnboardingWizard } from '@/components/onboarding/AdminOnboardingWizard'
import { SettingsService } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  SettingsService: { getSettings: vi.fn(), updateSettings: vi.fn() },
}))

function renderWizard() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AdminOnboardingWizard />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('AdminOnboardingWizard', () => {
  beforeEach(() => {
    vi.mocked(SettingsService.getSettings).mockResolvedValue({
      branding: {},
      space: { totalTables: 0 },
    } as never)
    vi.mocked(SettingsService.updateSettings).mockResolvedValue(undefined)
  })

  test('walks through business name and table count, saving each via PUT /settings', async () => {
    const user = userEvent.setup()
    renderWizard()

    await screen.findByText('Bienvenido a Ember')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    await screen.findByText('Nombre de tu negocio')
    await user.type(screen.getByLabelText('Nombre Comercial'), 'Ember Grill')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    await waitFor(() =>
      expect(SettingsService.updateSettings).toHaveBeenCalledWith(
        expect.objectContaining({ branding: expect.objectContaining({ businessName: 'Ember Grill' }) })
      )
    )

    await screen.findByText('Número de mesas')
    const tablesInput = screen.getByLabelText('Cantidad Total de Mesas')
    await user.clear(tablesInput)
    await user.type(tablesInput, '8')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    await waitFor(() =>
      expect(SettingsService.updateSettings).toHaveBeenCalledWith(
        expect.objectContaining({ space: { totalTables: 8 } })
      )
    )

    await screen.findByText('¡Listo!')
  })

  test('shows an inline error and does not advance when saving fails', async () => {
    vi.mocked(SettingsService.updateSettings).mockRejectedValueOnce(new Error('network'))
    const user = userEvent.setup()
    renderWizard()

    await screen.findByText('Bienvenido a Ember')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))
    await user.type(screen.getByLabelText('Nombre Comercial'), 'Ember Grill')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    await screen.findByText('No se pudo guardar. Verifica tu conexión e intenta de nuevo.')
    expect(screen.queryByText('Número de mesas')).not.toBeInTheDocument()
  })
})
