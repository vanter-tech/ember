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

function readPlanGateBody(data: { code?: string; feature?: string; requiredPlan?: string }): PlanGateError | null {
  if (data.code !== 'PLAN_LIMIT_EXCEEDED') return null
  return { feature: data.feature ?? '', requiredPlan: data.requiredPlan }
}

/** Same as {@link extractPlanGateError}, for requests made with `responseType: 'blob'`
 *  (e.g. a file download) — axios still hands back the error body as a Blob in that mode,
 *  even on a JSON error response, so it needs an async text()+JSON.parse round trip. */
export async function extractPlanGateErrorFromBlob(error: unknown): Promise<PlanGateError | null> {
  if (!axios.isAxiosError(error)) return null
  const data = error.response?.data
  if (data instanceof Blob) {
    try {
      return readPlanGateBody(JSON.parse(await data.text()))
    } catch {
      return null
    }
  }
  return readPlanGateBody((data ?? {}) as { code?: string; feature?: string; requiredPlan?: string })
}
