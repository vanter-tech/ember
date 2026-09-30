import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { PlanSettings } from './PlanSettings'
import { restaurantAdminService, type SubscriptionResponse } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return { ...actual, restaurantAdminService: { getPlan: vi.fn(), getSubscription: vi.fn() } }
})

const DAY_MS = 86_400_000
const inDays = (days: number) => new Date(Date.now() + days * DAY_MS).toISOString()

const subscription = (over: Partial<SubscriptionResponse>): SubscriptionResponse => ({
  plan: 'STARTER',
  status: 'ACTIVE',
  planStartedAt: '2026-01-10T00:00:00Z',
  billingPeriod: 'SEMESTRAL',
  planPeriodEnd: inDays(120),
  ...over,
})

const wrap = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <PlanSettings />
    </QueryClientProvider>,
  )

describe('PlanSettings', () => {
  beforeEach(() => vi.clearAllMocks())

  test('shows the plan, period, days left and what the plan includes, without any price', async () => {
    vi.mocked(restaurantAdminService.getSubscription).mockResolvedValue(subscription({}))
    wrap()

    expect(await screen.findByText('STARTER')).toBeVisible()
    expect(screen.getByText('Semestral')).toBeVisible()
    expect(screen.getByText('120 días restantes')).toBeVisible()
    expect(screen.getByText('Hasta 10 mesas')).toBeVisible()
    expect(screen.queryByText(/\$/)).toBeNull()
    expect(screen.queryByText(/vence pronto/i)).toBeNull()
  })

  test('warns when the period ends within 10 days', async () => {
    vi.mocked(restaurantAdminService.getSubscription).mockResolvedValue(subscription({ planPeriodEnd: inDays(10) }))
    wrap()

    expect(await screen.findByText('Tu plan vence pronto. Contáctanos para renovarlo.')).toBeVisible()
  })

  test('does not warn when more than 10 days remain', async () => {
    vi.mocked(restaurantAdminService.getSubscription).mockResolvedValue(subscription({ planPeriodEnd: inDays(11) }))
    wrap()

    expect(await screen.findByText('11 días restantes')).toBeVisible()
    expect(screen.queryByText('Tu plan vence pronto. Contáctanos para renovarlo.')).toBeNull()
  })

  test('shows an expired notice once the period has passed', async () => {
    vi.mocked(restaurantAdminService.getSubscription).mockResolvedValue(subscription({ planPeriodEnd: inDays(-5) }))
    wrap()

    expect(await screen.findByText(/El período de tu plan ya terminó/)).toBeVisible()
    expect(screen.getByText('Venció hace 5 días')).toBeVisible()
  })

  test('shows the monthly period and next payment date', async () => {
    vi.mocked(restaurantAdminService.getSubscription).mockResolvedValue(
      subscription({ billingPeriod: 'MONTHLY', planPeriodEnd: inDays(20) }),
    )
    wrap()

    expect(await screen.findByText('Mensual')).toBeVisible()
    expect(screen.getByText('Próximo pago')).toBeVisible()
    expect(screen.getByText('20 días restantes')).toBeVisible()
  })

  test('links to the contact page to renew or change plan', async () => {
    vi.mocked(restaurantAdminService.getSubscription).mockResolvedValue(subscription({}))
    wrap()

    const link = await screen.findByRole('link', { name: 'Contactar para renovar o cambiar de plan' })
    expect(link).toHaveAttribute('href', 'https://ember.vanter.net/contacto')
  })
})
