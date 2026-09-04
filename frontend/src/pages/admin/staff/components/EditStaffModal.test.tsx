import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { EditStaffModal } from '@/pages/admin/staff/components/EditStaffModal'
import { useUIStore } from '@/store/uiStore'
import { staffService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    staffService: {
      ...actual.staffService,
      updateProfile: vi.fn(),
      updateRole: vi.fn(),
      setPin: vi.fn().mockResolvedValue(undefined),
      clearPin: vi.fn().mockResolvedValue(undefined),
    },
  }
})

const wrap = (ui: ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

const member = (over: Record<string, unknown> = {}) => ({
  id: 'u-1',
  name: 'Ana',
  email: 'ana@x.com',
  role: 'WAITER',
  active: true,
  hasPin: false,
  ...over,
})

describe('EditStaffModal — quick-login PIN section', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUIStore.setState({ activeModal: 'EDIT_STAFF', modalPayload: member() })
  })

  test('admin sets a PIN for the account', async () => {
    wrap(<EditStaffModal />)
    expect(screen.getByText('Sin PIN')).toBeVisible()

    fireEvent.change(screen.getByLabelText('Nuevo PIN (4-6 dígitos)'), {
      target: { value: '1234' },
    })
    fireEvent.change(screen.getByLabelText('Confirmar PIN'), {
      target: { value: '1234' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Agregar PIN' }))

    await waitFor(() =>
      expect(staffService.setPin).toHaveBeenCalledWith('u-1', '1234'),
    )
  })

  test('mismatched PINs show an error and do not call the API', () => {
    wrap(<EditStaffModal />)
    fireEvent.change(screen.getByLabelText('Nuevo PIN (4-6 dígitos)'), {
      target: { value: '1234' },
    })
    fireEvent.change(screen.getByLabelText('Confirmar PIN'), {
      target: { value: '9999' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Agregar PIN' }))

    expect(screen.getByText('Los PIN no coinciden')).toBeVisible()
    expect(staffService.setPin).not.toHaveBeenCalled()
  })

  test('an account that already has a PIN can have it removed', async () => {
    useUIStore.setState({
      activeModal: 'EDIT_STAFF',
      modalPayload: member({ hasPin: true }),
    })
    wrap(<EditStaffModal />)
    expect(screen.getByText('PIN configurado')).toBeVisible()

    fireEvent.click(screen.getByRole('button', { name: 'Quitar PIN' }))

    await waitFor(() =>
      expect(staffService.clearPin).toHaveBeenCalledWith('u-1'),
    )
  })
})
