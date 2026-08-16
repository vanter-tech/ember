import { useQuery } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { formatCurrency } from '@/lib/format'
import { OpenShiftDialog } from './components/OpenShiftDialog'
import { MovementDialog } from './components/MovementDialog'
import { CloseShiftDialog } from './components/CloseShiftDialog'

export const CashRegister = () => {
  const { openModal } = useUIStore()

  const { data: shift, isLoading } = useQuery({
    queryKey: ['cashShiftCurrent'],
    queryFn: cashShiftService.current,
  })

  const { data: detail } = useQuery({
    queryKey: ['cashShiftDetail', shift?.id],
    queryFn: () => cashShiftService.detail(shift!.id!),
    enabled: !!shift?.id,
  })

  if (isLoading) {
    return <div className="p-6 text-zinc-500">Cargando caja...</div>
  }

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">Caja</h1>
        <p className="text-sm text-muted-foreground">Apertura, movimientos y arqueo del turno.</p>
      </div>

      {!shift ? (
        <Card className="border border-border/40 bg-background py-6 shadow-sm">
          <CardContent className="flex flex-col items-center gap-4 py-10">
            <p className="text-sm text-muted-foreground">No hay un turno de caja abierto.</p>
            <Button onClick={() => openModal('OPEN_SHIFT')}>Abrir caja</Button>
          </CardContent>
        </Card>
      ) : (
        <>
          <Card className="border border-border/40 bg-background py-6 shadow-sm">
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Turno #{shift.shiftNumber}
              </CardTitle>
              <Badge variant="secondary">{shift.status === 'OPEN' ? 'Abierto' : 'Cerrado'}</Badge>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                <div>
                  <p className="text-xs text-muted-foreground">Fondo inicial</p>
                  <p className="text-lg font-bold text-primary">{formatCurrency(shift.openingFloat ?? 0)}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Abierto por</p>
                  <p className="text-sm font-medium">{shift.openedByName}</p>
                </div>
              </div>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => openModal('CASH_MOVEMENT', { shiftId: shift.id })}>
                  Registrar movimiento
                </Button>
                <Button onClick={() => openModal('CLOSE_SHIFT', { shiftId: shift.id })}>
                  Cerrar caja (Arqueo)
                </Button>
              </div>
            </CardContent>
          </Card>

          <Card className="border border-border/40 bg-background py-6 shadow-sm">
            <CardHeader>
              <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Movimientos
              </CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Tipo</TableHead>
                    <TableHead>Monto</TableHead>
                    <TableHead>Motivo</TableHead>
                    <TableHead>Registrado por</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(detail?.movements ?? []).length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={4} className="text-center text-sm text-muted-foreground">
                        Sin movimientos registrados.
                      </TableCell>
                    </TableRow>
                  ) : (
                    (detail!.movements ?? []).map((movement) => (
                      <TableRow key={movement.id}>
                        <TableCell>{movement.type === 'CASH_IN' ? 'Entrada' : 'Salida'}</TableCell>
                        <TableCell>{formatCurrency(movement.amount ?? 0)}</TableCell>
                        <TableCell>{movement.reason}</TableCell>
                        <TableCell>{movement.createdByName}</TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </>
      )}

      <OpenShiftDialog />
      <MovementDialog />
      <CloseShiftDialog />
    </div>
  )
}
