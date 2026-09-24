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

  test('the kitchen ticket preview shows the logo too', async () => {
    vi.mocked(ticketLogoService.get).mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
    const user = userEvent.setup()
    renderSettings()

    await user.click(await screen.findByRole('button', { name: /Comanda de cocina/ }))

    expect(await screen.findByTestId('ticket-preview-logo')).toBeVisible()
  })
})

describe('TicketSettings business info in the receipt preview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    URL.createObjectURL = vi.fn(() => 'blob:logo')
    URL.revokeObjectURL = vi.fn()
    vi.mocked(ticketLogoService.get).mockResolvedValue(null)
    vi.mocked(SettingsService.getSettings).mockResolvedValue({
      branding: {
        businessName: 'Ember Grill',
        ruc: 'J0310000000001',
        address: 'Calle Principal 123',
        phone: '2222-3333',
        openingTime: '12:00',
        closingTime: '23:00',
      },
      billing: { currencySymbol: '$', taxRules: [] },
      ticket: { paperWidth: 'MM_80' },
    } as never)
  })

  test('shows the opening hours by default, and RUC/address/phone only when switched on', async () => {
    const user = userEvent.setup()
    renderSettings()

    await user.click(await screen.findByRole('button', { name: /Recibo del cliente/ }))
    expect(await screen.findByText('Horario: 12:00 - 23:00')).toBeVisible()
    expect(screen.queryByText('RUC: J0310000000001')).not.toBeInTheDocument()
  })

  test('switching the info toggle on adds RUC, address and phone to the preview', async () => {
    const user = userEvent.setup()
    renderSettings()

    await user.click(await screen.findByLabelText('Mostrar RUC, dirección y teléfono'))
    await user.click(screen.getByRole('button', { name: /Recibo del cliente/ }))

    expect(await screen.findByText('RUC: J0310000000001')).toBeVisible()
    expect(screen.getByText('Calle Principal 123')).toBeVisible()
    expect(screen.getByText('Tel: 2222-3333')).toBeVisible()
  })

  test('switching the hours toggle off removes them from the preview', async () => {
    const user = userEvent.setup()
    renderSettings()

    await user.click(await screen.findByLabelText('Mostrar horario de atención'))
    await user.click(screen.getByRole('button', { name: /Recibo del cliente/ }))

    await screen.findByText('Ember Grill')
    expect(screen.queryByText('Horario: 12:00 - 23:00')).not.toBeInTheDocument()
  })
})

describe('TicketSettings preview: tax, hours and the tip line', () => {
  const withSettings = (overrides: Record<string, unknown> = {}) => {
    vi.clearAllMocks()
    URL.createObjectURL = vi.fn(() => 'blob:logo')
    URL.revokeObjectURL = vi.fn()
    vi.mocked(ticketLogoService.get).mockResolvedValue(null)
    vi.mocked(SettingsService.getSettings).mockResolvedValue({
      branding: { businessName: 'Ember Grill' },
      billing: { currencySymbol: '$', taxRate: 0, taxRules: [] },
      ticket: { paperWidth: 'MM_80', footerMessage: 'Gracias por visitarnos' },
      ...overrides,
    } as never)
  }
  const openCustomerPreview = async () => {
    const user = userEvent.setup()
    renderSettings()
    await user.click(await screen.findByRole('button', { name: /Recibo del cliente/ }))
    return user
  }

  test('with nothing configured, the active options say what is missing instead of vanishing', async () => {
    withSettings()
    await openCustomerPreview()

    expect(await screen.findByText('Horario: sin configurar')).toBeVisible()
    expect(screen.getByText('Impuesto: sin configurar')).toBeVisible()
  })

  test('a configured tax rate shows the tax row like the printed ticket does', async () => {
    withSettings({ billing: { currencySymbol: '$', taxRate: 15, taxRules: [] } })
    await openCustomerPreview()

    expect(await screen.findByText(/Impuesto \(15%\)/)).toBeVisible()
    expect(screen.queryByText('Impuesto: sin configurar')).not.toBeInTheDocument()
  })

  test('the tip option ends the receipt with a PROPINA line to write on', async () => {
    withSettings()
    await openCustomerPreview()

    const tipLine = await screen.findByTestId('ticket-preview-tip-line')
    expect(tipLine).toHaveTextContent('PROPINA:')
    // the footer message stays the very last thing on the receipt
    const footer = screen.getByText('Gracias por visitarnos')
    expect(tipLine.compareDocumentPosition(footer) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(screen.queryByText(/Propina sugerida/)).not.toBeInTheDocument()
  })

  test('switching the tip option off removes the PROPINA line', async () => {
    withSettings()
    const user = userEvent.setup()
    renderSettings()

    await user.click(await screen.findByLabelText('Línea de propina'))
    await user.click(screen.getByRole('button', { name: /Recibo del cliente/ }))

    await screen.findByText('Ember Grill')
    expect(screen.queryByTestId('ticket-preview-tip-line')).not.toBeInTheDocument()
  })
})
