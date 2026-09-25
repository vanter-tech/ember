import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import { PasswordInput } from '@/components/PasswordInput'

describe('PasswordInput', () => {
  test('toggles between hidden and visible', async () => {
    const user = userEvent.setup()
    render(<PasswordInput placeholder="pw" />)

    const input = screen.getByPlaceholderText('pw')
    const toggle = screen.getByRole('button')
    expect(input).toHaveAttribute('type', 'password')
    expect(toggle).toHaveAttribute('aria-pressed', 'false')

    await user.click(toggle)
    expect(input).toHaveAttribute('type', 'text')
    expect(toggle).toHaveAttribute('aria-pressed', 'true')

    await user.click(toggle)
    expect(input).toHaveAttribute('type', 'password')
  })
})
