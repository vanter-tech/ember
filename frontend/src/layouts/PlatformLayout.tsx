import { useState } from 'react'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { Menu } from 'lucide-react'
import { usePlatformAuthStore } from '@/store/platformAuthStore'
import { Button } from '@/components/ui/button'
import { ConsoleSidebar } from '@/components/console/ConsoleSidebar'

export const PlatformLayout = () => {
  const navigate = useNavigate()
  const { name, email, logout } = usePlatformAuthStore()
  const [drawerOpen, setDrawerOpen] = useState(false)

  const handleLogout = () => {
    logout()
    navigate('/console/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen bg-zinc-50/50">
      <aside className="hidden md:block">
        <ConsoleSidebar />
      </aside>

      {drawerOpen && (
        <div className="fixed inset-0 z-50 flex md:hidden">
          <ConsoleSidebar onNavigate={() => setDrawerOpen(false)} />
          <div className="flex-1 bg-black/40" onClick={() => setDrawerOpen(false)} />
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-zinc-200 bg-white px-4 py-3 md:px-6">
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="rounded-md p-1.5 text-zinc-600 hover:bg-zinc-100 md:hidden"
              aria-label="Abrir menú"
              onClick={() => setDrawerOpen(true)}
            >
              <Menu size={20} />
            </button>
            <span className="text-sm text-zinc-500 md:hidden">Ember Console</span>
          </div>
          <div className="flex items-center gap-4 text-sm text-zinc-500">
            <span className="hidden sm:inline">{name ?? email}</span>
            <Link to="/console/password" className="hover:underline">
              Cambiar contraseña
            </Link>
            <Button variant="outline" size="sm" onClick={handleLogout}>
              Cerrar sesión
            </Button>
          </div>
        </header>
        <main className="min-w-0 flex-1 p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
