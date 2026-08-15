import { usePlatformAuthStore } from '@/store/platformAuthStore'

export default function ConsoleDashboard() {
  const { name } = usePlatformAuthStore()

  return (
    <div className="flex flex-col gap-2">
      <h1 className="text-2xl font-semibold">Welcome{name ? `, ${name}` : ''}</h1>
      <p className="text-gray-600">Restaurant management lands in EMB-PC-12+.</p>
    </div>
  )
}
