import { describe, test, expect, afterEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QrScanner } from './QrScanner'

vi.mock('jsqr', () => ({ default: () => null }))

const origMediaDevices = navigator.mediaDevices

afterEach(() => {
  Object.defineProperty(navigator, 'mediaDevices', {
    value: origMediaDevices,
    configurable: true,
  })
  vi.restoreAllMocks()
})

describe('QrScanner', () => {
  test('shows the unsupported message when getUserMedia is unavailable', () => {
    Object.defineProperty(navigator, 'mediaDevices', { value: undefined, configurable: true })
    render(<QrScanner onDecoded={vi.fn()} />)
    expect(screen.getByText(/no permite escanear/i)).toBeInTheDocument()
  })

  test('renders the camera view + hint when getUserMedia is available', () => {
    Object.defineProperty(navigator, 'mediaDevices', {
      value: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [] }) },
      configurable: true,
    })
    const { container } = render(<QrScanner onDecoded={vi.fn()} />)
    expect(container.querySelector('video')).toBeInTheDocument()
    expect(screen.getByText(/cámara/i)).toBeInTheDocument()
  })
})
