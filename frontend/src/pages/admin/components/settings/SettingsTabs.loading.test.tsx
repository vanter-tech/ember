import type { ComponentType } from 'react'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { BillingSettings } from './BillingSettings'
import { BusinessHoursSettings } from './BusinessHoursSettings'
import { HardwareSettings } from './HardwareSettings'
import { LoyaltySettings } from './LoyaltySettings'
import { MenuSettings } from './MenuSettings'
import { PaymentGatewaySettings } from './PaymentGatewaySettings'
import { SpacesSettings } from './SpaceSettings'
import { TicketSettings } from './TicketSettings'
import { LoyaltyRewardsSettings } from './LoyaltyRewardsSettings'
import { BrandingSettings } from './BrandingSettings'
import { PlanSettings } from './PlanSettings'

const renderTab = (Tab: ComponentType) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <Tab />
      </MemoryRouter>
    </QueryClientProvider>,
  )

describe('Settings tabs while their data is loading', () => {
  beforeEach(() => {
    // Every GET stays pending: nothing resolves, and nothing goes over the real network.
    vi.spyOn(api, 'get').mockReturnValue(new Promise(() => {}) as never)
  })
  afterEach(() => vi.restoreAllMocks())

  // [tab, component, single fields, toggle rows]: the form each tab draws before its data arrives.
  const tabs: [string, ComponentType, number, number][] = [
    ['BrandingSettings', BrandingSettings, 8, 0],
    ['BillingSettings', BillingSettings, 6, 3],
    ['BusinessHoursSettings', BusinessHoursSettings, 14, 7],
    ['HardwareSettings', HardwareSettings, 0, 2],
    ['LoyaltySettings', LoyaltySettings, 6, 1],
    ['MenuSettings', MenuSettings, 0, 2],
    ['PaymentGatewaySettings', PaymentGatewaySettings, 3, 1],
    ['SpacesSettings', SpacesSettings, 1, 0],
    ['TicketSettings', TicketSettings, 3, 4],
  ]

  test.each(tabs)('%s draws its own form as blocks (no real text) and announces loading', (_name, Tab, fields, toggles) => {
    renderTab(Tab)

    expect(screen.getByTestId('skeleton-settings-header')).toBeInTheDocument()
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryAllByTestId('skeleton-field')).toHaveLength(fields)
    expect(screen.queryAllByTestId('skeleton-toggle')).toHaveLength(toggles)
    expect(screen.getByTestId('skeleton-settings-footer')).toBeInTheDocument()
    expect(screen.getByText('Cargando configuraciones...').closest('[role="status"]')).not.toBeNull()
  })

  test('BillingSettings shows the tips chips and tax-rule rows; TicketSettings its preview buttons', () => {
    const { unmount } = renderTab(BillingSettings)
    expect(screen.getAllByTestId('skeleton-chips')).toHaveLength(1)
    unmount()

    renderTab(TicketSettings)
    expect(screen.getAllByTestId('skeleton-preview')).toHaveLength(1)
  })

  test('PlanSettings is a read-only card of blocks (it used to render nothing while loading)', () => {
    const { container } = renderTab(PlanSettings)

    expect(container).not.toBeEmptyDOMElement()
    expect(screen.getByTestId('skeleton-settings-header')).toBeInTheDocument()
    expect(screen.getByTestId('skeleton-plan-name')).toBeInTheDocument()
    expect(screen.getAllByTestId('skeleton-plan-row').length).toBeGreaterThanOrEqual(3)
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    expect(screen.queryByTestId('skeleton-settings-footer')).not.toBeInTheDocument()
    expect(screen.getByText('Cargando configuraciones...').closest('[role="status"]')).not.toBeNull()
  })

  test('LoyaltyRewardsSettings is a whole-card placeholder: header with its button, and a 4-column table', () => {
    renderTab(LoyaltyRewardsSettings)

    expect(screen.getByTestId('skeleton-settings-header')).toBeInTheDocument()
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.getAllByTestId('skeleton-table')).toHaveLength(1)
    expect(screen.getAllByTestId('skeleton-table-row').length).toBeGreaterThanOrEqual(4)
    expect(screen.getByText('Cargando recompensas...').closest('[role="status"]')).not.toBeNull()
  })
})
