import { useMemo, useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Checkbox } from '@/components/ui/checkbox'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { inventoryMenuItemService, SessionTableService, type MenuItemResponse } from '@/lib/api'
import { Armchair, Minus, Plus, User } from 'lucide-react'
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

  const decrementLine = (clientKey: string, lineKey: string) => {
    setCart((prev) => {
      const lines = prev[clientKey] ?? []
      const line = lines.find((l) => l.key === lineKey)
      if (!line) return prev
      const nextLines =
        line.qty <= 1
          ? lines.filter((l) => l.key !== lineKey)
          : lines.map((l) => (l.key === lineKey ? { ...l, qty: l.qty - 1 } : l))
      return { ...prev, [clientKey]: nextLines }
    })
  }

  const incrementLine = (clientKey: string, lineKey: string) => {
    setCart((prev) => ({
      ...prev,
      [clientKey]: (prev[clientKey] ?? []).map((l) =>
        l.key === lineKey ? { ...l, qty: l.qty + 1 } : l,
      ),
    }))
  }

  const activeCart = cart[activeClient] ?? []
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
    `flex items-center gap-2 rounded-2xl border-2 px-3 py-2.5 text-left transition-colors ${
      activeClient === key
        ? 'border-[#8B0000] bg-[#8B0000]/5'
        : 'border-zinc-200 hover:border-zinc-300'
    }`

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-3xl rounded-3xl p-6">
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('addItemModalTitle')}
          </DialogTitle>
          <DialogDescription className="sr-only">{t('addItemModalTitle')}</DialogDescription>
        </DialogHeader>

        {pendingItem ? (
          <div className="space-y-4">
            <p className="font-semibold">{pendingItem.name}</p>
            {(pendingItem.modifierGroups ?? []).map((group) => (
              <div key={group.id} className="space-y-2">
                <p className="font-semibold text-sm">{group.name}</p>
                {group.selectionType === 'SINGLE_REQUIRED' ? (
                  <RadioGroup
                    value={String(optionIds[group.id!]?.[0] ?? '')}
                    onValueChange={(v) => toggleSingle(group.id!, Number(v))}
                  >
                    {(group.options ?? []).map((option) => (
                      <label key={option.id} className="flex items-center gap-2 text-sm">
                        <RadioGroupItem value={String(option.id)} />
                        {option.name}{' '}
                        {option.priceDelta ? `(+$${option.priceDelta.toFixed(2)})` : ''}
                      </label>
                    ))}
                  </RadioGroup>
                ) : (
                  (group.options ?? []).map((option) => (
                    <label key={option.id} className="flex items-center gap-2 text-sm">
                      <Checkbox
                        checked={optionIds[group.id!]?.includes(option.id!) ?? false}
                        onCheckedChange={() =>
                          toggleMulti(group.id!, option.id!, group.maxSelections)
                        }
                      />
                      {option.name}{' '}
                      {option.priceDelta ? `(+$${option.priceDelta.toFixed(2)})` : ''}
                    </label>
                  ))
                )}
              </div>
            ))}
            <DialogFooter>
              <Button variant="outline" onClick={() => setPendingItem(null)}>
                {t('addItemModifierCancel')}
              </Button>
              <Button onClick={confirmModifiers}>{t('addItemModifierConfirm')}</Button>
            </DialogFooter>
          </div>
        ) : (
          <>
            <div className="grid grid-cols-[minmax(160px,220px)_1fr] gap-4">
              <div className="flex flex-col gap-2 max-h-[50vh] overflow-y-auto pr-1">
                <p className="text-xs font-semibold uppercase tracking-wider text-zinc-500">
                  {t('addItemParticipantLabel')}
                </p>
                <button
                  type="button"
                  aria-pressed={activeClient === MESA_KEY}
                  onClick={() => setActiveClient(MESA_KEY)}
                  className={clientChipClass(MESA_KEY)}
                >
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-zinc-100">
                    <Armchair className="size-4 text-zinc-600" />
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
                      <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-red-100">
                        <User className="size-4 text-red-700" />
                      </span>
                      <span className="min-w-0 flex-1 truncate text-sm font-medium">{p.name}</span>
                      {(cart[p.name]?.length ?? 0) > 0 && (
                        <Badge>{cart[p.name]!.reduce((s, l) => s + l.qty, 0)}</Badge>
                      )}
                    </button>
                  ))}
              </div>

              <div className="flex min-w-0 flex-col gap-3">
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
                        className={`shrink-0 whitespace-nowrap rounded-full px-4 py-2 text-sm font-medium transition-colors ${
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
                <div className="grid grid-cols-2 gap-2 max-h-[38vh] overflow-y-auto pr-1">
                  {visibleItems.map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => handleTapItem(item)}
                      className="rounded-2xl border-2 border-zinc-200 px-4 py-3 text-left transition-colors hover:border-zinc-300"
                    >
                      <span className="font-semibold block">{item.name}</span>
                      <span className="text-sm text-zinc-500">${(item.price ?? 0).toFixed(2)}</span>
                    </button>
                  ))}
                </div>
              </div>
            </div>

            <div className="border-t border-zinc-100 pt-3" data-testid="active-client-cart">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-zinc-500">
                {t('addItemCartHeading', { name: clientLabel(activeClient) })}
              </p>
              {activeCart.length === 0 ? (
                <p className="text-sm text-zinc-400">{t('addItemEmptyCart')}</p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {activeCart.map((line) => (
                    <span
                      key={line.key}
                      className="flex items-center gap-1.5 rounded-full bg-zinc-100 py-1 pl-3 pr-1 text-sm"
                    >
                      {line.name} <span className="font-semibold">×{line.qty}</span>
                      <button
                        type="button"
                        aria-label={t('addItemDecrementAria', { name: line.name })}
                        onClick={() => decrementLine(activeClient, line.key)}
                        className="flex size-6 items-center justify-center rounded-full hover:bg-zinc-200"
                      >
                        <Minus className="size-3.5" />
                      </button>
                      <button
                        type="button"
                        aria-label={t('addItemIncrementAria', { name: line.name })}
                        onClick={() => incrementLine(activeClient, line.key)}
                        className="flex size-6 items-center justify-center rounded-full hover:bg-zinc-200"
                      >
                        <Plus className="size-3.5" />
                      </button>
                    </span>
                  ))}
                </div>
              )}
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
      </DialogContent>
    </Dialog>
  )
}
