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
      resetPassword: vi.fn().mockResolvedValue(undefined),
    },
  }
})

const wrap = (ui: ReactNode, qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })) =>
  render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)

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

  // QA_SIMULATION_REPORT.md E-13: `modalPayload` is a one-time snapshot taken when "Profile" was
  // clicked. Saving a PIN invalidates the `['staff']` query in the background, but the modal used
  // to keep reading that frozen snapshot — the badge stayed "Sin PIN" even after the cache (and
  // the server) already had `hasPin: true`. It must reflect the live cache, not the snapshot.
  test('reflects a fresher hasPin from the ["staff"] cache over the stale modalPayload snapshot', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    qc.setQueryData(['staff'], [member({ hasPin: true })])
    useUIStore.setState({
      activeModal: 'EDIT_STAFF',
      modalPayload: member({ hasPin: false }), // stale snapshot from before the PIN was saved
    })

    wrap(<EditStaffModal />, qc)

    expect(screen.getByText('PIN configurado')).toBeVisible()
    expect(screen.queryByText('Sin PIN')).not.toBeInTheDocument()
  })
})

describe('EditStaffModal — admin password reset section', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUIStore.setState({ activeModal: 'EDIT_STAFF', modalPayload: member() })
  })

  const fill = (pwd: string, confirm = pwd) => {
    fireEvent.change(screen.getByLabelText('Nueva contraseña'), { target: { value: pwd } })
    fireEvent.change(screen.getByLabelText('Confirmar contraseña'), { target: { value: confirm } })
  }

  test('admin sets a new password for a waiter', async () => {
    wrap(<EditStaffModal />)

    fill('Nuev0!Clave')
    fireEvent.click(screen.getByRole('button', { name: 'Restablecer contraseña' }))

    await waitFor(() => expect(staffService.resetPassword).toHaveBeenCalledWith('u-1', 'Nuev0!Clave'))
  })

  test('mismatched passwords show an error and do not call the API', () => {
    wrap(<EditStaffModal />)

    fill('Nuev0!Clave', 'Otra0!Clave')
    fireEvent.click(screen.getByRole('button', { name: 'Restablecer contraseña' }))

    expect(screen.getByText('Las contraseñas no coinciden')).toBeVisible()
    expect(staffService.resetPassword).not.toHaveBeenCalled()
  })

  test('a password that breaks the policy shows the rule and does not call the API', () => {
    wrap(<EditStaffModal />)

    fill('weakpass')
    fireEvent.click(screen.getByRole('button', { name: 'Restablecer contraseña' }))

    expect(screen.getByText(/mayúscula, una minúscula, un número y un símbolo/)).toBeVisible()
    expect(staffService.resetPassword).not.toHaveBeenCalled()
  })

  test('is not offered when editing an administrator', () => {
    useUIStore.setState({ activeModal: 'EDIT_STAFF', modalPayload: member({ role: 'ADMIN' }) })
    wrap(<EditStaffModal />)

    expect(screen.queryByRole('button', { name: 'Restablecer contraseña' })).not.toBeInTheDocument()
  })

  test('during the cooldown the button is disabled and says when it is available again', () => {
    const availableAt = new Date(Date.now() + 3 * 60 * 60 * 1000).toISOString()
    useUIStore.setState({
      activeModal: 'EDIT_STAFF',
      modalPayload: member({ passwordResetAvailableAt: availableAt }),
    })
    wrap(<EditStaffModal />)

    expect(screen.getByRole('button', { name: 'Restablecer contraseña' })).toBeDisabled()
    expect(screen.getByText(/Disponible de nuevo a las/)).toBeVisible()
  })
})

describe('EditStaffModal — saving the profile', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUIStore.setState({ activeModal: 'EDIT_STAFF', modalPayload: member() })
  })

  // Saving is two requests (profile, then role). When the second one fails the first has already been
  // applied — and a role change can be applied server-side even when its response fails — so the modal
  // must refresh the staff list from the server instead of leaving the stale row it opened with.
  test('a failed save refreshes the staff list, so the modal reflects what the server really saved', async () => {
    vi.mocked(staffService.updateProfile).mockRejectedValue(new Error('boom'))
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidate = vi.spyOn(qc, 'invalidateQueries')
    wrap(<EditStaffModal />, qc)

    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() => expect(invalidate).toHaveBeenCalledWith({ queryKey: ['staff'] }))
  })
})

describe('EditStaffModal — when the PIN was last changed', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('shows the date the PIN was last set when the account has one', () => {
    useUIStore.setState({
      activeModal: 'EDIT_STAFF',
      modalPayload: member({ hasPin: true, pinUpdatedAt: '2026-10-01T15:00:00Z' }),
    })

    wrap(<EditStaffModal />)

    expect(screen.getByText(/PIN actualizado: 01\/10\/2026/)).toBeVisible()
  })

  test('shows nothing about the date when the account has no PIN', () => {
    useUIStore.setState({ activeModal: 'EDIT_STAFF', modalPayload: member({ hasPin: false }) })

    wrap(<EditStaffModal />)

    expect(screen.queryByText(/PIN actualizado/)).not.toBeInTheDocument()
  })
})
