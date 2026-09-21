import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { platformRestaurantService, type DeploymentMode } from '@/lib/platformApi'
import { PaginationControls } from '@/components/PaginationControls'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
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

export default function ConsoleRestaurants() {
  const [page, setPage] = useState(0)
  const [includeDeleted, setIncludeDeleted] = useState(false)
  const [mode, setMode] = useState<DeploymentMode | 'ALL'>('ALL')

  const {
    data: restaurantsPage,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['platformRestaurants', page, includeDeleted, mode],
    queryFn: () =>
      platformRestaurantService.getAll(page, 10, includeDeleted, mode === 'ALL' ? undefined : mode),
  })
  const restaurants = restaurantsPage?.content ?? []

  return (
    <div className="flex flex-col gap-4">
      <ConsolePageHeader
        title="Restaurantes"
        action={
          <Button asChild>
            <Link to="/console/restaurants/new">Nuevo restaurante</Link>
          </Button>
        }
      />

      <div className="flex items-center gap-2">
        <Switch
          id="include-deleted"
          checked={includeDeleted}
          onCheckedChange={(v) => {
            setIncludeDeleted(v)
            setPage(0)
          }}
        />
        <Label htmlFor="include-deleted" className="text-sm text-zinc-600">
          Ver eliminados
        </Label>
        <Select
          value={mode}
          onValueChange={(value) => {
            setMode(value as DeploymentMode | 'ALL')
            setPage(0)
          }}
        >
          <SelectTrigger className="ml-4 w-44" aria-label="Filtrar por modo">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Todos los modos</SelectItem>
            <SelectItem value="CLOUD">Web</SelectItem>
            <SelectItem value="HUB">Hub</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="rounded-lg border border-zinc-200 bg-white">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Nombre</TableHead>
              <TableHead>Slug</TableHead>
              <TableHead>Plan</TableHead>
              <TableHead>Modo</TableHead>
              <TableHead>Estado</TableHead>
              <TableHead>Hub</TableHead>
              <TableHead>Creado</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading &&
              Array.from({ length: 6 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell colSpan={7}>
                    <div className="h-4 animate-pulse rounded bg-zinc-100" />
                  </TableCell>
                </TableRow>
              ))}

            {isError && (
              <TableRow>
                <TableCell colSpan={7} className="py-6 text-center text-red-500">
                  Error al cargar los restaurantes.
                </TableCell>
              </TableRow>
            )}

            {!isLoading &&
              !isError &&
              restaurants.map((restaurant) => (
                <TableRow
                  key={restaurant.id}
                  className={restaurant.status === 'DELETED' ? 'opacity-50' : undefined}
                >
                  <TableCell className="font-medium text-zinc-800">
                    <Link
                      to={`/console/restaurants/${restaurant.id}`}
                      className="hover:underline"
                    >
                      {restaurant.name}
                    </Link>
                  </TableCell>
                  <TableCell className="text-zinc-500">{restaurant.slug}</TableCell>
                  <TableCell className="text-zinc-500">{restaurant.plan}</TableCell>
                  <TableCell>
                    <Badge
                      aria-label={`Modo: ${modeLabel(restaurant.deploymentMode)}`}
                      className={modeBadgeClass(restaurant.deploymentMode)}
                    >
                      {modeLabel(restaurant.deploymentMode)}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <Badge className={statusBadgeClass(restaurant.status)}>
                      {restaurant.status}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <HubBadge status={restaurant.hubStatus} />
                  </TableCell>
                  <TableCell className="text-zinc-500">
                    {new Date(restaurant.createdAt).toLocaleDateString()}
                  </TableCell>
                </TableRow>
              ))}

            {!isLoading && !isError && restaurants.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} className="py-6 text-center text-zinc-400">
                  Sin restaurantes registrados.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <PaginationControls
        page={page}
        totalPages={restaurantsPage?.totalPages ?? 0}
        onPageChange={setPage}
      />
    </div>
  )
}
