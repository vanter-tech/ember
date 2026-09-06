import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  platformStatsService,
  platformAuditLogService,
  type HubStatus,
  type PlatformStats,
} from '@/lib/platformApi'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { HubBadge } from '@/components/console/HubBadge'
import { ConsolePageHeader } from '@/components/console/ConsolePageHeader'

function StatCard({
  label,
  value,
  children,
}: {
  label: string
  value: number
  children?: ReactNode
}) {
  return (
    <Card className="rounded-2xl">
      <CardContent className="flex flex-col gap-1 p-4">
        <span className="text-xs text-zinc-500">{label}</span>
        <span className="text-2xl font-semibold text-zinc-900">{value}</span>
        {children}
      </CardContent>
    </Card>
  )
}

const HUB_ORDER: HubStatus[] = ['ONLINE', 'STALE', 'OFFLINE', 'NEVER']

function hubValue(hubs: PlatformStats['hubs'], s: HubStatus): number {
  switch (s) {
    case 'ONLINE':
      return hubs.online
    case 'STALE':
      return hubs.stale
    case 'OFFLINE':
      return hubs.offline
    case 'NEVER':
      return hubs.never
  }
}

export default function ConsoleDashboard() {
  const stats = useQuery({ queryKey: ['platformStats'], queryFn: platformStatsService.get })
  const activity = useQuery({
    queryKey: ['platformActivity'],
    queryFn: () => platformAuditLogService.getRecent(0, 10),
  })
  const rows = activity.data?.content ?? []

  return (
    <div className="flex flex-col gap-6">
      <ConsolePageHeader title="Panel" />

      {stats.isError ? (
        <div className="text-sm text-red-500">No se pudieron cargar las métricas.</div>
      ) : stats.isLoading ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {Array.from({ length: 7 }).map((_, i) => (
            <div key={i} className="h-20 animate-pulse rounded-2xl bg-zinc-100" />
          ))}
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            <StatCard label="Tenants activos" value={stats.data!.tenants.active} />
            <StatCard label="Suspendidos" value={stats.data!.tenants.suspended} />
            <StatCard label="Eliminados" value={stats.data!.tenants.deleted} />
          </div>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {HUB_ORDER.map((s) => (
              <StatCard
                key={s}
                label={`Hubs ${s.toLowerCase()}`}
                value={hubValue(stats.data!.hubs, s)}
              >
                <HubBadge status={s} />
              </StatCard>
            ))}
          </div>
        </div>
      )}

      <Card className="rounded-2xl">
        <CardHeader>
          <CardTitle className="text-lg">Actividad reciente</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Fecha</TableHead>
                <TableHead>Operador</TableHead>
                <TableHead>Acción</TableHead>
                <TableHead>Restaurante</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((e) => (
                <TableRow key={e.id}>
                  <TableCell
                    className="text-zinc-500"
                    title={new Date(e.createdAt).toLocaleString()}
                  >
                    {new Date(e.createdAt).toLocaleString()}
                  </TableCell>
                  <TableCell className="text-zinc-500">{e.operatorEmail}</TableCell>
                  <TableCell>
                    <Badge variant="outline">{e.action}</Badge>
                  </TableCell>
                  <TableCell>
                    {e.restaurantId ? (
                      <Link
                        to={`/console/restaurants/${e.restaurantId}`}
                        className="text-[#8c1717] hover:underline"
                      >
                        Ver
                      </Link>
                    ) : (
                      '—'
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {rows.length === 0 && !activity.isLoading && (
                <TableRow>
                  <TableCell colSpan={4} className="py-6 text-center text-zinc-400">
                    Sin actividad registrada.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <div className="flex flex-wrap gap-3">
        <Button asChild>
          <Link to="/console/restaurants/new">Nuevo restaurante</Link>
        </Button>
        <Button variant="outline" asChild>
          <Link to="/console/restaurants">Ver restaurantes</Link>
        </Button>
      </div>
    </div>
  )
}
