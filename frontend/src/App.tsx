import { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from '@/queryClient';
import { useAuthStore } from './store/authStore'
import { ProtectedRoute } from './components/ProtectedRoute'
import { NotFound } from './components/NotFound'
import { Login } from './pages/auth/Login'
import { Register } from './pages/auth/Register'
import { Toaster, ToastBar } from 'react-hot-toast'
import { AdminLayout } from './layouts/AdminLayout'
import { InventoryHub } from './pages/admin/inventoryHub/InventoryHub'
import { Category } from './pages/admin/Category'
import { ListMenuItem } from './pages/admin/ListMenuItem'
import { ModifierGroups } from './pages/admin/ModifierGroups'
import { Inventory } from './pages/admin/Inventory'
import { Tables } from '@/pages/waiter/Tables'
import { CashRegister as WaiterCashRegister } from '@/pages/waiter/cashRegister/CashRegister'
import { WaiterLayout } from '@/layouts/WaiterLayout'
import { Settings } from './pages/admin/Settings'
import { Analytics } from './pages/admin/analytics/Analytics'
import { Staff } from './pages/admin/staff/Staff'
import { CashRegister as AdminCashRegister } from '@/pages/admin/cashRegister/CashRegister'
import { TableInformation } from './pages/waiter/TableInformation'
import { OrdersDisplays } from './pages/kitchen/OrdersDisplay'
import { KitchenLayout } from './layouts/KitchenLayout'
import { TenantLanding } from './pages/public/TenantLanding'
import { TenantSuspendedModal } from './components/TenantSuspendedModal'
import { isHubBuild } from '@/lib/isHubBuild'

// The whole customer-facing subsystem (collaborative cart, join-table) is dead weight in the
// Hub build — the waiter drives the table there. Lazy so it is never in the Hub bundle's graph.
const CustomerLayout = lazy(() => import('./layouts/CustomerLayout').then(m => ({ default: m.CustomerLayout })))
const Home = lazy(() => import('./pages/customer/Home').then(m => ({ default: m.Home })))
const MenuJoin = lazy(() => import('./pages/customer/MenuJoin').then(m => ({ default: m.MenuJoin })))
const JoinByCode = lazy(() => import('./pages/customer/JoinByCode').then(m => ({ default: m.JoinByCode })))
const Menu = lazy(() => import('./pages/customer/Menu').then(m => ({ default: m.Menu })))
const ComandaView = lazy(() => import('./pages/customer/ComandaView').then(m => ({ default: m.ComandaView })))
const Bill = lazy(() => import('./pages/customer/Bill').then(m => ({ default: m.Bill })))

// Code-split: the platform console is a separate audience (operators, not tenant users) and
// must never land in the tenant app's main bundle.
const ConsoleApp = lazy(() => import('./pages/console/ConsoleApp'))

const RoleRedirect = () => {
  const { role } = useAuthStore()

  if (!role) return <Navigate to="/login" replace />
  // The Hub build has no customer surface — a stale CUSTOMER token lands back on /login.
  if (isHubBuild() && role === 'CUSTOMER') return <Navigate to="/login" replace />
  if (role === 'ADMIN') return <Navigate to="/admin" replace />
  if (role === 'CUSTOMER') return <Navigate to="/customer" replace />
  if (role === 'WAITER') return <Navigate to="/waiter" replace />
  if (role === 'KITCHEN') return <Navigate to="/kitchen" replace />

  return <Navigate to="/login" replace />
}

// Vite's BASE_URL reflects `vite build --base=/app/` (the Hub-bundled build only, see
// ember-hub/build-frontend.ps1) — normal cloud/dev builds have BASE_URL "/", where a trailing
// slash-stripped basename is falsy and BrowserRouter falls back to its own root default.
const routerBasename = import.meta.env.BASE_URL.replace(/\/$/, '') || undefined

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
    <BrowserRouter basename={routerBasename}>
      <Toaster
        position="top-right"
        toastOptions={{
          duration: 4000,
          style: {
            background: 'var(--card)',
            color: 'var(--card-foreground)',
            border: '1px solid var(--border)',
            borderRadius: 'var(--radius-lg)',
            padding: '12px 16px',
            fontSize: '14px',
            maxWidth: '420px',
            boxShadow: '0 4px 16px oklch(0 0 0 / 12%)',
          },
          success: {
            iconTheme: { primary: '#16a34a', secondary: 'var(--card)' },
            style: { borderLeft: '4px solid #16a34a' },
          },
          error: {
            iconTheme: { primary: 'var(--destructive)', secondary: 'var(--card)' },
            style: { borderLeft: '4px solid var(--destructive)' },
          },
        }}
      >
        {(t) => (
          <ToastBar
            toast={t}
            style={{
              animation: t.visible
                ? 'toast-slide-in 0.35s cubic-bezier(.21,1.02,.73,1) forwards'
                : 'toast-slide-out 0.4s cubic-bezier(.06,.71,.55,1) forwards',
            }}
          />
        )}
      </Toaster>
      <TenantSuspendedModal />
      <Routes>
        <Route path="/" element={<RoleRedirect />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        {/* Public: the table QR is scanned with the phone camera, so the visitor may not be
            logged in yet. MenuJoin parks the token and routes through /login if needed. /join is
            the no-QR, no-account path — the diner types the 5-character table code straight in.
            Both are absent from the Hub build — there is no customer join flow there. */}
        {!isHubBuild() && (
          <Route path="/menu/join" element={<Suspense fallback={null}><MenuJoin /></Suspense>} />
        )}
        {!isHubBuild() && (
          <Route path="/join" element={<Suspense fallback={null}><JoinByCode /></Suspense>} />
        )}
        <Route path="/t/:slug" element={<TenantLanding />} />

        <Route
          path="/console/*"
          element={
            <Suspense fallback={null}>
              <ConsoleApp />
            </Suspense>
          }
        />

        {!isHubBuild() && (
          <Route element={<ProtectedRoute allowedRoles={['CUSTOMER']} />}>
            <Route path='/customer' element={<Suspense fallback={null}><CustomerLayout/></Suspense>}>
              <Route index element={<Navigate to="home" replace />} />
              <Route path='home' element={<Suspense fallback={null}><Home/></Suspense>}/>
              <Route path='menu' element={<Suspense fallback={null}><Menu/></Suspense>}/>
              <Route path="menu/:id/comanda" element={<Suspense fallback={null}><ComandaView/></Suspense>} />
              <Route path="menu/:id/bill" element={<Suspense fallback={null}><Bill/></Suspense>} />
            </Route>
          </Route>
        )}

        <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
          <Route path="/admin" element={<AdminLayout />}>
            {/* QA_SIMULATION_REPORT.md E-11: bare /admin (and /waiter, /kitchen below) rendered
                a blank screen with no feedback after login — no <Route index> matched, so
                AdminLayout's <Outlet/> had nothing to render. Land on analytics (the business
                dashboard) rather than the inventory tab. */}
            <Route index element={<Navigate to="analytics" replace />} />
            <Route path="inventory" element={<InventoryHub />}>
              <Route index element={<Inventory />} />
              <Route path="categories" element={<Category />} />
              <Route path="categories/:id/items" element={<ListMenuItem />} />
              <Route path="modifiers" element={<ModifierGroups />} />
            </Route>
            <Route path="settings" element={<Settings />} />
            <Route path="analytics" element={<Analytics />} />
            <Route path="employees" element={<Staff />} />
            <Route path="cash-register" element={<AdminCashRegister />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['WAITER', 'ADMIN']} />}>
          <Route path="/waiter" element={<WaiterLayout />}>
            <Route index element={<Navigate to="tables" replace />} />
            <Route path="tables" element={<Tables />} />
            <Route path="tables/:id" element={<TableInformation />} />
            <Route path="cash-register" element={<WaiterCashRegister />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['KITCHEN', 'ADMIN']} />}>
          <Route path="/kitchen" element={<KitchenLayout/>}>
            <Route index element={<Navigate to="orders" replace />} />
            <Route path='orders' element={<OrdersDisplays/>}/>
          </Route>
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
    </BrowserRouter>
    </QueryClientProvider>
  )
}
