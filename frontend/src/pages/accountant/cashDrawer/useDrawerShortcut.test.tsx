import { describe, test, expect, vi } from 'vitest'
import { render, fireEvent } from '@testing-library/react'
import { useDrawerShortcut } from './useDrawerShortcut'

const Probe = ({ onTrigger }: { onTrigger: () => void }) => {
  useDrawerShortcut(onTrigger)
  return null
}

describe('useDrawerShortcut', () => {
  test('F9 triggers once and is prevented; other keys and key-repeat are ignored', () => {
    const onTrigger = vi.fn()
    render(<Probe onTrigger={onTrigger} />)
    const f9 = new KeyboardEvent('keydown', { key: 'F9', cancelable: true })
    window.dispatchEvent(f9)
    expect(onTrigger).toHaveBeenCalledTimes(1)
    expect(f9.defaultPrevented).toBe(true)
    fireEvent.keyDown(window, { key: 'F8' })
    fireEvent.keyDown(window, { key: 'F9', repeat: true })
    expect(onTrigger).toHaveBeenCalledTimes(1)
  })
})
