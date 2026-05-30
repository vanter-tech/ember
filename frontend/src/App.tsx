import {BrowserRouter, Routes, Route, Navigate} from 'react-router-dom'
import { useAuthStore } from './store/authStore'
import {ProtectedRoute} from './components/ProtectedRoute'
import {NotFound} from './components/NotFound'
import { Login } from './components/Login'
import { Register } from './components/Register'
import {Toaster} from 'react-hot-toast'

const RoleRedirect = () => {
  const { role } = useAuthStore()

  if (!role) return <Navigate to="/login" replace />
  if(role === 'ADMIN') return <Navigate to="/admin" replace />
  if(role === 'CUSTOMER') return <Navigate to="/customer" replace />
  if(role === 'WAITER') return <Navigate to="/waiter" replace />
  if(role === 'KITCHEN  ') return <Navigate to="/kitchen" replace /> 

  return <Navigate to="/login" replace />
}

export default function App() {
  return (
    <BrowserRouter>
      <Toaster />
      <Routes>

        <Route path="/" element={<RoleRedirect />} />
        <Route path="/login" element = {<Login />} />
        <Route path="/register" element = {<Register />} />
        
        <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
          <Route path="/admin/*" element={<div className="p-10 text-2xl">Panel de Administración</div>} />
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['WAITER']} />}>
          <Route path="/waiter/*" element={<div className="p-10 text-2xl">Terminal de meseros</div>} />
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['KITCHEN', 'ADMIN']} />}>
          <Route path="/kitchen/*" element={<div className="p-10 text-2xl">Área de Cocina</div>} />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
    </BrowserRouter>
  )
}