import { useEffect, useMemo, useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Popover, PopoverAnchor, PopoverContent } from '@/components/ui/popover'
import { ModifierOptionBadges } from '@/components/ModifierOptionBadges'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { inventoryMenuItemService, SessionTableService, type MenuItemResponse } from '@/lib/api'
import { Armchair, Plus, ShoppingCart, Trash2, User, UtensilsCrossed, X } from 'lucide-react'
import toast from 'react-hot-toast'
import axios from 'axios'
import { useTranslation } from '@/lib/i18n'

/** One (menuItem + modifier selection) combination in a client's draft cart. Same item with a
 * different modifier selection is a separate line — `key` disambiguates them. */
type CartLine = {
  key: string
  menuItemId: number
  name: string
  selectedOptionIds: number[]
  qty: number
}

/** Keyed by participant name; `''` is the shared "Mesa" bucket (participantName: null). */
type Cart = Record<string, CartLine[]>

const MESA_KEY = ''

const lineKeyFor = (menuItemId: number, selectedOptionIds: number[]) =>
  `${menuItemId}:${[...selectedOptionIds].sort((a, b) => a - b).join(',')}`

export const AddItemModal = () => {
  const { t } = useTranslation('waiter')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()

  const isOpen = activeModal === 'ADD_ITEM'
  const sessionId: string | undefined = modalPayload?.sessionId
  const participants: { name?: string }[] = modalPayload?.participants ?? []

  const [search, setSearch] = useState('')
  const [activeClient, setActiveClient] = useState<string>(MESA_KEY)
  const [activeCategory, setActiveCategory] = useState<number | 'none' | undefined>()
  const [cart, setCart] = useState<Cart>({})
  const [pendingItem, setPendingItem] = useState<MenuItemResponse | null>(null)
  const [optionIds, setOptionIds] = useState<Record<number, number[]>>({})
  const [showCartPanel, setShowCartPanel] = useState(false)
  const [panelEntered, setPanelEntered] = useState(false)

  const { data: menuItems = [] } = useQuery({
    queryKey: ['menuItemsAll'],
    queryFn: inventoryMenuItemService.listAll,
    enabled: isOpen,
  })

  const categories = useMemo(() => {
    const map = new Map<number | 'none', { id: number | 'none'; name: string; items: MenuItemResponse[] }>()
    for (const item of menuItems) {
      const id = item.category?.id ?? 'none'
      const name = item.category?.name ?? t('addItemUncategorized')
      if (!map.has(id)) map.set(id, { id, name, items: [] })
      map.get(id)!.items.push(item)
    }
    return Array.from(map.values())
  }, [menuItems, t])

  // Default to the first category once the menu loads — same render-time-adjustment pattern the
  // customer digital menu uses, so it converges in one extra render with no post-paint flash.
  if (categories.length > 0 && activeCategory === undefined) {
    setActiveCategory(categories[0].id)
  }

  const visibleItems = useMemo(() => {
    const term = search.trim().toLowerCase()
    if (term) {
      return menuItems.filter((item) => (item.name ?? '').toLowerCase().includes(term))
    }
    return categories.find((c) => c.id === activeCategory)?.items ?? []
  }, [menuItems, categories, activeCategory, search])

  const reset = () => {
    setSearch('')
    setActiveClient(MESA_KEY)
    setActiveCategory(undefined)
    setCart({})
    setPendingItem(null)
    setOptionIds({})
    setShowCartPanel(false)
    setPanelEntered(false)
  }

  const handleClose = () => {
    reset()
    closeModal()
  }

  const addToCart = (item: MenuItemResponse, selectedOptionIds: number[]) => {
    const key = lineKeyFor(item.id!, selectedOptionIds)
    setCart((prev) => {
      const lines = prev[activeClient] ?? []
      const existing = lines.find((l) => l.key === key)
      const nextLines = existing
        ? lines.map((l) => (l.key === key ? { ...l, qty: l.qty + 1 } : l))
        : [...lines, { key, menuItemId: item.id!, name: item.name ?? '', selectedOptionIds, qty: 1 }]
      return { ...prev, [activeClient]: nextLines }
    })
  }

  const handleTapItem = (item: MenuItemResponse) => {
    if ((item.modifierGroups ?? []).length > 0) {
      setPendingItem(item)
      setOptionIds({})
    } else {
      addToCart(item, [])
    }
  }

  const confirmModifiers = () => {
    if (!pendingItem) return
    addToCart(pendingItem, Object.values(optionIds).flat())
    setPendingItem(null)
    setOptionIds({})
  }

  const toggleSingle = (groupId: number, optionId: number) => {
    setOptionIds((prev) => ({ ...prev, [groupId]: [optionId] }))
  }

  const toggleMulti = (groupId: number, optionId: number, max: number | null | undefined) => {
    setOptionIds((prev) => {
      const current = prev[groupId] ?? []
      if (current.includes(optionId)) {
        return { ...prev, [groupId]: current.filter((id) => id !== optionId) }
      }
      if (max != null && current.length >= max) {
        return prev
      }
      return { ...prev, [groupId]: [...current, optionId] }
    })
  }

  // The cart panel only ever removes a whole line — bumping quantity happens by tapping "+"
  // again on the product card (see report 545).
  const removeLine = (clientKey: string, lineKey: string) => {
    setCart((prev) => ({
      ...prev,
      [clientKey]: (prev[clientKey] ?? []).filter((l) => l.key !== lineKey),
    }))
  }

  const activeCart = cart[activeClient] ?? []
  const activeCartCount = activeCart.reduce((s, l) => s + l.qty, 0)
  const totalCount = Object.values(cart).reduce(
    (sum, lines) => sum + lines.reduce((s, l) => s + l.qty, 0),
    0,
  )

  const mutation = useMutation({
    mutationFn: async () => {
      for (const [clientKey, lines] of Object.entries(cart)) {
        for (const line of lines) {
          for (let i = 0; i < line.qty; i++) {
            await SessionTableService.addWaiterItem(sessionId!, {
              menuItemId: line.menuItemId,
              selectedOptionIds: line.selectedOptionIds,
              participantName: clientKey || null,
            })
          }
        }
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sessionDetails', sessionId] })
      queryClient.invalidateQueries({ queryKey: ['bill', sessionId] })
      toast.success(t('addItemSuccessToast'))
      handleClose()
    },
    onError: (error) => {
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.error(t('addItemBillExistsToast'))
      } else {
        toast.error(t('addItemErrorToast'))
      }
    },
  })

  const clientLabel = (key: string) =>
    key === MESA_KEY ? t('addItemParticipantMesa') : key

  const clientChipClass = (key: string) =>
    `flex cursor-pointer items-center gap-3 rounded-2xl border-2 px-4 py-3 text-left transition-colors ${
      activeClient === key
        ? 'border-[#8B0000] bg-[#8B0000]/5'
        : 'border-zinc-200 hover:border-zinc-300'
    }`

  // The panel is a real DOM descendant of DialogContent now (positioned absolute, breaking out
  // to its left) instead of a floating sibling — Radix's outside-interaction dismiss logic only
  // ever sees "inside the dialog" for anything inside it, closing button included. `top-0
  // bottom-0` on the panel stretches it to match the dialog's own rendered height exactly.
  // Side-by-side (dialog shifted right, panel to its left) only fits from 1920px up; below that
  // — iPad and most laptops — the panel overlays the right side of the dialog instead. 216px is
  // half of (panel width 416px + gap 16px), recentering the pair as one.

  useEffect(() => {
    if (!showCartPanel) return
    const raf = requestAnimationFrame(() => setPanelEntered(true))
    return () => cancelAnimationFrame(raf)
  }, [showCartPanel])

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent
        className={`rounded-3xl p-8 transition-[margin-left] duration-300 ease-out sm:max-w-[min(95vw,88rem)] ${
          showCartPanel ? 'min-[1920px]:ml-[216px]' : ''
        }`}
      >
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('addItemModalTitle')}
          </DialogTitle>
          <DialogDescription className="sr-only">{t('addItemModalTitle')}</DialogDescription>
        </DialogHeader>

        {(
          <>
            <div className="grid grid-cols-[minmax(180px,240px)_1fr] gap-10">
              <div className="flex flex-col gap-3 max-h-[calc(100dvh-16rem)] overflow-y-auto border-r border-zinc-100 pr-6">
                <p className="text-xs font-semibold uppercase tracking-wider text-zinc-500">
                  {t('addItemParticipantLabel')}
                </p>
                <button
                  type="button"
                  aria-pressed={activeClient === MESA_KEY}
                  onClick={() => setActiveClient(MESA_KEY)}
                  className={clientChipClass(MESA_KEY)}
                >
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-zinc-100">
                    <Armchair className="size-5 text-zinc-600" />
                  </span>
                  <span className="min-w-0 flex-1 truncate text-sm font-medium">
                    {t('addItemParticipantMesa')}
                  </span>
                  {(cart[MESA_KEY]?.length ?? 0) > 0 && (
                    <Badge>{cart[MESA_KEY]!.reduce((s, l) => s + l.qty, 0)}</Badge>
                  )}
                </button>
                {participants
                  .filter((p): p is { name: string } => Boolean(p.name))
                  .map((p) => (
                    <button
                      key={p.name}
                      type="button"
                      aria-pressed={activeClient === p.name}
                      onClick={() => setActiveClient(p.name)}
                      className={clientChipClass(p.name)}
                    >
                      <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-red-100">
                        <User className="size-5 text-red-700" />
                      </span>
                      <span className="min-w-0 flex-1 truncate text-sm font-medium">{p.name}</span>
                      {(cart[p.name]?.length ?? 0) > 0 && (
                        <Badge>{cart[p.name]!.reduce((s, l) => s + l.qty, 0)}</Badge>
                      )}
                    </button>
                  ))}
              </div>

              <div className="flex min-w-0 flex-col gap-4">
                <div className="flex items-center justify-end">
                  <button
                    type="button"
                    onClick={() => setShowCartPanel(true)}
                    className="flex cursor-pointer items-center gap-2 rounded-full border-2 border-zinc-200 px-4 py-2 text-sm font-medium text-zinc-600 transition-colors hover:border-zinc-300"
                  >
                    <ShoppingCart className="size-4" />
                    {t('addItemViewCartButton')}
                    {activeCartCount > 0 && <Badge>{activeCartCount}</Badge>}
                  </button>
                </div>
                <input
                  type="text"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder={t('addItemSearchPlaceholder')}
                  className="w-full rounded-2xl border-2 border-zinc-200 px-4 py-2 outline-none focus:border-[#8B0000]"
                />
                {!search.trim() && (
                  <div className="flex gap-2 overflow-x-auto pb-1">
                    {categories.map((cat) => (
                      <button
                        key={cat.id}
                        type="button"
                        onClick={() => setActiveCategory(cat.id)}
                        className={`shrink-0 cursor-pointer whitespace-nowrap rounded-full px-4 py-2 text-sm font-medium transition-colors ${
                          activeCategory === cat.id
                            ? 'bg-[#8B0000] text-white'
                            : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200'
                        }`}
                      >
                        {cat.name}
                      </button>
                    ))}
                  </div>
                )}
                <div className="grid grid-cols-2 gap-5 max-h-[calc(100dvh-26rem)] overflow-y-auto pr-1">
                  {visibleItems.map((item) => (
                    // Deliberately not a single big button: a card this dense on a touch screen is
                    // an easy misclick, so only the "+" adds — the rest is inert display.
                    <Popover
                      key={item.id}
                      open={pendingItem?.id === item.id}
                      onOpenChange={(open) => !open && setPendingItem(null)}
                    >
                    <PopoverAnchor asChild>
                    <div className="flex flex-col overflow-hidden rounded-2xl border-2 border-zinc-200">
                      <div className="h-52 w-full bg-zinc-100">
                        {item.imageUrl ? (
                          <img
                            src={item.imageUrl}
                            alt={item.name}
                            className="h-full w-full object-cover"
                          />
                        ) : (
                          <div
                            data-testid={`menu-item-placeholder-${item.id}`}
                            className="flex h-full items-center justify-center text-zinc-300"
                          >
                            <UtensilsCrossed className="size-10" />
                          </div>
                        )}
                      </div>
                      <div className="flex items-center justify-between gap-2 p-4">
                        <div className="min-w-0">
                          <p className="truncate text-base font-semibold">{item.name}</p>
                          <p className="text-base text-zinc-500">${(item.price ?? 0).toFixed(2)}</p>
                        </div>
                        <button
                          type="button"
                          aria-label={t('addItemAddAria', { name: item.name ?? '' })}
                          onClick={() => handleTapItem(item)}
                          className="flex size-12 shrink-0 cursor-pointer items-center justify-center rounded-full bg-[#8B0000] text-white shadow-sm transition-colors hover:bg-[#6a1111] active:scale-95"
                        >
                          <Plus className="size-6" />
                        </button>
                      </div>
                    </div>
                    </PopoverAnchor>
                    <PopoverContent
                      side="right"
                      align="start"
                      collisionPadding={16}
                      data-testid="modifiers-panel"
                      className="flex w-96 flex-col gap-3 p-4"
                      style={{ height: 'var(--radix-popover-trigger-height)' }}
                    >
                      <p className="shrink-0 font-semibold">{item.name}</p>
                      <div className="no-scrollbar flex-1 space-y-4 overflow-y-auto">
                        {(item.modifierGroups ?? []).map((group) => (
                          <div key={group.id} className="space-y-2">
                            <p className="text-sm font-semibold">{group.name}</p>
                            <ModifierOptionBadges
                              groupId={group.id}
                              options={group.options ?? []}
                              selectedIds={optionIds[group.id!] ?? []}
                              single={group.selectionType === 'SINGLE_REQUIRED'}
                              onToggle={(optionId) =>
                                group.selectionType === 'SINGLE_REQUIRED'
                                  ? toggleSingle(group.id!, optionId)
                                  : toggleMulti(group.id!, optionId, group.maxSelections)
                              }
                            />
                          </div>
                        ))}
                      </div>
                      <div className="flex shrink-0 justify-end gap-2">
                        <Button variant="outline" size="sm" onClick={() => setPendingItem(null)}>
                          {t('addItemModifierCancel')}
                        </Button>
                        <Button size="sm" onClick={confirmModifiers}>
                          {t('addItemModifierConfirm')}
                        </Button>
                      </div>
                    </PopoverContent>
                    </Popover>
                  ))}
                </div>
              </div>
            </div>

            <DialogFooter>
              <Button
                className="w-full"
                onClick={() => mutation.mutate()}
                disabled={totalCount === 0 || mutation.isPending}
              >
                {t('addItemSubmit')}
                {totalCount > 0 ? ` (${totalCount})` : ''}
              </Button>
            </DialogFooter>
          </>
        )}

        {showCartPanel && (
          // A real child of DialogContent (not a floating sibling) so Radix's own "is this
          // inside the dialog" checks already include it — no manual outside-click guard needed.
          // `top-0 bottom-0` stretches it to match this dialog's own rendered height exactly.
          <div
            data-testid="client-cart-panel"
            className={`absolute top-0 bottom-0 right-0 z-10 flex w-[min(26rem,100%)] flex-col min-[1920px]:right-[calc(100%+1rem)] overflow-hidden rounded-3xl bg-popover p-6 shadow-2xl ring-1 ring-foreground/10 transition-all duration-300 ease-out ${
              panelEntered ? 'translate-x-0 opacity-100' : '-translate-x-4 opacity-0'
            }`}
          >
            <div className="mb-4 flex shrink-0 items-center justify-between">
              <p className="text-base font-semibold text-zinc-800">
                {t('addItemCartHeading', { name: clientLabel(activeClient) })}
              </p>
              <button
                type="button"
                aria-label={t('addItemCartPanelCloseAria')}
                onClick={() => {
                  setShowCartPanel(false)
                  setPanelEntered(false)
                }}
                className="flex size-8 cursor-pointer items-center justify-center rounded-full hover:bg-zinc-100"
              >
                <X className="size-4" />
              </button>
            </div>
            {activeCart.length === 0 ? (
              <p className="text-sm text-zinc-400">{t('addItemEmptyCart')}</p>
            ) : (
              <ul className="flex flex-1 flex-col gap-2 overflow-y-auto pr-1">
                {activeCart.map((line) => (
                  <li
                    key={line.key}
                    className="flex items-center gap-3 rounded-2xl bg-zinc-100 px-3 py-2.5"
                  >
                    <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-white text-xs font-semibold text-zinc-700 ring-1 ring-zinc-200">
                      {line.qty}x
                    </span>
                    <span className="min-w-0 flex-1 truncate text-sm">{line.name}</span>
                    <Button
                      variant="destructive"
                      size="icon"
                      aria-label={t('addItemRemoveLineAria', { name: line.name })}
                      onClick={() => removeLine(activeClient, line.key)}
                    >
                      <Trash2 />
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
