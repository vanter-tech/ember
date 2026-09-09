import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { PrintingSettings } from './PrintingSettings'
import { printingService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    printingService: {
      ...actual.printingService,
      listAgents: vi.fn(),
      listJobs: vi.fn().mockResolvedValue([]),
      listPrinters: vi.fn().mockResolvedValue([]),
      createPairingCode: vi.fn(),
      cancelJob: vi.fn().mockResolvedValue(undefined),
      cancelPendingJobs: vi.fn().mockResolvedValue(1),
    },
  }
})

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      {ui}
    </QueryClientProvider>,
  )

describe('PrintingSettings', () => {
  beforeEach(() => vi.clearAllMocks())

  test('hides revoked agents and shows a delete button for the rest', async () => {
    vi.mocked(printingService.listAgents).mockResolvedValue([
      { id: 'a-1', name: 'Caja 1', status: 'ACTIVE', connected: true },
      { id: 'a-2', name: 'Agente viejo', status: 'REVOKED', connected: false },
    ] as never)

    wrap(<PrintingSettings />)

    expect(await screen.findByText('Caja 1')).toBeInTheDocument()
    expect(screen.queryByText('Agente viejo')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Eliminar agente' })).toBeInTheDocument()
  })

  test('shows the paired / not-paired badge and the installer download link', async () => {
    vi.mocked(printingService.listAgents).mockResolvedValue([
      { id: 'a-1', name: 'Caja 1', status: 'ACTIVE', connected: true, paired: false },
      { id: 'a-2', name: 'Caja 2', status: 'ACTIVE', connected: true, paired: true },
    ] as never)

    wrap(<PrintingSettings />)

    expect(await screen.findByText('Sin emparejar')).toBeInTheDocument()
    expect(screen.getByText('Emparejado')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Descargar Ember Agent (.exe)' })).toBeInTheDocument()
  })

  test('"Nuevo código" fetches a pairing code and shows it', async () => {
    vi.mocked(printingService.listAgents).mockResolvedValue([
      { id: 'a-1', name: 'Caja 1', status: 'ACTIVE', connected: true, paired: false },
    ] as never)
    vi.mocked(printingService.createPairingCode).mockResolvedValue({
      code: 'ABCDEFGHJK',
      expiresAt: '2026-09-08T12:00:00Z',
    } as never)

    wrap(<PrintingSettings />)

    fireEvent.click(await screen.findByRole('button', { name: 'Nuevo código' }))

    expect(await screen.findByText('ABCDEFGHJK')).toBeInTheDocument()
    expect(printingService.createPairingCode).toHaveBeenCalledWith('a-1')
  })

  test('cancels a pending job and clears all pending jobs', async () => {
    vi.mocked(printingService.listAgents).mockResolvedValue([] as never)
    vi.mocked(printingService.listJobs).mockResolvedValue([
      { id: 'j-1', role: 'KITCHEN', status: 'PENDING' },
    ] as never)

    wrap(<PrintingSettings />)

    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar' }))
    await waitFor(() => expect(printingService.cancelJob).toHaveBeenCalledWith('j-1'))

    fireEvent.click(screen.getByRole('button', { name: 'Limpiar pendientes' }))
    await waitFor(() => expect(printingService.cancelPendingJobs).toHaveBeenCalled())
  })
})
