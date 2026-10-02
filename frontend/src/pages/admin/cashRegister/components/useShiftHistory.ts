import { useQuery } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'

// Shared by the page (it is one skeleton until the first page of history arrives) and the table.
export const useShiftHistory = (page: number) =>
  useQuery({
    queryKey: ['cashShiftHistory', page],
    queryFn: () => cashShiftService.history({ page, size: 20 }),
  })
