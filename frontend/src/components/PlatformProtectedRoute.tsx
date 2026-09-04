import { Navigate, Outlet } from 'react-router-dom'
import { usePlatformAuthStore } from '../store/platformAuthStore'

export const PlatformProtectedRoute = () => {
  const { token } = usePlatformAuthStore()

  if (!token) {
    return <Navigate to="/console/login" replace />
  }

  return <Outlet />
}
