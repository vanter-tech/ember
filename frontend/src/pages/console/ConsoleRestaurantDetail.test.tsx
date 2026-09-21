import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import ConsoleRestaurantDetail from '@/pages/console/ConsoleRestaurantDetail'
import { platformRestaurantService, platformAuditLogService } from '@/lib/platformApi'

vi.mock('react-hot-toast', () => ({
  default: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn() }),
}))

vi.mock('@/lib/platformApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/platformApi')>()
  return {
    ...actual,
    platformRestaurantService: {
      ...actual.platformRestaurantService,
      getById: vi.fn(),
      deleteRestaurant: vi.fn().mockResolvedValue(undefined),
      restoreRestaurant: vi.fn().mockResolvedValue(undefined),
      updateStatus: vi.fn(),
      issueHubLicense: vi.fn(),
      updateMode: vi.fn(),
    },
    platformAuditLogService: { ...actual.platformAuditLogService, getByRestaurant: vi.fn() },
  }
})

const detail = (over: Record<string, unknown> = {}) => ({
  id: 'r-1',
  name: 'Tenant Grill',
  slug: 'tenant-grill',
  plan: 'PRO',
  status: 'SUSPENDED',
  createdAt: '2026-09-01T00:00:00Z',
  admins: [],
  hubStatus: 'ONLINE',
  deploymentMode: 'HUB',
  hubActivatedAt: '2026-09-02T00:00:00Z',
  lastHeartbeatAt: '2026-09-06T11:59:00Z',
  lastHeartbeatIp: '203.0.113.7',
  ...over,
})

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/console/restaurants/r-1']}>
        <Routes>
          <Route path="/console/restaurants/:id" element={ui} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )

describe('ConsoleRestaurantDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(platformAuditLogService.getByRestaurant).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, size: 10, number: 0,
    } as never)
  })

  test('shows the Hub panel with last heartbeat and IP', async () => {
    vi.mocked(platformRestaurantService.getById).mockResolvedValue(detail() as never)
    wrap(<ConsoleRestaurantDetail />)
    expect(await screen.findByText('203.0.113.7')).toBeVisible()
    expect(screen.getByText('ONLINE')).toBeVisible()
  })

  test('delete requires typing the slug then calls deleteRestaurant', async () => {
    vi.mocked(platformRestaurantService.getById).mockResolvedValue(detail() as never)
    wrap(<ConsoleRestaurantDetail />)
    fireEvent.click(await screen.findByRole('button', { name: 'Eliminar restaurante' }))

    const confirmBtn = screen.getByRole('button', { name: 'Confirmar eliminación' })
    expect(confirmBtn).toBeDisabled()

    fireEvent.change(screen.getByLabelText('Escribe el slug para confirmar'), {
      target: { value: 'tenant-grill' },
    })
    expect(confirmBtn).toBeEnabled()
    fireEvent.click(confirmBtn)

    await waitFor(() =>
      expect(platformRestaurantService.deleteRestaurant).toHaveBeenCalledWith('r-1')
    )
  })

  test('a failed delete surfaces the backend error instead of failing silently', async () => {
    vi.mocked(platformRestaurantService.getById).mockResolvedValue(detail() as never)
    vi.mocked(platformRestaurantService.deleteRestaurant).mockRejectedValue({
      isAxiosError: true,
      response: { status: 500, data: { detail: 'An unexpected error occurred.' } },
    })
    wrap(<ConsoleRestaurantDetail />)
    fireEvent.click(await screen.findByRole('button', { name: 'Eliminar restaurante' }))
    fireEvent.change(screen.getByLabelText('Escribe el slug para confirmar'), {
      target: { value: 'tenant-grill' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar eliminación' }))

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(
        'An unexpected error occurred.',
        expect.objectContaining({ id: 'console-delete-error' }),
      ),
    )
  })

  test('a DELETED restaurant shows Restaurar instead of the status/license controls', async () => {
    vi.mocked(platformRestaurantService.getById).mockResolvedValue(detail({ status: 'DELETED' }) as never)
    wrap(<ConsoleRestaurantDetail />)

    fireEvent.click(await screen.findByRole('button', { name: 'Restaurar restaurante' }))
    await waitFor(() =>
      expect(platformRestaurantService.restoreRestaurant).toHaveBeenCalledWith('r-1')
    )
    expect(screen.queryByRole('button', { name: 'Emitir licencia Hub' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Eliminar restaurante' })).toBeNull()
  })

  test('changing the mode needs the typed slug, shows the consequences and the plan reminder', async () => {
    vi.mocked(platformRestaurantService.getById).mockResolvedValue(detail() as never)
    vi.mocked(platformRestaurantService.updateMode).mockResolvedValue({} as never)
    wrap(<ConsoleRestaurantDetail />)

    expect(await screen.findByLabelText('Modo: Hub')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar modo' }))

    expect(screen.getByText(/su Hub queda en modo consulta/i)).toBeInTheDocument()
    expect(screen.getByText(/Plan actual:/)).toBeInTheDocument()
    const confirmBtn = screen.getByRole('button', { name: 'Confirmar cambio' })
    expect(confirmBtn).toBeDisabled()

    fireEvent.change(screen.getByLabelText('Escribe el slug para confirmar'), {
      target: { value: 'tenant-grill' },
    })
    expect(confirmBtn).toBeEnabled()
    fireEvent.click(confirmBtn)

    await waitFor(() =>
      expect(platformRestaurantService.updateMode).toHaveBeenCalledWith('r-1', 'CLOUD', 'tenant-grill')
    )
  })

  test('a Web restaurant cannot be issued a Hub license from here', async () => {
    vi.mocked(platformRestaurantService.getById).mockResolvedValue(
      detail({ deploymentMode: 'CLOUD', status: 'ACTIVE' }) as never
    )
    wrap(<ConsoleRestaurantDetail />)

    expect(await screen.findByRole('button', { name: 'Emitir licencia Hub' })).toBeDisabled()
  })
})
