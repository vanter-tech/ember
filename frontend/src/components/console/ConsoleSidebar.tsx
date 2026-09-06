import { NavLink } from 'react-router-dom'
import { LayoutDashboard, Store } from 'lucide-react'
import { usePlatformAuthStore } from '@/store/platformAuthStore'

const NAV = [
  { to: '/console', end: true, label: 'Dashboard', icon: LayoutDashboard },
  { to: '/console/restaurants', end: false, label: 'Restaurantes', icon: Store },
]

export function ConsoleSidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { name, email } = usePlatformAuthStore()

  return (
    <div className="flex h-full w-60 flex-col border-r border-zinc-200 bg-white">
      <div className="flex flex-col gap-1 border-b border-zinc-100 px-5 py-4">
        <span className="text-lg font-bold text-[#8c1717]">Ember Console</span>
        <span className="truncate text-xs text-zinc-500">{name ?? email}</span>
      </div>
      <nav className="flex flex-1 flex-col gap-1 p-3">
        {NAV.map(({ to, end, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            onClick={onNavigate}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors ${
                isActive
                  ? 'bg-[#8c1717]/10 font-medium text-[#8c1717]'
                  : 'text-zinc-600 hover:bg-zinc-100'
              }`
            }
          >
            <Icon size={18} strokeWidth={2} />
            {label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
