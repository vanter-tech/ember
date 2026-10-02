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
      listJobs: vi.fn(),
      listPrinters: vi.fn(),
    },
  }
})

const never = () => new Promise<never>(() => {})

const wrap = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <PrintingSettings />
    </QueryClientProvider>,
  )

describe('PrintingSettings while its data is loading', () => {
  beforeEach(() => vi.clearAllMocks())

  test('first load: the whole tab is blocks — header, agents card with rows and printers, jobs card with rows', () => {
    vi.mocked(printingService.listAgents).mockImplementation(never)
    vi.mocked(printingService.listJobs).mockImplementation(never)

    wrap()

    expect(screen.getByTestId('skeleton-settings-header')).toBeInTheDocument()
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
    expect(screen.getAllByTestId('skeleton-agent-row').length).toBeGreaterThanOrEqual(2)
    expect(screen.getAllByTestId('skeleton-job-row').length).toBeGreaterThanOrEqual(3)
    expect(screen.queryByText('Aún no hay agentes configurados.')).not.toBeInTheDocument()
    expect(screen.getByText('Cargando configuraciones...').closest('[role="status"]')).not.toBeNull()
  })

  test('agents loaded but jobs still loading: real agents, and the jobs card is placeholders (not an empty list)', async () => {
    vi.mocked(printingService.listAgents).mockResolvedValue([
      { id: 'a-1', name: 'Caja 1', status: 'ACTIVE', connected: true },
    ] as never)
    vi.mocked(printingService.listJobs).mockImplementation(never)
    vi.mocked(printingService.listPrinters).mockResolvedValue([])

    wrap()

    expect(await screen.findByText('Caja 1')).toBeInTheDocument()
    expect(screen.getAllByTestId('skeleton-job-row').length).toBeGreaterThanOrEqual(3)
    expect(screen.queryByText('Trabajos recientes')).not.toBeInTheDocument()
  })

  test('an agent whose printers are still loading shows a placeholder row, not "no printers"', async () => {
    vi.mocked(printingService.listAgents).mockResolvedValue([
      { id: 'a-1', name: 'Caja 1', status: 'ACTIVE', connected: true },
    ] as never)
    vi.mocked(printingService.listJobs).mockResolvedValue([])
    vi.mocked(printingService.listPrinters).mockImplementation(never)

    wrap()

    expect(await screen.findByText('Caja 1')).toBeInTheDocument()
    expect(screen.getAllByTestId('skeleton-printer-row')).toHaveLength(1)
    expect(screen.queryByText('Aún no hay impresoras en este agente.')).not.toBeInTheDocument()
  })
})
