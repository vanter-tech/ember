import { Routes, Route } from 'react-router-dom'
import { PlatformProtectedRoute } from '@/components/PlatformProtectedRoute'
import { PlatformLayout } from '@/layouts/PlatformLayout'
import ConsoleLogin from './ConsoleLogin'
import ConsoleDashboard from './ConsoleDashboard'
import ConsoleRestaurants from './ConsoleRestaurants'

export default function ConsoleApp() {
  return (
    <Routes>
      <Route path="login" element={<ConsoleLogin />} />
      <Route element={<PlatformProtectedRoute />}>
        <Route element={<PlatformLayout />}>
          <Route index element={<ConsoleDashboard />} />
          <Route path="restaurants" element={<ConsoleRestaurants />} />
        </Route>
      </Route>
    </Routes>
  )
}
