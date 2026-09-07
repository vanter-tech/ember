import { render, screen } from '@testing-library/react'
import { vi, describe, it, expect, afterEach } from 'vitest'

// App and the isHubBuild module both read import.meta.env.BASE_URL at module scope, so each case
// stubs the env, resets the module graph, and re-imports App.
const renderAt = async (path: string) => {
  window.history.pushState({}, '', path)
  const { default: App } = await import('./App')
  render(<App />)
}

afterEach(() => {
  vi.unstubAllEnvs()
  vi.resetModules()
  window.history.pushState({}, '', '/')
})

describe('Hub build routing', () => {
  it('does not mount /menu/join when isHubBuild', async () => {
    vi.stubEnv('BASE_URL', '/app/')
    await renderAt('/app/menu/join?token=abc')

    expect(await screen.findByText(/no encontrada|not found/i)).toBeInTheDocument()
  }, 20000)

  it('mounts /menu/join on the cloud build', async () => {
    vi.stubEnv('BASE_URL', '/')
    await renderAt('/menu/join?token=abc')

    // MenuJoin renders its own invalid-link / choice screen — anything but the 404 page.
    expect(screen.queryByText(/página no encontrada|page not found/i)).not.toBeInTheDocument()
  }, 20000)
})
