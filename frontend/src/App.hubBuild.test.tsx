import { render, screen } from '@testing-library/react'
import { vi, describe, it, expect, afterEach } from 'vitest'

// isHubBuild is read at render time in App (route gates, RoleRedirect), so a hoisted mock with a
// mutable flag is enough — no vi.stubEnv / resetModules / dynamic re-import of the whole App graph
// (that re-parse is what made this file flake past its timeout under parallel load).
const { hubFlag } = vi.hoisted(() => ({ hubFlag: { current: true } }))
vi.mock('@/lib/isHubBuild', () => ({
  get isHubBuild() {
    return hubFlag.current
  },
}))

import App from './App'

const renderAt = (path: string) => {
  window.history.pushState({}, '', path)
  render(<App />)
}

afterEach(() => {
  hubFlag.current = true
  window.history.pushState({}, '', '/')
})

describe('Hub build routing', () => {
  it('does not mount /menu/join when isHubBuild', async () => {
    hubFlag.current = true
    renderAt('/menu/join?token=abc')

    expect(await screen.findByText(/no encontrada|not found/i)).toBeInTheDocument()
  })

  it('mounts /menu/join on the cloud build', () => {
    hubFlag.current = false
    renderAt('/menu/join?token=abc')

    // MenuJoin renders its own invalid-link / choice screen — anything but the 404 page.
    expect(screen.queryByText(/página no encontrada|page not found/i)).not.toBeInTheDocument()
  })
})
