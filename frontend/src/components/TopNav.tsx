import { useEffect, useState } from 'react'
import { useLocation, useMatch } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { Search, Plus, Clock, HelpCircle } from 'lucide-react'
import { useUIStore, type ModalType } from '@/store/uiStore'
import { settingStore } from '@/store/settingStore'
import { LanguageSwitcher } from '@/components/LanguageSwitcher'
import { useTranslation } from '@/lib/i18n'
import { Popover, PopoverAnchor, PopoverContent } from '@/components/ui/popover'
import { GlobalSearchResults } from '@/components/GlobalSearchResults'

export const TopNav = () => {
  let actionType: ModalType | null = null

  const role = useAuthStore((state) => state.role)
  const { settings } = settingStore()
  const {
    openModal,
    searchTerm,
    setSearchTerm,
    isGlobalSearchOpen,
    setGlobalSearchOpen,
    activeInventoryHubSection,
    activeTourSection,
    requestTour,
  } = useUIStore()
  const { t, locale } = useTranslation('common')

  const location = useLocation()
  const path = location.pathname
  const isWaiterRoute = path.includes('/waiter')
  const isAnalyticsRoute = path.includes('/admin/analytics')
  const isSettingsRoute = path.includes('/admin/settings')

  useEffect(() => {
    setSearchTerm('')
  }, [path, setSearchTerm])

  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    if (!isWaiterRoute) return
    const interval = setInterval(() => setNow(new Date()), 60_000)
    return () => clearInterval(interval)
  }, [isWaiterRoute])

  const allowedWaiterPaths = ['/waiter/tables'] // Rutas por agregar ya que no tengo bien definidas las views de los meseros.

  const isMenuItemRoute = useMatch('/admin/inventory/categories/:id/items')

  const isMenuItemsRouteId = isMenuItemRoute?.params.id

  if (!role || role === 'CUSTOMER') return null
  if (role === 'WAITER' && !allowedWaiterPaths.includes(path)) return null

  let buttonText = t('defaultButtonText')
  let searchPlaceholder = t('defaultSearchPlaceholder')

  if (isMenuItemRoute) {
    buttonText = t('newMenuItemButton')
    searchPlaceholder = t('searchMenuItemsPlaceholder')
    actionType = 'CREATE_ITEMS'
  } else if (activeInventoryHubSection === 'categories') {
    buttonText = t('newCategoryButton')
    searchPlaceholder = t('searchCategoriesPlaceholder')
    actionType = 'CREATE_CATEGORY'
  } else if (activeInventoryHubSection === 'modifiers') {
    buttonText = t('newModifierGroupButton')
    actionType = 'CREATE_MODIFIER_GROUP'
  } else if (activeInventoryHubSection === 'stock') {
    buttonText = t('newInventoryItemButton')
    actionType = 'CREATE_INVENTORY_ITEM'
  } else if (path.includes('/admin/employees')) {
    buttonText = t('newEmployeeButton')
    searchPlaceholder = t('searchEmployeesPlaceholder')
    actionType = 'CREATE_STAFF'
  } else if (isWaiterRoute) {
    searchPlaceholder = t('searchTablesPlaceholder')
  }

  return (
    <header
      className="w-full bg-white rounded-2xl shadows-sm border border-zinc-100 px-6
        py-3 flex items-center justify-between mb-6"
    >
      <div className="flex items-center gap-3">
        <h1
          className="text-3xl font-bold
                text-[#8c1717] tracking-tight"
        >
          {settings?.branding?.businessName || 'Ember'}
        </h1>
        <LanguageSwitcher />
        {activeTourSection && (
          <button
            type="button"
            aria-label={t('replayTourButtonLabel')}
            title={t('replayTourButtonLabel')}
            onClick={() => requestTour(activeTourSection)}
            className="grid place-items-center h-8 w-8 rounded-full text-zinc-500
              hover:bg-zinc-100 hover:text-zinc-700 transition-colors cursor-pointer"
          >
            <HelpCircle size={18} strokeWidth={2} />
          </button>
        )}
      </div>
      <div className="flex-1 max-w-md mx-8">
        <Popover open={role === 'ADMIN' && isGlobalSearchOpen} onOpenChange={setGlobalSearchOpen}>
          <PopoverAnchor asChild>
            <div
              className="relative flex items-center
                    w-full h-10 rounded-full bg-zinc-100/80
                    focus-within:bg-white focus-withi:ring-2
                    focus-within:ring-[8c1717]/20 transition-all"
            >
              <div
                className="grid place-items-center h-full
                        w-12 text-zinc-400"
              >
                <Search size={18} strokeWidth={2} />
              </div>
              <input
                className="peer h-full w-full outline-none
                        text-sm text-zinc-700 bg-transparent pr-2"
                type="text"
                placeholder={searchPlaceholder}
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                onClick={() => role === 'ADMIN' && setGlobalSearchOpen(true)}
              />
            </div>
          </PopoverAnchor>
          {role === 'ADMIN' && (
            <PopoverContent className="w-[28rem] max-w-[calc(100vw-3rem)]">
              <GlobalSearchResults query={searchTerm} enabled={isGlobalSearchOpen} />
            </PopoverContent>
          )}
        </Popover>
      </div>

      {isWaiterRoute ? (
        <div
          className="flex items-center gap-2 rounded-full bg-zinc-100
            px-5 py-2.5 text-sm font-medium text-zinc-700"
        >
          <Clock size={18} strokeWidth={2} />
          {now.toLocaleTimeString(locale === 'en' ? 'en-US' : 'es-MX', { hour: '2-digit', minute: '2-digit' })}
        </div>
      ) : isAnalyticsRoute || isSettingsRoute ? null : (
        <button
          id="topnav-create-button"
          className="flex items-center gap-2
              bg-[#8c1717] hover:bg-[#7a1414] text-white
              px-5 py-2.5 rounded-full text-sm font-medium
              transition-colors shadows-sm cursor-pointer"
          onClick={(e) => {
            e.preventDefault()
            e.stopPropagation()
            openModal(actionType, { id: Number(isMenuItemsRouteId) })
          }}
        >
          <Plus size={18} strokeWidth={2} />
          {buttonText}
        </button>
      )}
    </header>
  )
}
