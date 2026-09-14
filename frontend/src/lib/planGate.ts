import axios from 'axios'

export interface PlanGateError {
  feature: string
  requiredPlan?: string
}

/** Matches the `{ code: "PLAN_LIMIT_EXCEEDED", feature, requiredPlan, currentPlan }` body
 *  GlobalExceptionHandler.handlePlanLimitExceeded sends on a 402, same code-property pattern
 *  already used for CASH_SHIFT_OVERDUE etc. (see TableInformation.tsx). */
export function extractPlanGateError(error: unknown): PlanGateError | null {
  if (!axios.isAxiosError(error)) return null
  const data = error.response?.data as
    | { code?: string; feature?: string; requiredPlan?: string }
    | undefined
  if (data?.code !== 'PLAN_LIMIT_EXCEEDED') return null
  return { feature: data.feature ?? '', requiredPlan: data.requiredPlan }
}
