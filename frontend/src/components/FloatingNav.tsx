import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import {
  LayoutDashboard,
  Package,
  BarChart3,
  Settings,
  LogOut,
  User,
  Home,
  Menu,
  ChefHat,
} from 'lucide-react'
import { useSessionStore } from '@/store/sessionStore'

export const FloatingNav = () => {
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
      className=" fixed bottom-8 inset-x-0 mx-auto w-max bg-white dark:bg-zinc-900 shadow-2xl rounded-full
        px-4 py-2 flex items-center gap-2 border border-zinc-200 dark:border-zinc-800 z-50"
    >
      {(role === 'WAITER' || role === 'ADMIN') && (
        <Link
          to="/waiter/tables"
          className={navItemClass('/waiter/tables')}
          title="mesas"
        >
          <LayoutDashboard strokeWidth={1.5} size={24} />
        </Link>
      )}

      {(role === 'KITCHEN' || role === 'ADMIN') && (
        <Link
          to="/kitchen/orders"
          className={navItemClass('/kitchen/orders')}
          title="kitchen"
        >
          <ChefHat strokeWidth={1.5} size={24} />
        </Link>
      )}

      {role === 'ADMIN' && (
        <>
          <Link
            to="/admin/categories"
            className={navItemClass('/admin/categories')}
            title="Categorias"
          >
            <Package strokeWidth={1.5} size={24} />
          </Link>
          <Link
            to="/admin/reports"
            className={navItemClass('/admin/reports')}
            title="Reportes"
          >
            <BarChart3 strokeWidth={1.5} size={24} />
          </Link>
          <div className="w-px h-8 bg-zinc-200 dark:bg-zinc-700 mx-2"></div>
          <Link
            to="/admin/settings"
            className={navItemClass('/admin/settings')}
            title="Configuración"
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
            title="Home"
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
          title="Home"
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
          title="Cerrar sesión"
        >
          <LogOut strokeWidth={1.5} size={24} />
        </button>
      </div>
    </nav>
  )
}
