import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  categoryService,
  modifierGroupService,
  inventoryService,
  staffService,
} from '@/lib/api'
import { useUIStore, useSettingsStore, type SettingsType } from '@/store/uiStore'
import { useTranslation } from '@/lib/i18n'
import { dictionaries } from '@/locales'

type AdminTranslationKey = keyof (typeof dictionaries)['es']['admin']

// Reuses SettingsBar.tsx's own tab labels so the search results and the sidebar never drift.
const SETTINGS_TAB_LABEL_KEYS: Record<Exclude<SettingsType, null>, AdminTranslationKey> = {
  BRANDING: 'brandingAndBusinessLabel',
  MENU: 'menuLabel',
  BILLING: 'billingLabel',
  PAYMENT_GATEWAY: 'paymentGatewayCardTitle',
  TICKET: 'ticketLabel',
  PRINTING: 'printingLabel',
  HARDWARE: 'hardwareGeneralLabel',
  SPACE: 'spaceLabel',
  HORARIO: 'scheduleLabel',
  FIDELIZACION: 'loyaltyLabel',
  LOYALTY_REWARDS: 'rewardCatalogTitle',
}

interface ResultEntry {
  id: string
  label: string
  onSelect: () => void
}

interface GlobalSearchResultsProps {
  query: string
  enabled: boolean
}

export const GlobalSearchResults = ({ query, enabled }: GlobalSearchResultsProps) => {
  const { t } = useTranslation('common')
  const { t: tAdmin } = useTranslation('admin')
  const navigate = useNavigate()
  const setGlobalSearchOpen = useUIStore((state) => state.setGlobalSearchOpen)
  const openSettings = useSettingsStore((state) => state.openSettings)

  const trimmed = query.trim().toLowerCase()
  const hasQuery = trimmed.length > 0

  const categoriesQuery = useQuery({
    queryKey: ['categoriesSearch'],
    queryFn: () => categoryService.getAll(0, 1000),
    enabled: enabled && hasQuery,
  })
  const modifierGroupsQuery = useQuery({
    queryKey: ['modifierGroups'],
    queryFn: modifierGroupService.getAll,
    enabled: enabled && hasQuery,
  })
  const inventoryQuery = useQuery({
    queryKey: ['inventoryItems'],
    queryFn: inventoryService.getAll,
    enabled: enabled && hasQuery,
  })
  const staffQuery = useQuery({
    queryKey: ['staff'],
    queryFn: staffService.getAll,
    enabled: enabled && hasQuery,
  })

  const goTo = (path: string, after?: () => void) => {
    after?.()
    navigate(path)
    setGlobalSearchOpen(false)
  }

  const categoryResults: ResultEntry[] = useMemo(
    () =>
      (categoriesQuery.data?.content ?? [])
        .filter((category) => (category.name ?? '').toLowerCase().includes(trimmed))
        .map((category) => ({
          id: `category-${category.id}`,
          label: category.name ?? '',
          onSelect: () => goTo('/admin/inventory/categories'),
        })),
    [categoriesQuery.data, trimmed]
  )

  const modifierResults: ResultEntry[] = useMemo(
    () =>
      (modifierGroupsQuery.data ?? [])
        .filter((group) => (group.name ?? '').toLowerCase().includes(trimmed))
        .map((group) => ({
          id: `modifier-${group.id}`,
          label: group.name ?? '',
          onSelect: () => goTo('/admin/inventory/modifiers'),
        })),
    [modifierGroupsQuery.data, trimmed]
  )

  const inventoryResults: ResultEntry[] = useMemo(
    () =>
      (inventoryQuery.data ?? [])
        .filter((item) => (item.menuItemName ?? '').toLowerCase().includes(trimmed))
        .map((item) => ({
          id: `inventory-${item.id}`,
          label: item.menuItemName ?? '',
          onSelect: () => goTo('/admin/inventory'),
        })),
    [inventoryQuery.data, trimmed]
  )

  const staffResults: ResultEntry[] = useMemo(
    () =>
      (staffQuery.data ?? [])
        .filter((member) => (member.name ?? '').toLowerCase().includes(trimmed))
        .map((member) => ({
          id: `staff-${member.id}`,
          label: member.name ?? '',
          onSelect: () => goTo('/admin/employees'),
        })),
    [staffQuery.data, trimmed]
  )

  const sectionResults: ResultEntry[] = []
  if (t('navAnalytics').toLowerCase().includes(trimmed)) {
    sectionResults.push({ id: 'section-analytics', label: t('navAnalytics'), onSelect: () => goTo('/admin/analytics') })
  }
  if (t('navCash').toLowerCase().includes(trimmed)) {
    sectionResults.push({ id: 'section-cash', label: t('navCash'), onSelect: () => goTo('/admin/cash-register') })
  }
  ;(Object.keys(SETTINGS_TAB_LABEL_KEYS) as Exclude<SettingsType, null>[]).forEach((tab) => {
    const label = tAdmin(SETTINGS_TAB_LABEL_KEYS[tab])
    if (label.toLowerCase().includes(trimmed)) {
      sectionResults.push({
        id: `section-settings-${tab}`,
        label,
        onSelect: () => goTo('/admin/settings', () => openSettings(tab)),
      })
    }
  })

  const groups = [
    { key: 'categories', label: t('globalSearchGroupCategories'), results: categoryResults },
    { key: 'modifiers', label: t('globalSearchGroupModifiers'), results: modifierResults },
    { key: 'inventory', label: t('navInventory'), results: inventoryResults },
    { key: 'staff', label: t('navStaff'), results: staffResults },
    { key: 'sections', label: t('globalSearchGroupSections'), results: sectionResults },
  ].filter((group) => group.results.length > 0)

  const isLoading =
    hasQuery &&
    (categoriesQuery.isLoading || modifierGroupsQuery.isLoading || inventoryQuery.isLoading || staffQuery.isLoading)

  if (!hasQuery) {
    return <p className="p-4 text-sm text-zinc-500">{t('globalSearchHint')}</p>
  }

  if (isLoading) {
    return <p className="p-4 text-sm text-zinc-500">{t('globalSearchLoading')}</p>
  }

  if (groups.length === 0) {
    return <p className="p-4 text-sm text-zinc-500">{t('globalSearchNoResults')}</p>
  }

  return (
    <div className="max-h-96 overflow-y-auto py-2">
      {groups.map((group) => (
        <div key={group.key} className="px-2 py-1">
          <p className="px-2 py-1 text-xs font-medium tracking-wide text-zinc-400 uppercase">
            {group.label}
          </p>
          {group.results.map((result) => (
            <button
              key={result.id}
              type="button"
              onClick={result.onSelect}
              className="block w-full truncate rounded-md px-2 py-1.5 text-left text-sm text-zinc-700
                hover:bg-zinc-100 cursor-pointer"
            >
              {result.label}
            </button>
          ))}
        </div>
      ))}
    </div>
  )
}
