import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'
import { Card, CardContent } from '@/components/ui/card'
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { formatCurrency } from '@/lib/format'
import { PaginationControls } from '@/components/PaginationControls'

export const ShiftHistoryTable = () => {
  const [page, setPage] = useState(0)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['cashShiftHistory', page],
    queryFn: () => cashShiftService.history({ page, size: 20 }),
  })

  if (isLoading) {
    return <div className="p-6 text-sm text-muted-foreground">Cargando turnos...</div>
  }

  if (isError || !data) {
    return <div className="p-6 text-sm text-destructive">Error al cargar el historial.</div>
  }

  return (
    <>
      <Card className="border border-border/40 bg-background py-6 shadow-sm">
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Turno</TableHead>
                <TableHead>Estado</TableHead>
                <TableHead>Abierto por</TableHead>
                <TableHead>Cerrado por</TableHead>
                <TableHead>Esperado</TableHead>
                <TableHead>Contado</TableHead>
                <TableHead>Diferencia</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} className="text-center text-sm text-muted-foreground">
                    Sin turnos registrados.
                  </TableCell>
                </TableRow>
              ) : (
                data.content.map((shift) => (
                  <TableRow key={shift.id}>
                    <TableCell>#{shift.shiftNumber}</TableCell>
                    <TableCell>
                      <Badge variant={shift.status === 'OPEN' ? 'default' : 'secondary'}>
                        {shift.status === 'OPEN' ? 'Abierto' : 'Cerrado'}
                      </Badge>
                    </TableCell>
                    <TableCell>{shift.openedByName}</TableCell>
                    <TableCell>{shift.closedByName ?? '—'}</TableCell>
                    <TableCell>{shift.expectedCash != null ? formatCurrency(shift.expectedCash) : '—'}</TableCell>
                    <TableCell>{shift.countedCash != null ? formatCurrency(shift.countedCash) : '—'}</TableCell>
                    <TableCell>{shift.variance != null ? formatCurrency(shift.variance) : '—'}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
      <PaginationControls page={page} totalPages={data.totalPages} onPageChange={setPage} />
    </>
  )
}
