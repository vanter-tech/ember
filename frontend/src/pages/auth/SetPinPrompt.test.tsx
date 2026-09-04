import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { SetPinPrompt } from '@/pages/auth/SetPinPrompt'
import { authService } from '@/lib/api'

vi.mock('@/lib/api', async (orig) => {
  const actual = await orig<typeof import('@/lib/api')>()
  return { ...actual, authService: { setPin: vi.fn() } }
})

describe('SetPinPrompt', () => {
  beforeEach(() => vi.clearAllMocks())

  test('rejects mismatched PINs without calling the API', async () => {
    render(<SetPinPrompt email="a@x.com" onDone={vi.fn()} />)
    fireEvent.change(screen.getByLabelText('Contraseña actual'), { target: { value: 'pw' } })
    fireEvent.change(screen.getByLabelText('Nuevo PIN (4-6 dígitos)'), { target: { value: '1234' } })
    fireEvent.change(screen.getByLabelText('Confirmar PIN'), { target: { value: '5678' } })
    fireEvent.click(screen.getByText('Guardar PIN'))
    expect(await screen.findByText('Los PIN no coinciden')).toBeVisible()
    expect(authService.setPin).not.toHaveBeenCalled()
  })

  test('submits when PINs match', async () => {
    vi.mocked(authService.setPin).mockResolvedValue(undefined)
    const onDone = vi.fn()
    render(<SetPinPrompt email="a@x.com" onDone={onDone} />)
    fireEvent.change(screen.getByLabelText('Contraseña actual'), { target: { value: 'pw' } })
    fireEvent.change(screen.getByLabelText('Nuevo PIN (4-6 dígitos)'), { target: { value: '1234' } })
    fireEvent.change(screen.getByLabelText('Confirmar PIN'), { target: { value: '1234' } })
    fireEvent.click(screen.getByText('Guardar PIN'))
    await waitFor(() =>
      expect(authService.setPin).toHaveBeenCalledWith({ currentPassword: 'pw', pin: '1234' }))
    await waitFor(() => expect(onDone).toHaveBeenCalled())
  })
})
