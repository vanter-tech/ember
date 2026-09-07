import { describe, test, expect, vi, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { SettingsBar } from '@/components/SettingsBar'

// SettingsBar builds its section tree per render (buildSettingsNav), so the Hub-build check is a
// live call — flip the hoisted flag between cases, no module-graph reset or dynamic re-import.
const { hubFlag } = vi.hoisted(() => ({ hubFlag: { current: true } }))
vi.mock('@/lib/isHubBuild', () => ({
  isHubBuild: () => hubFlag.current,
}))

const noop = () => {}
const renderBar = () => render(<SettingsBar collapsed={false} onToggleCollapsed={noop} />)

afterEach(() => {
  hubFlag.current = true
})

describe('SettingsBar loyalty visibility by build', () => {
  test('Hub build hides the loyalty settings group', () => {
    hubFlag.current = true
    renderBar()

    expect(screen.queryByText(/fidelizaci|loyalty/i)).not.toBeInTheDocument()
  })

  test('cloud build shows the loyalty settings group', () => {
    hubFlag.current = false
    renderBar()

    expect(screen.getByText(/fidelizaci|loyalty/i)).toBeInTheDocument()
  })
})
