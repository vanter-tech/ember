import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ExportSettings } from './ExportSettings'
import { exportService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    exportService: {
      downloadTenantData: vi.fn(),
    },
  }
})

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      {ui}
    </QueryClientProvider>,
  )

describe('ExportSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    URL.createObjectURL = vi.fn().mockReturnValue('blob:mock-url')
    URL.revokeObjectURL = vi.fn()
  })

  test('downloading with no dates picked sends undefined bounds', async () => {
    vi.mocked(exportService.downloadTenantData).mockResolvedValue(new Blob(['zip']))

    wrap(<ExportSettings />)
    fireEvent.click(screen.getByRole('button', { name: 'Descargar' }))

    await waitFor(() =>
      expect(exportService.downloadTenantData).toHaveBeenCalledWith(undefined, undefined),
    )
  })

  test('picking a date range sends full-day ISO bounds', async () => {
    vi.mocked(exportService.downloadTenantData).mockResolvedValue(new Blob(['zip']))

    wrap(<ExportSettings />)
    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-08-01' } })
    fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-08-14' } })
    fireEvent.click(screen.getByRole('button', { name: 'Descargar' }))

    await waitFor(() =>
      expect(exportService.downloadTenantData).toHaveBeenCalledWith(
        '2026-08-01T00:00:00',
        '2026-08-14T23:59:59',
      ),
    )
  })

  test('a failed download does not throw out of the component', async () => {
    vi.mocked(exportService.downloadTenantData).mockRejectedValue(new Error('network error'))

    wrap(<ExportSettings />)
    fireEvent.click(screen.getByRole('button', { name: 'Descargar' }))

    await waitFor(() => expect(exportService.downloadTenantData).toHaveBeenCalled())
  })
})
