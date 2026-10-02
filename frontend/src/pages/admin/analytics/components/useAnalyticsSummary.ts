import { useQuery } from '@tanstack/react-query'
import { analyticsService } from '@/lib/api'

// Shared by the page (its header waits for it) and SummaryCards: one request, deduplicated.
export const useAnalyticsSummary = () =>
  useQuery({
    queryKey: ['analyticsSummary'],
    queryFn: () => analyticsService.getSummary(),
  })
