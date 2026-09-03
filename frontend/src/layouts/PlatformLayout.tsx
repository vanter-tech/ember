import { Link, Outlet, useNavigate } from 'react-router-dom'
import { usePlatformAuthStore } from '@/store/platformAuthStore'
import { Button } from '@/components/ui/button'

export const PlatformLayout = () => {
  const navigate = useNavigate()
  const { name, email, logout } = usePlatformAuthStore()

  const handleLogout = () => {
    logout()
    navigate('/console/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-zinc-50/50">
      <header className="flex items-center justify-between border-b bg-white px-6 py-4">
        <span className="text-lg font-semibold text-[#920703]">Ember Platform Console</span>
        <div className="flex items-center gap-4 text-sm text-muted-foreground">
          <span>{name ?? email}</span>
          <Link to="/console/password" className="hover:underline">
            Cambiar contraseña
          </Link>
          <Button variant="outline" size="sm" onClick={handleLogout}>
            Log out
          </Button>
        </div>
      </header>
      <main className="p-6">
        <Outlet />
      </main>
    </div>
  )
}
