import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AddItemModal } from '@/pages/waiter/components/AddItemModal'
import { useUIStore } from '@/store/uiStore'
import { SessionTableService, inventoryMenuItemService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    inventoryMenuItemService: { listAll: vi.fn() },
    SessionTableService: { ...actual.SessionTableService, addWaiterItem: vi.fn() },
  }
})

const wrap = (ui: ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

// The "+" is the only interactive add-trigger on a product card (see report 544) — the rest of
// the card (image, name, price) is deliberately inert so a stray touch can't queue an order.
const addButtonFor = (name: string) => screen.getByRole('button', { name: `Agregar ${name}` })

const pizza = {
  id: 10,
  name: 'Pizza',
  price: 12,
  available: true,
  modifierGroups: [],
  category: { id: 1, name: 'Platos fuertes' },
  imageUrl: 'https://cdn.example.com/pizza.jpg',
}
const ensalada = {
  id: 11,
  name: 'Ensalada',
  price: 8,
  available: true,
  modifierGroups: [],
  category: { id: 2, name: 'Entradas' },
}
const helado = {
  id: 12,
  name: 'Helado',
  price: 5,
  available: true,
  modifierGroups: [
    {
      id: 100,
      name: 'Sabor',
      selectionType: 'SINGLE_REQUIRED' as const,
      minSelections: 1,
      maxSelections: 1,
      active: true,
      options: [
        { id: 200, name: 'Vainilla', priceDelta: 0, active: true },
        { id: 201, name: 'Chocolate', priceDelta: 0, active: true },
      ],
    },
  ],
  category: { id: 3, name: 'Postres' },
}

describe('AddItemModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUIStore.setState({
      activeModal: 'ADD_ITEM',
      modalPayload: { sessionId: 's1', participants: [{ name: 'Ana' }, { name: 'Beto' }] },
    })
    vi.mocked(inventoryMenuItemService.listAll).mockResolvedValue([pizza, ensalada, helado])
  })

  test('selects "Mesa" as the active client by default', async () => {
    wrap(<AddItemModal />)
    const mesaChip = await screen.findByRole('button', { name: /Mesa \(general\)/ })
    expect(mesaChip).toHaveAttribute('aria-pressed', 'true')
  })

  test('renders a chip for each participant', async () => {
    wrap(<AddItemModal />)
    expect(await screen.findByRole('button', { name: /Ana/ })).toBeVisible()
    expect(screen.getByRole('button', { name: /Beto/ })).toBeVisible()
  })

  test('tapping a category shows only its items, tapping another switches', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    expect(screen.queryByText('Ensalada')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Entradas' }))
    expect(screen.queryByText('Pizza')).not.toBeInTheDocument()
    expect(screen.getByText('Ensalada')).toBeVisible()
  })

  test('search overrides the category filter across every item', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    fireEvent.click(screen.getByRole('button', { name: 'Entradas' }))

    fireEvent.change(screen.getByPlaceholderText('Buscar platillo...'), {
      target: { value: 'piz' },
    })
    expect(screen.getByText('Pizza')).toBeVisible()
    expect(screen.queryByText('Ensalada')).not.toBeInTheDocument()
  })

  test('tapping the card itself does not add anything — only the "+" button does', async () => {
    wrap(<AddItemModal />)
    fireEvent.click(await screen.findByText('Pizza'))
    fireEvent.click(screen.getByText('$12.00'))

    expect(within(screen.getByTestId('active-client-cart')).queryByText(/Pizza/)).not.toBeInTheDocument()
  })

  test('the "+" button adds an item with no modifiers straight to the active client cart', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    fireEvent.click(addButtonFor('Pizza'))

    const cart = screen.getByTestId('active-client-cart')
    expect(within(cart).getByText(/Pizza/)).toBeVisible()
    expect(within(cart).getByText('×1')).toBeVisible()
  })

  test('tapping "+" again bumps the quantity in the cart', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    fireEvent.click(addButtonFor('Pizza'))
    fireEvent.click(addButtonFor('Pizza'))

    const cart = screen.getByTestId('active-client-cart')
    expect(within(cart).getByText('×2')).toBeVisible()
  })

  test('each client keeps their own separate cart', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    fireEvent.click(addButtonFor('Pizza'))

    fireEvent.click(screen.getByRole('button', { name: /Ana/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Entradas' }))
    fireEvent.click(addButtonFor('Ensalada'))

    const cart = screen.getByTestId('active-client-cart')
    expect(within(cart).getByText(/Ensalada/)).toBeVisible()
    expect(within(cart).queryByText(/Pizza/)).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /Mesa \(general\)/ }))
    expect(within(screen.getByTestId('active-client-cart')).getByText(/Pizza/)).toBeVisible()
  })

  test('an item with modifier groups opens a picker before it reaches the cart', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    fireEvent.click(screen.getByRole('button', { name: 'Postres' }))
    await screen.findByText('Helado')
    fireEvent.click(addButtonFor('Helado'))

    expect(screen.queryByTestId('active-client-cart')).not.toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('Chocolate'))
    fireEvent.click(screen.getByText('Agregar'))

    const cart = screen.getByTestId('active-client-cart')
    expect(within(cart).getByText(/Helado/)).toBeVisible()
  })

  test('the minus stepper removes the line once it reaches zero', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    fireEvent.click(addButtonFor('Pizza'))

    const cart = screen.getByTestId('active-client-cart')
    fireEvent.click(within(cart).getByLabelText('Quitar uno de Pizza'))
    expect(within(cart).queryByText(/Pizza/)).not.toBeInTheDocument()
  })

  test('confirm sends one addWaiterItem call per unit, across every client', async () => {
    vi.mocked(SessionTableService.addWaiterItem).mockResolvedValue(undefined)
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    fireEvent.click(addButtonFor('Pizza'))
    fireEvent.click(addButtonFor('Pizza')) // Mesa: 2x Pizza

    fireEvent.click(screen.getByRole('button', { name: /Ana/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Entradas' }))
    fireEvent.click(addButtonFor('Ensalada')) // Ana: 1x Ensalada

    fireEvent.click(screen.getByText(/Confirmar pedido/))

    await waitFor(() => expect(SessionTableService.addWaiterItem).toHaveBeenCalledTimes(3))
    expect(SessionTableService.addWaiterItem).toHaveBeenCalledWith('s1', {
      menuItemId: 10,
      selectedOptionIds: [],
      participantName: null,
    })
    expect(SessionTableService.addWaiterItem).toHaveBeenCalledWith('s1', {
      menuItemId: 11,
      selectedOptionIds: [],
      participantName: 'Ana',
    })
  })

  test('confirm button is disabled with an empty cart', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    expect(screen.getByText(/Confirmar pedido/)).toBeDisabled()
  })

  test('shows the item photo when available, a placeholder icon otherwise', async () => {
    wrap(<AddItemModal />)
    const photo = await screen.findByAltText('Pizza') // has imageUrl in the fixture
    expect(photo).toHaveAttribute('src', 'https://cdn.example.com/pizza.jpg')

    fireEvent.click(screen.getByRole('button', { name: 'Entradas' }))
    expect(screen.getByTestId('menu-item-placeholder-11')).toBeInTheDocument() // Ensalada, no photo
  })
})
