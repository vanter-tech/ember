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
import { Button } from '@/components/ui/button'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { inventoryMenuItemService, SessionTableService, type MenuItemResponse } from '@/lib/api'
import toast from 'react-hot-toast'
import axios from 'axios'
import { useTranslation } from '@/lib/i18n'

export const AddItemModal = () => {
  const { t } = useTranslation('waiter')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()

  const isOpen = activeModal === 'ADD_ITEM'
  const sessionId: string | undefined = modalPayload?.sessionId
  const participants: { name?: string }[] = modalPayload?.participants ?? []

  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<MenuItemResponse | null>(null)
  const [optionIds, setOptionIds] = useState<Record<number, number[]>>({})
  const [qty, setQty] = useState(1)
  const [participant, setParticipant] = useState('')

  const { data: menuItems = [] } = useQuery({
    queryKey: ['menuItemsAll'],
    queryFn: inventoryMenuItemService.listAll,
    enabled: isOpen,
  })

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase()
    return menuItems.filter((item) => (item.name ?? '').toLowerCase().includes(term))
  }, [menuItems, search])

  const groups = selected?.modifierGroups ?? []
  const flatOptionIds = Object.values(optionIds).flat()

  const reset = () => {
    setSearch('')
    setSelected(null)
    setOptionIds({})
    setQty(1)
    setParticipant('')
  }

  const handleClose = () => {
    reset()
    closeModal()
  }

  const pickItem = (item: MenuItemResponse) => {
    setSelected(item)
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

  const mutation = useMutation({
    mutationFn: async () => {
      for (let i = 0; i < qty; i++) {
        await SessionTableService.addWaiterItem(sessionId!, {
          menuItemId: selected!.id!,
          selectedOptionIds: flatOptionIds,
          participantName: participant || null,
        })
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

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-lg rounded-3xl p-6">
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('addItemModalTitle')}
          </DialogTitle>
          <DialogDescription className="sr-only">{t('addItemModalTitle')}</DialogDescription>
        </DialogHeader>

        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={t('addItemSearchPlaceholder')}
          className="w-full rounded-2xl border-2 border-zinc-200 px-4 py-2 outline-none focus:border-[#8B0000]"
        />

        <div className="flex flex-col gap-1 max-h-60 overflow-y-auto pr-1">
          {filtered.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => pickItem(item)}
              className={`text-left rounded-2xl border-2 px-4 py-3 transition-colors ${
                selected?.id === item.id
                  ? 'border-[#8B0000] bg-[#8B0000]/5'
                  : 'border-zinc-200 hover:border-zinc-300'
              }`}
            >
              <span className="font-semibold block">{item.name}</span>
              <span className="text-sm text-zinc-500">${(item.price ?? 0).toFixed(2)}</span>
            </button>
          ))}
        </div>

        {groups.length > 0 && (
          <div className="space-y-4 border-t border-zinc-100 pt-3">
            {groups.map((group) => (
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
          </div>
        )}

        <div className="grid grid-cols-2 gap-3">
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-zinc-500">{t('addItemParticipantLabel')}</span>
            <select
              value={participant}
              onChange={(e) => setParticipant(e.target.value)}
              className="rounded-2xl border-2 border-zinc-200 px-3 py-2 outline-none focus:border-[#8B0000]"
            >
              <option value="">{t('addItemParticipantMesa')}</option>
              {participants
                .filter((p): p is { name: string } => Boolean(p.name))
                .map((p) => (
                  <option key={p.name} value={p.name}>
                    {p.name}
                  </option>
                ))}
            </select>
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-zinc-500">{t('addItemQuantityLabel')}</span>
            <input
              type="number"
              min={1}
              value={qty}
              onChange={(e) => setQty(Math.max(1, Number(e.target.value) || 1))}
              className="rounded-2xl border-2 border-zinc-200 px-3 py-2 outline-none focus:border-[#8B0000]"
            />
          </label>
        </div>

        <DialogFooter>
          <Button
            className="w-full"
            onClick={() => mutation.mutate()}
            disabled={!selected || mutation.isPending}
          >
            {t('addItemSubmit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
