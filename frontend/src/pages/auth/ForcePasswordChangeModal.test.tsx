import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { ForcePasswordChangeModal } from '@/pages/auth/ForcePasswordChangeModal'
import { userProfileService } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/lib/api', async (orig) => {
  const actual = await orig<typeof import('@/lib/api')>()
  return { ...actual, userProfileService: { ...actual.userProfileService, changePassword: vi.fn() } }
})

const fillAndSubmit = (current: string, next: string, confirm: string) => {
  fireEvent.change(screen.getByLabelText('Contraseña temporal'), { target: { value: current } })
  fireEvent.change(screen.getByLabelText('Nueva contraseña'), { target: { value: next } })
  fireEvent.change(screen.getByLabelText('Confirma la nueva contraseña'), {
    target: { value: confirm },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Guardar y continuar' }))
}

describe('ForcePasswordChangeModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({
      token: 'temp-token',
      userId: 'user-1',
      restaurantId: 'r-1',
      name: 'Ana',
      role: 'ADMIN',
      mustChangePassword: true,
    })
  })

  test('submit is disabled until all three fields are filled', () => {
    render(<ForcePasswordChangeModal />)
    expect(screen.getByRole('button', { name: 'Guardar y continuar' })).toBeDisabled()
  })

  test('shows a mismatch error and never calls the API when confirmation differs', async () => {
    render(<ForcePasswordChangeModal />)
    fillAndSubmit('temp-pass', 'NewSecret1!', 'Different1!')

    expect(await screen.findByText('Las contraseñas no coinciden')).toBeVisible()
    expect(userProfileService.changePassword).not.toHaveBeenCalled()
  })

  test('on success, updates the auth store and clears mustChangePassword', async () => {
    vi.mocked(userProfileService.changePassword).mockResolvedValue({
      token: 'fresh-token',
      role: 'ADMIN',
      mustChangePassword: false,
    })
    render(<ForcePasswordChangeModal />)

    fillAndSubmit('temp-pass', 'NewSecret1!', 'NewSecret1!')

    await waitFor(() =>
      expect(userProfileService.changePassword).toHaveBeenCalledWith({
        currentPassword: 'temp-pass',
        newPassword: 'NewSecret1!',
      })
    )
    await waitFor(() => expect(useAuthStore.getState().mustChangePassword).toBe(false))
    expect(useAuthStore.getState().token).toBe('fresh-token')
  })

  test('shows the server error message when the current password is wrong', async () => {
    vi.mocked(userProfileService.changePassword).mockRejectedValue({
      isAxiosError: true,
      response: { data: { detail: 'Invalid credentials' } },
    })
    render(<ForcePasswordChangeModal />)

    fillAndSubmit('wrong', 'NewSecret1!', 'NewSecret1!')

    expect(await screen.findByText('Invalid credentials')).toBeVisible()
  })
})
