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
    default:
      return 'bg-zinc-100 text-zinc-600'
  }
}

export default function ConsoleRestaurants() {
  const [page, setPage] = useState(0)

  const {
    data: restaurantsPage,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['platformRestaurants', page],
    queryFn: () => platformRestaurantService.getAll(page),
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
      <h1 className="text-2xl font-semibold">Restaurantes</h1>

      <div className="overflow-x-auto rounded-lg border border-zinc-200">
        <table className="min-w-full divide-y divide-zinc-200 text-sm">
          <thead className="bg-zinc-50">
            <tr>
              <th className="px-4 py-2 text-left font-medium text-zinc-500">Nombre</th>
              <th className="px-4 py-2 text-left font-medium text-zinc-500">Slug</th>
              <th className="px-4 py-2 text-left font-medium text-zinc-500">Plan</th>
              <th className="px-4 py-2 text-left font-medium text-zinc-500">Estado</th>
              <th className="px-4 py-2 text-left font-medium text-zinc-500">Creado</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-100">
            {restaurants.map((restaurant) => (
              <tr key={restaurant.id}>
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
                <td className="px-4 py-2 text-zinc-500">
                  {new Date(restaurant.createdAt).toLocaleDateString()}
                </td>
              </tr>
            ))}
            {restaurants.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-zinc-400">
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
