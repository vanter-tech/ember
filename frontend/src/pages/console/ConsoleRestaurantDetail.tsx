import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  platformAuditLogService,
  platformRestaurantService,
  type PlatformRestaurantDetail,
} from '@/lib/platformApi'
import { PaginationControls } from '@/components/PaginationControls'
import { Button } from '@/components/ui/button'

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

const nextStatus = (
  status: PlatformRestaurantDetail['status']
): PlatformRestaurantDetail['status'] => (status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED')

export default function ConsoleRestaurantDetail() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
  const [auditPage, setAuditPage] = useState(0)

  const {
    data: restaurant,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['platformRestaurant', id],
    queryFn: () => platformRestaurantService.getById(id!),
    enabled: !!id,
  })

  const { data: auditLogPage, isLoading: isAuditLoading } = useQuery({
    queryKey: ['platformAuditLog', id, auditPage],
    queryFn: () => platformAuditLogService.getByRestaurant(id!, auditPage),
    enabled: !!id,
  })
  const auditLog = auditLogPage?.content ?? []

  const toggleStatus = useMutation({
    mutationFn: (status: PlatformRestaurantDetail['status']) =>
      platformRestaurantService.updateStatus(id!, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['platformRestaurant', id] })
      queryClient.invalidateQueries({ queryKey: ['platformAuditLog', id] })
      queryClient.invalidateQueries({ queryKey: ['platformRestaurants'] })
    },
  })

  const issueHubLicense = useMutation({
    mutationFn: () => platformRestaurantService.issueHubLicense(id!),
    onSuccess: (licenseKeyContents) => {
      const blob = new Blob([licenseKeyContents], { type: 'text/plain' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = 'license.key'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(url)
    },
  })

  if (isLoading) {
    return <div className="p-6 text-zinc-500">Cargando restaurante...</div>
  }

  if (isError || !restaurant) {
    return <div className="p-6 text-red-500">Error al cargar el restaurante.</div>
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-1">
          <Link to="/console/restaurants" className="text-sm text-blue-600 hover:underline">
            &larr; Restaurantes
          </Link>
          <h1 className="text-2xl font-semibold">{restaurant.name}</h1>
        </div>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            disabled={issueHubLicense.isPending}
            onClick={() => issueHubLicense.mutate()}
          >
            {issueHubLicense.isPending ? 'Emitiendo...' : 'Emitir licencia Hub'}
          </Button>
          <Button
            type="button"
            disabled={toggleStatus.isPending}
            onClick={() => toggleStatus.mutate(nextStatus(restaurant.status))}
          >
            {restaurant.status === 'SUSPENDED' ? 'Reactivar' : 'Suspender'}
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4 rounded-lg border border-zinc-200 p-4 text-sm">
        <div>
          <div className="text-zinc-500">Slug</div>
          <div className="font-medium text-zinc-800">{restaurant.slug}</div>
        </div>
        <div>
          <div className="text-zinc-500">Plan</div>
          <div className="font-medium text-zinc-800">{restaurant.plan}</div>
        </div>
        <div>
          <div className="text-zinc-500">Estado</div>
          <span
            className={`inline-block rounded-full px-2 py-1 text-xs font-medium ${statusBadgeClass(restaurant.status)}`}
          >
            {restaurant.status}
          </span>
        </div>
        <div>
          <div className="text-zinc-500">Creado</div>
          <div className="font-medium text-zinc-800">
            {new Date(restaurant.createdAt).toLocaleDateString()}
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <h2 className="text-lg font-semibold">Administradores</h2>
        <div className="overflow-x-auto rounded-lg border border-zinc-200">
          <table className="min-w-full divide-y divide-zinc-200 text-sm">
            <thead className="bg-zinc-50">
              <tr>
                <th className="px-4 py-2 text-left font-medium text-zinc-500">Nombre</th>
                <th className="px-4 py-2 text-left font-medium text-zinc-500">Email</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-100">
              {restaurant.admins.map((admin) => (
                <tr key={admin.id}>
                  <td className="px-4 py-2 font-medium text-zinc-800">{admin.name}</td>
                  <td className="px-4 py-2 text-zinc-500">{admin.email}</td>
                </tr>
              ))}
              {restaurant.admins.length === 0 && (
                <tr>
                  <td colSpan={2} className="px-4 py-6 text-center text-zinc-400">
                    Sin administradores.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <h2 className="text-lg font-semibold">Historial de auditoría</h2>
        {isAuditLoading ? (
          <div className="text-zinc-500">Cargando historial...</div>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-zinc-200">
            <table className="min-w-full divide-y divide-zinc-200 text-sm">
              <thead className="bg-zinc-50">
                <tr>
                  <th className="px-4 py-2 text-left font-medium text-zinc-500">Fecha</th>
                  <th className="px-4 py-2 text-left font-medium text-zinc-500">Operador</th>
                  <th className="px-4 py-2 text-left font-medium text-zinc-500">Acción</th>
                  <th className="px-4 py-2 text-left font-medium text-zinc-500">Anterior</th>
                  <th className="px-4 py-2 text-left font-medium text-zinc-500">Nuevo</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100">
                {auditLog.map((entry) => (
                  <tr key={entry.id}>
                    <td className="px-4 py-2 text-zinc-500">
                      {new Date(entry.createdAt).toLocaleString()}
                    </td>
                    <td className="px-4 py-2 text-zinc-500">{entry.operatorEmail}</td>
                    <td className="px-4 py-2 font-medium text-zinc-800">{entry.action}</td>
                    <td className="px-4 py-2 text-zinc-500">{entry.oldValue ?? '-'}</td>
                    <td className="px-4 py-2 text-zinc-500">{entry.newValue ?? '-'}</td>
                  </tr>
                ))}
                {auditLog.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-4 py-6 text-center text-zinc-400">
                      Sin actividad registrada.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <PaginationControls
        page={auditPage}
        totalPages={auditLogPage?.totalPages ?? 0}
        onPageChange={setAuditPage}
      />
    </div>
  )
}
