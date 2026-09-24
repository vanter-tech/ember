import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import { TicketSettings } from './TicketSettings'
import { SettingsService, ticketLogoService } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  SettingsService: { getSettings: vi.fn(), updateSettings: vi.fn() },
  ticketLogoService: { get: vi.fn(), upload: vi.fn(), remove: vi.fn() },
}))

const renderSettings = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <TicketSettings />
    </QueryClientProvider>
  )

describe('TicketSettings previews', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    URL.createObjectURL = vi.fn(() => 'blob:logo')
    URL.revokeObjectURL = vi.fn()
    vi.mocked(SettingsService.getSettings).mockResolvedValue({
      branding: { businessName: 'Ember Grill' },
      billing: { currencySymbol: '$', taxRules: [] },
      ticket: { paperWidth: 'MM_80' },
    } as never)
  })

  test('the customer receipt preview shows the logo when there is one', async () => {
    vi.mocked(ticketLogoService.get).mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
    const user = userEvent.setup()
    renderSettings()

    await user.click(await screen.findByRole('button', { name: /Recibo del cliente/ }))

    expect(await screen.findByTestId('ticket-preview-logo')).toBeVisible()
  })

  test('the customer receipt preview has no logo image when none is set', async () => {
    vi.mocked(ticketLogoService.get).mockResolvedValue(null)
    const user = userEvent.setup()
    renderSettings()

    await user.click(await screen.findByRole('button', { name: /Recibo del cliente/ }))

    await screen.findByText('Ember Grill')
    expect(screen.queryByTestId('ticket-preview-logo')).not.toBeInTheDocument()
  })

  test('the kitchen ticket preview never shows the logo', async () => {
    vi.mocked(ticketLogoService.get).mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
    const user = userEvent.setup()
    renderSettings()

    await user.click(await screen.findByRole('button', { name: /Comanda de cocina/ }))

    expect(screen.queryByTestId('ticket-preview-logo')).not.toBeInTheDocument()
  })
})
