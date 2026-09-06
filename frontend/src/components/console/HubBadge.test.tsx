import { describe, test, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { HubBadge } from '@/components/console/HubBadge'

describe('HubBadge', () => {
  test('renders the label for a live hub', () => {
    render(<HubBadge status="ONLINE" />)
    expect(screen.getByText('ONLINE')).toBeVisible()
  })

  test('renders a dash for NEVER', () => {
    render(<HubBadge status="NEVER" />)
    expect(screen.getByText('—')).toBeVisible()
  })
})
