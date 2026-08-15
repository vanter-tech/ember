import { Link } from 'react-router-dom'
import { usePlatformAuthStore } from '@/store/platformAuthStore'

export default function ConsoleDashboard() {
  const { name } = usePlatformAuthStore()

  return (
    <div className="flex flex-col gap-2">
      <h1 className="text-2xl font-semibold">Welcome{name ? `, ${name}` : ''}</h1>
      <Link to="restaurants" className="text-sm text-blue-600 hover:underline">
        Ver restaurantes
      </Link>
    </div>
  )
}
