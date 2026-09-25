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

// The active client's draft cart lives in a floating panel opened via "Ver pedido" (see report
// 545) — there's no inline cart section in the main view any more.
const openCartPanel = () => fireEvent.click(screen.getByRole('button', { name: /Ver pedido/ }))

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

    openCartPanel()
    expect(within(screen.getByTestId('client-cart-panel')).queryByText(/Pizza/)).not.toBeInTheDocument()
  })

  test('the "+" button adds an item with no modifiers straight to the active client cart', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    fireEvent.click(addButtonFor('Pizza'))
    openCartPanel()

    const panel = screen.getByTestId('client-cart-panel')
    expect(within(panel).getByText(/Pizza/)).toBeVisible()
    expect(within(panel).getByText('1x')).toBeVisible()
  })

  test('tapping "+" again bumps the quantity in the cart', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    fireEvent.click(addButtonFor('Pizza'))
    fireEvent.click(addButtonFor('Pizza'))
    openCartPanel()

    expect(within(screen.getByTestId('client-cart-panel')).getByText('2x')).toBeVisible()
  })

  test('each client keeps their own separate cart', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    fireEvent.click(addButtonFor('Pizza'))

    fireEvent.click(screen.getByRole('button', { name: /Ana/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Entradas' }))
    fireEvent.click(addButtonFor('Ensalada'))
    openCartPanel()

    const panel = screen.getByTestId('client-cart-panel')
    expect(within(panel).getByText(/Ensalada/)).toBeVisible()
    expect(within(panel).queryByText(/Pizza/)).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /Mesa \(general\)/ }))
    expect(within(screen.getByTestId('client-cart-panel')).getByText(/Pizza/)).toBeVisible()
  })

  test('an item with modifier groups opens a picker before it reaches the cart', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    fireEvent.click(screen.getByRole('button', { name: 'Postres' }))
    await screen.findByText('Helado')
    fireEvent.click(addButtonFor('Helado'))

    expect(await screen.findByTestId('modifiers-panel')).toBeVisible()
    fireEvent.click(screen.getByText('Chocolate'))
    fireEvent.click(screen.getByText('Agregar'))

    openCartPanel()
    expect(within(screen.getByTestId('client-cart-panel')).getByText(/Helado/)).toBeVisible()
  })

  test('opening the cart panel with nothing added shows the empty state', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    openCartPanel()

    expect(
      within(screen.getByTestId('client-cart-panel')).getByText(/Todavía no hay platillos/),
    ).toBeVisible()
  })

  test('the trash button on a cart panel line removes it entirely, regardless of quantity', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    fireEvent.click(addButtonFor('Pizza'))
    fireEvent.click(addButtonFor('Pizza')) // qty 2
    openCartPanel()

    const panel = screen.getByTestId('client-cart-panel')
    expect(within(panel).getByText('2x')).toBeVisible()

    fireEvent.click(within(panel).getByLabelText('Eliminar Pizza'))
    expect(within(panel).queryByText(/Pizza/)).not.toBeInTheDocument()
    expect(within(panel).getByText(/Todavía no hay platillos/)).toBeVisible()
  })

  // The panel is now a real DOM child of DialogContent (see report 547), not a floating sibling —
  // that structural fix is what makes both of the next two tests pass: Radix's outside-interaction
  // dismiss logic only ever sees "outside" for things that aren't inside the dialog's own content
  // node, and the panel now always is one. Report 546 tried patching this with a ref-based
  // `onInteractOutside` guard; it missed the close button specifically, because clicking it
  // unmounts the panel (and the focused button with it), which is exactly the case a "click
  // target" guard can't catch — the fix had to be structural, not a guard.
  test('closing the cart panel via its own close button only hides the panel, not the whole modal', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    fireEvent.click(addButtonFor('Pizza'))
    openCartPanel()

    const closeButton = screen.getByLabelText('Cerrar carrito')
    fireEvent.pointerDown(closeButton)
    fireEvent.pointerUp(closeButton)
    fireEvent.click(closeButton)

    expect(screen.queryByTestId('client-cart-panel')).not.toBeInTheDocument()
    // the modal itself is still open, not the waiter back at the tables view
    expect(screen.getByText(/Confirmar pedido/)).toBeInTheDocument()
  })

  test('interacting with a line inside the cart panel does not close the whole modal', async () => {
    wrap(<AddItemModal />)
    await screen.findByText('Pizza')
    fireEvent.click(addButtonFor('Pizza'))
    openCartPanel()

    const panel = screen.getByTestId('client-cart-panel')
    const line = within(panel).getByText(/Pizza/)
    fireEvent.pointerDown(line)
    fireEvent.pointerUp(line)

    expect(screen.getByRole('button', { name: /Ver pedido/ })).toBeInTheDocument()
    expect(screen.getByTestId('client-cart-panel')).toBeInTheDocument()
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
