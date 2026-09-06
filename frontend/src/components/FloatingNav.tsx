import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import toast from 'react-hot-toast'
import {
  LayoutDashboard,
  BarChart3,
  Settings,
  LogOut,
  Home,
  Menu,
  ChefHat,
  Users,
  Banknote,
  BookOpen,
  Warehouse,
  DoorOpen,
} from 'lucide-react'
import { SessionTableService } from '@/lib/api'
import { useSessionStore } from '@/store/sessionStore'
import { useTranslation } from '@/lib/i18n'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import type { CashShiftResponse } from '@/lib/api'

export const FloatingNav = () => {
  const { t } = useTranslation('common')
  const role = useAuthStore((state) => state.role)
  const logout = useAuthStore((state) => state.logout)
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const { userId } = useAuthStore()
  const { participants, id: sessionId, clearSession } = useSessionStore()
  const [confirmLogout, setConfirmLogout] = useState(false)
  const [confirmLeave, setConfirmLeave] = useState(false)

  // Right-edge fade hint: shown only while the links row can still scroll right.
  const scrollRef = useRef<HTMLDivElement>(null)
  const [showEndFade, setShowEndFade] = useState(false)

  const updateFade = useCallback(() => {
    const el = scrollRef.current
    if (!el) {
      setShowEndFade(false)
      return
    }
    setShowEndFade(el.scrollLeft + el.clientWidth < el.scrollWidth - 1)
  }, [])

  useEffect(() => {
    updateFade()
    const el = scrollRef.current
    if (!el) return
    el.addEventListener('scroll', updateFade, { passive: true })
    window.addEventListener('resize', updateFade)
    return () => {
      el.removeEventListener('scroll', updateFade)
      window.removeEventListener('resize', updateFade)
    }
  }, [updateFade, role])

  const amiIn = participants?.find((data) => data.userId === userId)

  const leaveMutation = useMutation({
    mutationFn: () => SessionTableService.leaveSession(sessionId!),
    onSuccess: () => {
      clearSession()
      setConfirmLeave(false)
      toast.success(t('leaveTableDoneToast'))
      navigate('/customer/home')
    },
    onError: () => toast.error(t('leaveTableErrorToast')),
  })

  if (!role) return null

  const doLogout = () => {
    logout()
    navigate('/login')
  }

  const handleLogout = () => {
    const shift = queryClient.getQueryData<CashShiftResponse | null>([
      'cashShiftCurrent',
    ])
    if (shift?.overdue) {
      setConfirmLogout(true)
      return
    }
    doLogout()
  }

  const isActive = (path: string) => location.pathname.includes(path)
  const navItemClass = (path: string) => `
    flex items-center justify-center w-12 h-12 shrink-0 rounded-full transition-all duration-300
    ${
      isActive(path)
        ? 'bg-[#920703] text-red-100 shadow-md scale-110'
        : 'text-zinc-500 hover:bg-zinc-100 hover:text-zinc-800'
    }`

  return (
    <>
    <nav
      className="fixed bottom-4 sm:bottom-8 inset-x-0 mx-auto w-max max-w-[92vw] bg-white dark:bg-zinc-900 shadow-2xl rounded-full
        px-2 sm:px-4 py-2 flex items-center gap-1 sm:gap-2 border border-zinc-200 dark:border-zinc-800 z-50"
    >
      <div className="relative flex-1 min-w-0">
        <div
          ref={scrollRef}
          className="flex items-center gap-1 sm:gap-2 overflow-x-auto no-scrollbar"
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
          <div className="w-px h-8 shrink-0 bg-zinc-200 dark:bg-zinc-700 mx-2"></div>
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

      {role === 'CUSTOMER' && amiIn && (
        <button
          onClick={() => setConfirmLeave(true)}
          className="flex items-center justify-center w-12 h-12 shrink-0 rounded-full text-zinc-500
            hover:bg-red-50 hover:text-[#920703] transition-all duration-300 cursor-pointer"
          title={t('leaveTableCta')}
        >
          <DoorOpen strokeWidth={1.5} size={24} />
        </button>
      )}
        </div>
        {showEndFade && (
          <div
            aria-hidden
            className="pointer-events-none absolute inset-y-0 right-0 w-10 bg-gradient-to-l from-[#8c1717]/70 to-transparent"
          />
        )}
      </div>

      <div className="flex shrink-0 items-center pl-2 border-l border-zinc-200 dark:border-zinc-700">
        <button
          onClick={handleLogout}
          className="flex items-center justify-center w-12 h-12 rounded-full text-[#920703]
                hover:bg-zinc-100 hover:text-red-600 transition-colors cursor-pointer"
          title={t('navLogout')}
        >
          <LogOut strokeWidth={1.5} size={24} />
        </button>
      </div>
    </nav>

      <AlertDialog open={confirmLogout} onOpenChange={setConfirmLogout}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {t('logoutCashShiftOverdueTitle')}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {t('logoutCashShiftOverdueBody')}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>
              {t('logoutCashShiftBackButton')}
            </AlertDialogCancel>
            <AlertDialogAction onClick={doLogout}>
              {t('logoutCashShiftConfirmButton')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={confirmLeave} onOpenChange={setConfirmLeave}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('leaveTableConfirmTitle')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('leaveTableConfirmBody')}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('leaveTableCancelButton')}</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => leaveMutation.mutate()}
              disabled={leaveMutation.isPending}
            >
              {t('leaveTableConfirmButton')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
