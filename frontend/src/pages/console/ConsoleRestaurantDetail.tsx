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
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { HubBadge } from '@/components/console/HubBadge'
import { ConsolePageHeader } from '@/components/console/ConsolePageHeader'

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

const nextStatus = (
  status: PlatformRestaurantDetail['status']
): PlatformRestaurantDetail['status'] => (status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED')

export default function ConsoleRestaurantDetail() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
  const [auditPage, setAuditPage] = useState(0)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [slugInput, setSlugInput] = useState('')

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

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ['platformRestaurant', id] })
    queryClient.invalidateQueries({ queryKey: ['platformAuditLog', id] })
    queryClient.invalidateQueries({ queryKey: ['platformRestaurants'] })
  }

  const toggleStatus = useMutation({
    mutationFn: (status: PlatformRestaurantDetail['status']) =>
      platformRestaurantService.updateStatus(id!, status),
    onSuccess: invalidateAll,
  })

  const deleteRestaurant = useMutation({
    mutationFn: () => platformRestaurantService.deleteRestaurant(id!),
    onSuccess: () => {
      setShowDeleteConfirm(false)
      setSlugInput('')
      invalidateAll()
    },
  })

  const restoreRestaurant = useMutation({
    mutationFn: () => platformRestaurantService.restoreRestaurant(id!),
    onSuccess: invalidateAll,
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
      <ConsolePageHeader
        title={restaurant.name}
        action={
          <div className="flex items-center gap-2">
            {restaurant.status === 'DELETED' ? (
              <Button
                disabled={restoreRestaurant.isPending}
                onClick={() => restoreRestaurant.mutate()}
              >
                {restoreRestaurant.isPending ? 'Restaurando...' : 'Restaurar restaurante'}
              </Button>
            ) : (
              <>
                <Button
                  variant="outline"
                  disabled={issueHubLicense.isPending}
                  onClick={() => issueHubLicense.mutate()}
                >
                  {issueHubLicense.isPending ? 'Emitiendo...' : 'Emitir licencia Hub'}
                </Button>
                <Button
                  disabled={toggleStatus.isPending}
                  onClick={() => toggleStatus.mutate(nextStatus(restaurant.status))}
                >
                  {restaurant.status === 'SUSPENDED' ? 'Reactivar' : 'Suspender'}
                </Button>
                <Button
                  variant="outline"
                  className="border-red-300 text-red-700 hover:bg-red-50"
                  disabled={restaurant.status !== 'SUSPENDED'}
                  title={
                    restaurant.status !== 'SUSPENDED'
                      ? 'Suspende el restaurante primero'
                      : undefined
                  }
                  onClick={() => setShowDeleteConfirm(true)}
                >
                  Eliminar restaurante
                </Button>
              </>
            )}
          </div>
        }
      />

      <Link to="/console/restaurants" className="text-sm text-[#8c1717] hover:underline">
        &larr; Restaurantes
      </Link>

      <Card className="rounded-2xl">
        <CardHeader>
          <CardTitle className="text-base">Datos</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-4 text-sm">
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
            <Badge className={statusBadgeClass(restaurant.status)}>{restaurant.status}</Badge>
          </div>
          <div>
            <div className="text-zinc-500">Creado</div>
            <div className="font-medium text-zinc-800">
              {new Date(restaurant.createdAt).toLocaleDateString()}
            </div>
          </div>
        </CardContent>
      </Card>

      <Card className="rounded-2xl">
        <CardHeader>
          <CardTitle className="text-base">Hub</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <div className="text-zinc-500">Estado</div>
            <HubBadge status={restaurant.hubStatus} />
          </div>
          <div>
            <div className="text-zinc-500">Activado</div>
            <div className="font-medium text-zinc-800">
              {restaurant.hubActivatedAt
                ? new Date(restaurant.hubActivatedAt).toLocaleString()
                : '—'}
            </div>
          </div>
          <div>
            <div className="text-zinc-500">Último latido</div>
            <div className="font-medium text-zinc-800">
              {restaurant.lastHeartbeatAt
                ? new Date(restaurant.lastHeartbeatAt).toLocaleString()
                : '—'}
            </div>
          </div>
          <div>
            <div className="text-zinc-500">IP</div>
            <div className="font-medium text-zinc-800">{restaurant.lastHeartbeatIp ?? '—'}</div>
          </div>
        </CardContent>
      </Card>

      <Card className="rounded-2xl">
        <CardHeader>
          <CardTitle className="text-base">Administradores</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Nombre</TableHead>
                <TableHead>Email</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {restaurant.admins.map((admin) => (
                <TableRow key={admin.id}>
                  <TableCell className="font-medium text-zinc-800">{admin.name}</TableCell>
                  <TableCell className="text-zinc-500">{admin.email}</TableCell>
                </TableRow>
              ))}
              {restaurant.admins.length === 0 && (
                <TableRow>
                  <TableCell colSpan={2} className="py-6 text-center text-zinc-400">
                    Sin administradores.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card className="rounded-2xl">
        <CardHeader>
          <CardTitle className="text-base">Historial de auditoría</CardTitle>
        </CardHeader>
        <CardContent>
          {isAuditLoading ? (
            <div className="text-zinc-500">Cargando historial...</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Fecha</TableHead>
                  <TableHead>Operador</TableHead>
                  <TableHead>Acción</TableHead>
                  <TableHead>Anterior</TableHead>
                  <TableHead>Nuevo</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {auditLog.map((entry) => (
                  <TableRow key={entry.id}>
                    <TableCell className="text-zinc-500">
                      {new Date(entry.createdAt).toLocaleString()}
                    </TableCell>
                    <TableCell className="text-zinc-500">{entry.operatorEmail}</TableCell>
                    <TableCell>
                      <Badge variant="outline">{entry.action}</Badge>
                    </TableCell>
                    <TableCell className="text-zinc-500">{entry.oldValue ?? '-'}</TableCell>
                    <TableCell className="text-zinc-500">{entry.newValue ?? '-'}</TableCell>
                  </TableRow>
                ))}
                {auditLog.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} className="py-6 text-center text-zinc-400">
                      Sin actividad registrada.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <PaginationControls
        page={auditPage}
        totalPages={auditLogPage?.totalPages ?? 0}
        onPageChange={setAuditPage}
      />

      <Dialog
        open={showDeleteConfirm}
        onOpenChange={(open) => {
          setShowDeleteConfirm(open)
          if (!open) setSlugInput('')
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Eliminar restaurante</DialogTitle>
            <DialogDescription>
              Esto marca <span className="font-medium">{restaurant.name}</span> como eliminado. Se
              puede restaurar después. Escribe <code>{restaurant.slug}</code> para confirmar.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            <Label htmlFor="delete-slug" className="text-sm text-zinc-600">
              Escribe el slug para confirmar
            </Label>
            <input
              id="delete-slug"
              className="rounded-md border border-zinc-300 px-2 py-1 text-sm"
              value={slugInput}
              onChange={(e) => setSlugInput(e.target.value)}
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setShowDeleteConfirm(false)
                setSlugInput('')
              }}
            >
              Cancelar
            </Button>
            <Button
              className="bg-red-600 hover:bg-red-700"
              disabled={slugInput !== restaurant.slug || deleteRestaurant.isPending}
              onClick={() => deleteRestaurant.mutate()}
            >
              Confirmar eliminación
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
