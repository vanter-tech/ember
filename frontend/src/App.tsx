import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './store/authStore'
import { ProtectedRoute } from './components/ProtectedRoute'
import { NotFound } from './components/NotFound'
import { Login } from './pages/auth/Login'
import { Register } from './pages/auth/Register'
import { Toaster } from 'react-hot-toast'
import { AdminLayout } from './layouts/AdminLayout'
import { Category } from './pages/admin/Category'
import { ListMenuItem } from './pages/admin/ListMenuItem'
import { Tables } from '@/pages/waiter/Tables'
import { WaiterLayout } from '@/layouts/WaiterLayout'
import { Settings } from './pages/admin/Settings'
import { TableInformation } from './pages/waiter/TableInformation'
import { CustomerLayout } from './layouts/CustomerLayout'
import { Home } from './pages/customer/Home'
import { Menu } from './pages/customer/Menu'
import { ComandaView } from './pages/customer/ComandaView'

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
    <BrowserRouter>
      <Toaster />
      <Routes>
        <Route path="/" element={<RoleRedirect />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

        <Route element={<ProtectedRoute allowedRoles={['CUSTOMER']} />}>
          <Route path='/customer' element={<CustomerLayout/>}>
            <Route path='home' element={<Home/>}/>
            <Route path='menu' element={<Menu/>}/>
            <Route path="menu/:id/comanda" element={<ComandaView/>} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
          <Route path="/admin" element={<AdminLayout />}>
            <Route path="categories" element={<Category />} />
            <Route path="categories/:id/items" element={<ListMenuItem />} />
            <Route path="settings" element={<Settings />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['WAITER', 'ADMIN']} />}>
          <Route path="/waiter" element={<WaiterLayout />}>
            <Route path="tables" element={<Tables />} />
            <Route path="tables/:id" element={<TableInformation />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['KITCHEN', 'ADMIN']} />}>
          <Route
            path="/kitchen/*"
            element={<div className="p-10 text-2xl">Área de Cocina</div>}
          />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
    </BrowserRouter>
  )
}
