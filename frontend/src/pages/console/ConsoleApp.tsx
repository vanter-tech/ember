import { Routes, Route, Navigate } from 'react-router-dom'
import { PlatformProtectedRoute } from '@/components/PlatformProtectedRoute'
import { PlatformLayout } from '@/layouts/PlatformLayout'
import ConsoleLogin from './ConsoleLogin'
import ConsoleDashboard from './ConsoleDashboard'
import ConsoleRestaurants from './ConsoleRestaurants'
import ConsoleRestaurantDetail from './ConsoleRestaurantDetail'
import ConsoleRestaurantCreate from './ConsoleRestaurantCreate'
import ConsolePasswordChange from './ConsolePasswordChange'

export default function ConsoleApp() {
  return (
    <Routes>
      <Route path="login" element={<ConsoleLogin />} />
      <Route element={<PlatformProtectedRoute />}>
        <Route element={<PlatformLayout />}>
          <Route index element={<ConsoleDashboard />} />
          <Route path="restaurants" element={<ConsoleRestaurants />} />
          <Route path="restaurants/new" element={<ConsoleRestaurantCreate />} />
          <Route path="restaurants/:id" element={<ConsoleRestaurantDetail />} />
          <Route path="password" element={<ConsolePasswordChange />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/console/login" replace />} />
    </Routes>
  )
}
