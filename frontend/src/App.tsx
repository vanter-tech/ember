import { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from '@/queryClient';
import { useAuthStore } from './store/authStore'
import { ProtectedRoute } from './components/ProtectedRoute'
import { NotFound } from './components/NotFound'
import { Login } from './pages/auth/Login'
import { Register } from './pages/auth/Register'
import { Toaster } from 'react-hot-toast'
import { AdminLayout } from './layouts/AdminLayout'
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
import { CustomerLayout } from './layouts/CustomerLayout'
import { Home } from './pages/customer/Home'
import { Menu } from './pages/customer/Menu'
import { ComandaView } from './pages/customer/ComandaView'
import { Bill } from './pages/customer/Bill'
import { OrdersDisplays } from './pages/kitchen/OrdersDisplay'
import { KitchenLayout } from './layouts/KitchenLayout'
import { TenantLanding } from './pages/public/TenantLanding'
import { TenantSuspendedModal } from './components/TenantSuspendedModal'

// Code-split: the platform console is a separate audience (operators, not tenant users) and
// must never land in the tenant app's main bundle.
const ConsoleApp = lazy(() => import('./pages/console/ConsoleApp'))

const RoleRedirect = () => {
  const { role } = useAuthStore()

  if (!role) return <Navigate to="/login" replace />
  if (role === 'ADMIN') return <Navigate to="/admin" replace />
  if (role === 'CUSTOMER') return <Navigate to="/customer" replace />
  if (role === 'WAITER') return <Navigate to="/waiter" replace />
  if (role === 'KITCHEN') return <Navigate to="/kitchen" replace />

  return <Navigate to="/login" replace />
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
    <BrowserRouter>
      <Toaster
        toastOptions={{
          duration: 4000,
          style: {
            background: 'var(--card)',
            color: 'var(--card-foreground)',
            border: '1px solid var(--border)',
            borderRadius: 'var(--radius-lg)',
            padding: '12px 16px',
            fontSize: '14px',
            boxShadow: '0 4px 16px oklch(0 0 0 / 12%)',
          },
          success: {
            iconTheme: { primary: '#16a34a', secondary: 'var(--card)' },
          },
          error: {
            iconTheme: { primary: 'var(--destructive)', secondary: 'var(--card)' },
          },
        }}
      />
      <TenantSuspendedModal />
      <Routes>
        <Route path="/" element={<RoleRedirect />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/t/:slug" element={<TenantLanding />} />

        <Route
          path="/console/*"
          element={
            <Suspense fallback={null}>
              <ConsoleApp />
            </Suspense>
          }
        />

        <Route element={<ProtectedRoute allowedRoles={['CUSTOMER']} />}>
          <Route path='/customer' element={<CustomerLayout/>}>
            <Route path='home' element={<Home/>}/>
            <Route path='menu' element={<Menu/>}/>
            <Route path="menu/:id/comanda" element={<ComandaView/>} />
            <Route path="menu/:id/bill" element={<Bill/>} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
          <Route path="/admin" element={<AdminLayout />}>
            <Route path="categories" element={<Category />} />
            <Route path="categories/:id/items" element={<ListMenuItem />} />
            <Route path="modifier-groups" element={<ModifierGroups />} />
            <Route path="inventory" element={<Inventory />} />
            <Route path="settings" element={<Settings />} />
            <Route path="analytics" element={<Analytics />} />
            <Route path="employees" element={<Staff />} />
            <Route path="cash-register" element={<AdminCashRegister />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['WAITER', 'ADMIN']} />}>
          <Route path="/waiter" element={<WaiterLayout />}>
            <Route path="tables" element={<Tables />} />
            <Route path="tables/:id" element={<TableInformation />} />
            <Route path="cash-register" element={<WaiterCashRegister />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['KITCHEN', 'ADMIN']} />}>
          <Route path="/kitchen" element={<KitchenLayout/>}>
            <Route path='orders' element={<OrdersDisplays/>}/>
          </Route>
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
    </BrowserRouter>
    </QueryClientProvider>
  )
}
