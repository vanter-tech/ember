import { useEffect } from 'react'
import { useLocation, useMatch } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { Search, Plus } from 'lucide-react'
import { useUIStore, type ModalType } from '@/store/uiStore'
import { settingStore } from '@/store/settingStore'

export const TopNav = () => {
  let actionType: ModalType | null = null

  const role = useAuthStore((state) => state.role)
  const { settings } = settingStore()
  const { openModal, searchTerm, setSearchTerm } = useUIStore()

  const location = useLocation()
  const path = location.pathname

  useEffect(() => {
    setSearchTerm('')
  }, [path, setSearchTerm])

  const allowedWaiterPaths = ['/waiter/tables'] // Rutas por agregar ya que no tengo bien definidas las views de los meseros.

  const isMenuItemRoute = useMatch('/admin/categories/:id/items')
  const isCategoryRoute = useMatch('/admin/categories')

  const isMenuItemsRouteId = isMenuItemRoute?.params.id

  if (!role || role === 'CUSTOMER') return null
  if (role === 'WAITER' && !allowedWaiterPaths.includes(path)) return null

  let buttonText = 'Nuevo registro'
  let searchPlaceholder = 'Buscar...'

  if (isMenuItemRoute) {
    buttonText = 'Nuevo platillo'
    searchPlaceholder = 'Buscar platillos...'
    actionType = 'CREATE_ITEMS'
  } else if (isCategoryRoute) {
    buttonText = 'Nueva categoria'
    searchPlaceholder = 'Buscar categorias...'
    actionType = 'CREATE_CATEGORY'
  } else if (path.includes('/admin/employees')) {
    buttonText = 'Nuevo empleado'
    searchPlaceholder = 'Buscar empleados...'
  } else if (path.includes('/waiter')) {
    buttonText = 'Nueva orden'
    searchPlaceholder = 'Buscar mesas...'
  }

  return (
    <header
      className="w-full bg-white rounded-2xl shadows-sm border border-zinc-100 px-6
        py-3 flex items-center justify-between mb-6"
    >
      <div className="flex items-center ">
        <h1
          className="text-3xl font-bold
                text-[#8c1717] tracking-tight"
        >
          {settings?.branding?.businessName || 'Ember'}
        </h1>
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
    </header>
  )
}
