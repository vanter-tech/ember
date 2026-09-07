import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
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
})
