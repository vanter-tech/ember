import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import toast from 'react-hot-toast'
import {
  platformAuditLogService,
  platformRestaurantService,
  type DeploymentMode,
  type PlatformRestaurantAdmin,
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
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

const modeBadgeClass = (mode: string) =>
  mode === 'HUB' ? 'bg-amber-100 text-amber-800' : 'bg-blue-100 text-blue-700'
const modeLabel = (mode: string) => (mode === 'HUB' ? 'Hub' : 'Web')
const otherMode = (mode: DeploymentMode): DeploymentMode => (mode === 'HUB' ? 'CLOUD' : 'HUB')

const nextStatus = (
  status: PlatformRestaurantDetail['status']
): PlatformRestaurantDetail['status'] => (status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED')

export default function ConsoleRestaurantDetail() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
  const [auditPage, setAuditPage] = useState(0)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [slugInput, setSlugInput] = useState('')
  const [showModeConfirm, setShowModeConfirm] = useState(false)
  const [modeSlugInput, setModeSlugInput] = useState('')
  const [resetPasswordAdmin, setResetPasswordAdmin] = useState<PlatformRestaurantAdmin | null>(null)
  const [resetPasswordValue, setResetPasswordValue] = useState('')

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

  const changePlan = useMutation({
    mutationFn: (plan: PlatformRestaurantDetail['plan']) =>
      platformRestaurantService.updatePlan(id!, plan),
    onSuccess: invalidateAll,
  })

  const changeMode = useMutation({
    mutationFn: (mode: DeploymentMode) =>
      platformRestaurantService.updateMode(id!, mode, modeSlugInput),
    onSuccess: () => {
      setShowModeConfirm(false)
      setModeSlugInput('')
      invalidateAll()
    },
    onError: (error) => {
      const detail =
        axios.isAxiosError(error) &&
        typeof (error.response?.data as { detail?: unknown })?.detail === 'string'
          ? (error.response!.data as { detail: string }).detail
          : undefined
      toast.error(detail ?? 'No se pudo cambiar el modo', {
        id: 'console-mode-error',
        duration: 5000,
      })
    },
  })

  const deleteRestaurant = useMutation({
    mutationFn: () => platformRestaurantService.deleteRestaurant(id!),
    onSuccess: () => {
      setShowDeleteConfirm(false)
      setSlugInput('')
      invalidateAll()
    },
    onError: (error) => {
      const detail =
        axios.isAxiosError(error) &&
        typeof (error.response?.data as { detail?: unknown })?.detail === 'string'
          ? (error.response!.data as { detail: string }).detail
          : undefined
      toast.error(detail ?? 'No se pudo eliminar el restaurante', {
        id: 'console-delete-error',
        duration: 5000,
      })
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

  const resetAdminPassword = useMutation({
    mutationFn: () =>
      platformRestaurantService.resetAdminPassword(id!, resetPasswordAdmin!.id, resetPasswordValue),
    onSuccess: () => {
      toast.success(`Contraseña temporal asignada a ${resetPasswordAdmin?.email}`)
      setResetPasswordAdmin(null)
      setResetPasswordValue('')
    },
    onError: (error) => {
      const detail =
        axios.isAxiosError(error) &&
        typeof (error.response?.data as { detail?: unknown })?.detail === 'string'
          ? (error.response!.data as { detail: string }).detail
          : undefined
      toast.error(detail ?? 'No se pudo resetear la contraseña', {
        id: 'console-reset-password-error',
        duration: 5000,
      })
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
                  disabled={issueHubLicense.isPending || restaurant.deploymentMode !== 'HUB'}
                  title={
                    restaurant.deploymentMode !== 'HUB' ? 'Solo restaurantes en modo Hub' : undefined
                  }
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
            <Select
              value={restaurant.plan}
              onValueChange={(value) => changePlan.mutate(value as PlatformRestaurantDetail['plan'])}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="FREE">Free</SelectItem>
                <SelectItem value="STARTER">Starter</SelectItem>
                <SelectItem value="PRO">Pro</SelectItem>
                <SelectItem value="ENTERPRISE">Enterprise</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div>
            <div className="text-zinc-500">Modo de uso</div>
            <div className="flex items-center gap-2">
              <Badge
                aria-label={`Modo: ${modeLabel(restaurant.deploymentMode)}`}
                className={modeBadgeClass(restaurant.deploymentMode)}
              >
                {modeLabel(restaurant.deploymentMode)}
              </Badge>
              <Button
                variant="outline"
                size="sm"
                disabled={restaurant.status === 'DELETED'}
                onClick={() => setShowModeConfirm(true)}
              >
                Cambiar modo
              </Button>
            </div>
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
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {restaurant.admins.map((admin) => (
                <TableRow key={admin.id}>
                  <TableCell className="font-medium text-zinc-800">{admin.name}</TableCell>
                  <TableCell className="text-zinc-500">{admin.email}</TableCell>
                  <TableCell className="text-right">
                    <Button variant="outline" size="sm" onClick={() => setResetPasswordAdmin(admin)}>
                      Resetear contraseña
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {restaurant.admins.length === 0 && (
                <TableRow>
                  <TableCell colSpan={3} className="py-6 text-center text-zinc-400">
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
        open={showModeConfirm}
        onOpenChange={(open) => {
          setShowModeConfirm(open)
          if (!open) setModeSlugInput('')
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Cambiar modo de uso</DialogTitle>
            <DialogDescription>
              {restaurant.deploymentMode === 'HUB' ? (
                <>
                  Pasar <span className="font-medium">{restaurant.name}</span> a{' '}
                  <strong>Web</strong>: su Hub queda en modo consulta (solo lectura) tras 48 h y la
                  cuenta web empieza vacía. No se copian datos.
                </>
              ) : (
                <>
                  Pasar <span className="font-medium">{restaurant.name}</span> a{' '}
                  <strong>Hub</strong>: el acceso a Ember Web se bloquea de inmediato y sus datos en
                  la nube quedan guardados pero inaccesibles. No se copian datos.
                </>
              )}
            </DialogDescription>
          </DialogHeader>
          <p className="text-sm text-zinc-600">
            Plan actual: <span className="font-medium">{restaurant.plan}</span>. Asigna el plan
            acordado después del cambio.
          </p>
          <div className="flex flex-col gap-2">
            <Label htmlFor="mode-slug" className="text-sm text-zinc-600">
              Escribe el slug para confirmar
            </Label>
            <input
              id="mode-slug"
              className="rounded-md border border-zinc-300 px-2 py-1 text-sm"
              value={modeSlugInput}
              onChange={(e) => setModeSlugInput(e.target.value)}
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setShowModeConfirm(false)
                setModeSlugInput('')
              }}
            >
              Cancelar
            </Button>
            <Button
              disabled={modeSlugInput !== restaurant.slug || changeMode.isPending}
              onClick={() => changeMode.mutate(otherMode(restaurant.deploymentMode))}
            >
              Confirmar cambio
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

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

      <Dialog
        open={resetPasswordAdmin !== null}
        onOpenChange={(open) => {
          if (!open) {
            setResetPasswordAdmin(null)
            setResetPasswordValue('')
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Resetear contraseña</DialogTitle>
            <DialogDescription>
              Define una contraseña temporal para{' '}
              <span className="font-medium">{resetPasswordAdmin?.email}</span>. Deberá cambiarla
              al iniciar sesión — compártesela por un canal aparte de este panel.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            <Label htmlFor="reset-password-value" className="text-sm text-zinc-600">
              Contraseña temporal
            </Label>
            <input
              id="reset-password-value"
              type="text"
              className="rounded-md border border-zinc-300 px-2 py-1 text-sm"
              value={resetPasswordValue}
              onChange={(e) => setResetPasswordValue(e.target.value)}
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setResetPasswordAdmin(null)
                setResetPasswordValue('')
              }}
            >
              Cancelar
            </Button>
            <Button
              disabled={resetPasswordValue.length < 8 || resetAdminPassword.isPending}
              onClick={() => resetAdminPassword.mutate()}
            >
              {resetAdminPassword.isPending ? 'Reseteando...' : 'Confirmar reseteo'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
