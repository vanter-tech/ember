import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import { TicketLogoField } from './TicketLogoField'
import { ticketLogoService } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  ticketLogoService: { get: vi.fn(), upload: vi.fn(), remove: vi.fn() },
}))

const renderField = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <TicketLogoField />
    </QueryClientProvider>
  )

describe('TicketLogoField', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    URL.createObjectURL = vi.fn(() => 'blob:logo')
    URL.revokeObjectURL = vi.fn()
  })

  test('with no logo it shows the empty state and an upload button, no remove', async () => {
    vi.mocked(ticketLogoService.get).mockResolvedValue(null)
    renderField()

    expect(await screen.findByText('Sin logo')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Subir logo' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Quitar logo' })).not.toBeInTheDocument()
  })

  test('with a logo it shows the preview, replace and remove', async () => {
    vi.mocked(ticketLogoService.get).mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
    renderField()

    expect(await screen.findByAltText('Vista previa del logo tal como se imprime')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Cambiar logo' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Quitar logo' })).toBeVisible()
  })

  test('uploads the picked file', async () => {
    vi.mocked(ticketLogoService.get).mockResolvedValue(null)
    vi.mocked(ticketLogoService.upload).mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderField()
    await screen.findByText('Sin logo')

    const file = new File(['png'], 'logo.png', { type: 'image/png' })
    await user.upload(screen.getByTestId('ticket-logo-input'), file)

    await waitFor(() => expect(ticketLogoService.upload).toHaveBeenCalledWith(file))
  })

  test('does not upload a file over 2 MB', async () => {
    vi.mocked(ticketLogoService.get).mockResolvedValue(null)
    const user = userEvent.setup()
    renderField()
    await screen.findByText('Sin logo')

    const big = new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'big.png', { type: 'image/png' })
    await user.upload(screen.getByTestId('ticket-logo-input'), big)

    expect(ticketLogoService.upload).not.toHaveBeenCalled()
  })

  test('removes the logo', async () => {
    vi.mocked(ticketLogoService.get).mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
    vi.mocked(ticketLogoService.remove).mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderField()

    await user.click(await screen.findByRole('button', { name: 'Quitar logo' }))

    await waitFor(() => expect(ticketLogoService.remove).toHaveBeenCalled())
  })
})
