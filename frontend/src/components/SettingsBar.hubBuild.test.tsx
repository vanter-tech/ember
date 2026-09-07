import { describe, test, expect, vi, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'

// SettingsBar builds SETTINGS_NAV at module scope, so the isHubBuild value is baked in at import
// time — each case sets the flag, resets the module graph, and re-imports.
const { hubFlag } = vi.hoisted(() => ({ hubFlag: { current: true } }))
vi.mock('@/lib/isHubBuild', () => ({
  get isHubBuild() {
    return hubFlag.current
  },
}))

const noop = () => {}

const renderBar = async () => {
  const { SettingsBar } = await import('@/components/SettingsBar')
  render(<SettingsBar collapsed={false} onToggleCollapsed={noop} />)
}

afterEach(() => vi.resetModules())

describe('SettingsBar loyalty visibility by build', () => {
  test('Hub build hides the loyalty settings group', async () => {
    hubFlag.current = true
    await renderBar()

    expect(screen.queryByText(/fidelizaci|loyalty/i)).not.toBeInTheDocument()
  })

  test('cloud build shows the loyalty settings group', async () => {
    hubFlag.current = false
    await renderBar()

    expect(screen.getByText(/fidelizaci|loyalty/i)).toBeInTheDocument()
  })
})
