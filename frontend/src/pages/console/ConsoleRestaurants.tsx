import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { platformRestaurantService } from '@/lib/platformApi'
import { PaginationControls } from '@/components/PaginationControls'

const statusBadgeClass = (status: string) => {
  switch (status) {
    case 'ACTIVE':
      return 'bg-green-100 text-green-700'
    case 'SUSPENDED':
      return 'bg-red-100 text-red-700'
    case 'DELETED':
      return 'bg-zinc-200 text-zinc-500'
    default:
      return 'bg-zinc-100 text-zinc-600'
  }
}

const hubDot = (hubStatus: string) => {
  switch (hubStatus) {
    case 'ONLINE':
      return { cls: 'bg-green-500', label: 'ONLINE' }
    case 'STALE':
      return { cls: 'bg-amber-500', label: 'STALE' }
    case 'OFFLINE':
      return { cls: 'bg-zinc-400', label: 'OFFLINE' }
    default:
      return { cls: 'bg-transparent', label: '—' }
  }
}

export default function ConsoleRestaurants() {
  const [page, setPage] = useState(0)
  const [includeDeleted, setIncludeDeleted] = useState(false)

  const {
    data: restaurantsPage,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['platformRestaurants', page, includeDeleted],
    queryFn: () => platformRestaurantService.getAll(page, 10, includeDeleted),
  })
  const restaurants = restaurantsPage?.content ?? []

  if (isLoading) {
    return <div className="p-6 text-zinc-500">Cargando restaurantes...</div>
  }

  if (isError) {
    return <div className="p-6 text-red-500">Error al cargar los restaurantes.</div>
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Restaurantes</h1>
        <Link
          to="/console/restaurants/new"
          className="rounded-md bg-[#920703] px-3 py-2 text-sm font-medium text-white hover:bg-[#7a0602]"
        >
          Nuevo restaurante
        </Link>
      </div>

      <label className="flex items-center gap-2 text-sm text-zinc-600">
        <input
          type="checkbox"
          checked={includeDeleted}
          onChange={(e) => {
            setIncludeDeleted(e.target.checked)
            setPage(0)
          }}
        />
        Ver eliminados
      </label>

      <div className="overflow-x-auto rounded-lg border border-zinc-200">
        <table className="min-w-full divide-y divide-zinc-200 text-sm">
          <thead className="bg-zinc-50">
            <tr>
              <th className="px-4 py-2 text-left font-medium text-zinc-500">Nombre</th>
              <th className="px-4 py-2 text-left font-medium text-zinc-500">Slug</th>
              <th className="px-4 py-2 text-left font-medium text-zinc-500">Plan</th>
              <th className="px-4 py-2 text-left font-medium text-zinc-500">Estado</th>
              <th className="px-4 py-2 text-left font-medium text-zinc-500">Hub</th>
              <th className="px-4 py-2 text-left font-medium text-zinc-500">Creado</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-100">
            {restaurants.map((restaurant) => {
              const dot = hubDot(restaurant.hubStatus)
              return (
                <tr key={restaurant.id} className={restaurant.status === 'DELETED' ? 'opacity-50' : undefined}>
                  <td className="px-4 py-2 font-medium text-zinc-800">
                    <Link
                      to={`/console/restaurants/${restaurant.id}`}
                      className="hover:underline"
                    >
                      {restaurant.name}
                    </Link>
                  </td>
                  <td className="px-4 py-2 text-zinc-500">{restaurant.slug}</td>
                  <td className="px-4 py-2 text-zinc-500">{restaurant.plan}</td>
                  <td className="px-4 py-2">
                    <span
                      className={`rounded-full px-2 py-1 text-xs font-medium ${statusBadgeClass(restaurant.status)}`}
                    >
                      {restaurant.status}
                    </span>
                  </td>
                  <td className="px-4 py-2">
                    <span className="inline-flex items-center gap-1.5 text-xs text-zinc-600">
                      <span className={`h-2 w-2 rounded-full ${dot.cls}`} />
                      {dot.label}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-zinc-500">
                    {new Date(restaurant.createdAt).toLocaleDateString()}
                  </td>
                </tr>
              )
            })}
            {restaurants.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-zinc-400">
                  Sin restaurantes registrados.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <PaginationControls
        page={page}
        totalPages={restaurantsPage?.totalPages ?? 0}
        onPageChange={setPage}
      />
    </div>
  )
}
