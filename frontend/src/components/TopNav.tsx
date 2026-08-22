import { useEffect, useState } from 'react'
import { useLocation, useMatch } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { Search, Plus, Clock } from 'lucide-react'
import { useUIStore, type ModalType } from '@/store/uiStore'
import { settingStore } from '@/store/settingStore'
import { LanguageSwitcher } from '@/components/LanguageSwitcher'
import { useTranslation } from '@/lib/i18n'

export const TopNav = () => {
  let actionType: ModalType | null = null

  const role = useAuthStore((state) => state.role)
  const { settings } = settingStore()
  const { openModal, searchTerm, setSearchTerm } = useUIStore()
  const { t, locale } = useTranslation('common')

  const location = useLocation()
  const path = location.pathname
  const isWaiterRoute = path.includes('/waiter')

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

  const isMenuItemRoute = useMatch('/admin/categories/:id/items')
  const isCategoryRoute = useMatch('/admin/categories')

  const isMenuItemsRouteId = isMenuItemRoute?.params.id

  if (!role || role === 'CUSTOMER') return null
  if (role === 'WAITER' && !allowedWaiterPaths.includes(path)) return null

  let buttonText = t('defaultButtonText')
  let searchPlaceholder = t('defaultSearchPlaceholder')

  if (isMenuItemRoute) {
    buttonText = t('newMenuItemButton')
    searchPlaceholder = t('searchMenuItemsPlaceholder')
    actionType = 'CREATE_ITEMS'
  } else if (isCategoryRoute) {
    buttonText = t('newCategoryButton')
    searchPlaceholder = t('searchCategoriesPlaceholder')
    actionType = 'CREATE_CATEGORY'
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
      </div>
      <div className="flex-1 max-w-md mx-8">
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
          />
        </div>
      </div>

      {isWaiterRoute ? (
        <div
          className="flex items-center gap-2 rounded-full bg-zinc-100
            px-5 py-2.5 text-sm font-medium text-zinc-700"
        >
          <Clock size={18} strokeWidth={2} />
          {now.toLocaleTimeString(locale === 'en' ? 'en-US' : 'es-MX', { hour: '2-digit', minute: '2-digit' })}
        </div>
      ) : (
        <button
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
