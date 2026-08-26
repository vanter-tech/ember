import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import {
  LayoutDashboard,
  BarChart3,
  Settings,
  LogOut,
  User,
  Home,
  Menu,
  ChefHat,
  Users,
  Banknote,
  BookOpen,
  Warehouse,
} from 'lucide-react'
import { useSessionStore } from '@/store/sessionStore'
import { useTranslation } from '@/lib/i18n'

export const FloatingNav = () => {
  const { t } = useTranslation('common')
  const role = useAuthStore((state) => state.role)
  const logout = useAuthStore((state) => state.logout)
  const navigate = useNavigate()
  const location = useLocation()
  const { userId } = useAuthStore()
  const { participants } = useSessionStore()

  const amiIn = participants?.find((data) => data.userId === userId)

  if (!role) return null

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const isActive = (path: string) => location.pathname.includes(path)
  const navItemClass = (path: string) => `
    flex items-center justify-center w-12 h-12 rounded-full transition-all duration-300
    ${
      isActive(path)
        ? 'bg-[#920703] text-red-100 shadow-md scale-110'
        : 'text-zinc-500 hover:bg-zinc-100 hover:text-zinc-800'
    }`

  return (
    <nav
      className="fixed bottom-4 sm:bottom-8 inset-x-0 mx-auto w-max max-w-[92vw] bg-white dark:bg-zinc-900 shadow-2xl rounded-full
        px-2 sm:px-4 py-2 flex items-center gap-1 sm:gap-2 border border-zinc-200 dark:border-zinc-800 z-50
        overflow-x-auto no-scrollbar"
    >
      {(role === 'WAITER' || role === 'ADMIN') && (
        <Link
          to="/waiter/tables"
          className={navItemClass('/waiter/tables')}
          title={t('navTables')}
        >
          <LayoutDashboard strokeWidth={1.5} size={24} />
        </Link>
      )}

      {(role === 'KITCHEN' || role === 'ADMIN') && (
        <Link
          to="/kitchen/orders"
          className={navItemClass('/kitchen/orders')}
          title={t('navKitchen')}
        >
          <ChefHat strokeWidth={1.5} size={24} />
        </Link>
      )}

      {role === 'WAITER' && (
        <Link
          to="/waiter/cash-register"
          className={navItemClass('/waiter/cash-register')}
          title={t('navCash')}
        >
          <Banknote strokeWidth={1.5} size={24} />
        </Link>
      )}

      {role === 'ADMIN' && (
        <>
          <Link
            to="/admin/inventory"
            className={navItemClass('/admin/inventory')}
            title={t('navInventory')}
          >
            <Warehouse strokeWidth={1.5} size={24} />
          </Link>
          <Link
            to="/admin/analytics"
            className={navItemClass('/admin/analytics')}
            title={t('navAnalytics')}
          >
            <BarChart3 strokeWidth={1.5} size={24} />
          </Link>
          <Link
            to="/admin/employees"
            className={navItemClass('/admin/employees')}
            title={t('navStaff')}
          >
            <Users strokeWidth={1.5} size={24} />
          </Link>
          <Link
            to="/admin/cash-register"
            className={navItemClass('/admin/cash-register')}
            title={t('navCash')}
          >
            <BookOpen strokeWidth={1.5} size={24} />
          </Link>
          <div className="w-px h-8 bg-zinc-200 dark:bg-zinc-700 mx-2"></div>
          <Link
            to="/admin/settings"
            className={navItemClass('/admin/settings')}
            title={t('navSettings')}
          >
            <Settings strokeWidth={1.5} size={24} />
          </Link>
        </>
      )}

      {role === 'CUSTOMER' &&
        (amiIn ? (
          <Link
            to="/customer/menu"
            className={navItemClass('/customer/menu')}
            title={t('navHome')}
          >
            <Menu strokeWidth={1.5} size={24} />
          </Link>
        ) : (
          ''
        ))}
      {role === 'CUSTOMER' && (
        <Link
          to="/customer/home"
          className={navItemClass('/customer/home')}
          title={t('navHome')}
        >
          <Home strokeWidth={1.5} size={24} />
        </Link>
      )}

      <div
        className="flex items-center gap-4 pl-2 border-l
            border-zinc-200 dark:border-zinc-700"
      >
        <div
          className="w-8 h-8 rounded-full bg-zinc-100 dark:bg-zinc-800
                flex items-center justify-center border border-zinc-200 dark:border-zinc-700"
        >
          <User strokeWidth={1.5} size={18} className="text-zinc-500" />
        </div>
        <button
          onClick={handleLogout}
          className="text-[#920703]
                hover:text-red-600 transition-colors cursor-pointer"
          title={t('navLogout')}
        >
          <LogOut strokeWidth={1.5} size={24} />
        </button>
      </div>
    </nav>
  )
}
