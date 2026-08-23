import type { components } from '@/lib/backend-types'
import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
import { useUIStore } from '@/store/uiStore'
export type LoginRequest = components['schemas']['LoginRequest']
export type LoginResponse = components['schemas']['AuthResponse']
export type CategoryResponse = components['schemas']['CategoryResponse']
export type CategoryRequest = components['schemas']['CategoryRequest']
export type MenuItemResponse = components['schemas']['MenuItemResponse']
export type MenuItemRequest = components['schemas']['MenuItemRequest']
export type ModifierGroupResponse = components['schemas']['ModifierGroupResponse']
export type ModifierGroupRequest = components['schemas']['ModifierGroupRequest']
export type ModifierOptionRequest = components['schemas']['ModifierOptionRequest']
export type SettingsResponse = components['schemas']['SettingsPayload']
export type DashboardResponse = components['schemas']['TableStatusResponse']
export type CreateSession = components['schemas']['SessionCreatedResponse']
export type infoSession = components['schemas']['SessionDetailResponseDto']
export type sessionActivity = components['schemas']['SessionActivityDto']
export type calculateBill = components['schemas']['CalculateBillRequest']
export type JoinSessionCode = components['schemas']['JoinSessionCodeRequest']
export type sessionResponse = components['schemas']['Session']
export type joinSessionResponse = components['schemas']['JoinSessionResponse']
export type menuResponse = components['schemas']['MenuDTO']
export type orderItemDTO = components['schemas']['OrderItemDto']
export type participantDTO = components['schemas']['ParticipantDto']
export type tableStatus = components['schemas']['SessionStatusDto']
export type kitchenOrdersDisplayByTables = components['schemas']['KitchenDisplayEntry']
export type kitchenOrders = components['schemas']['KitchenOrder']
export type OrderItemStatus = components['schemas']['UpdateItemStatusRequest']['status']
export type SplitMethod = calculateBill['splitMethod']
export type Bill = components['schemas']['Bill']
export type BillSplit = components['schemas']['BillSplit']
export type Payment = components['schemas']['Payment']
export type RequestBillingRequest = components['schemas']['RequestBillingRequest']
export type PaymentResponse = components['schemas']['PaymentResponse']
export type RefundResponse = components['schemas']['RefundResponse']
export type Refund = components['schemas']['Refund']

// WebSocket-only shapes (BILL_READY/SPLIT_PAID/DIGITAL_PAYMENT_INITIATED broadcasts) — the OpenAPI
// spec only documents REST responses, so these have no generated schema to switch to.
export interface PendingDigitalPayment {
  id: number
  participantName: string
  amount: number
}

export interface WaiterBillState {
  id: number
  total: number
  splits: BillSplit[]
  pendingDigitalPayments?: PendingDigitalPayment[]
}

export type LoyaltySettings = components['schemas']['LoyaltySettings']
export type LoyaltyAccrualMode = NonNullable<LoyaltySettings['accrualMode']>

export type LoyaltyRewardResponse = components['schemas']['LoyaltyRewardResponse']
export type CreateLoyaltyRewardRequest = components['schemas']['CreateLoyaltyRewardRequest']
export type UpdateLoyaltyRewardRequest = components['schemas']['UpdateLoyaltyRewardRequest']
export type LoyaltyTier = NonNullable<LoyaltyRewardResponse['requiredTier']>

export const loyaltyRewardService = {
  list: async (): Promise<LoyaltyRewardResponse[]> => {
    const { data } = await api.get<LoyaltyRewardResponse[]>('/loyalty/rewards')
    return data
  },
  create: async (request: CreateLoyaltyRewardRequest): Promise<LoyaltyRewardResponse> => {
    const { data } = await api.post<LoyaltyRewardResponse>('/loyalty/rewards', request)
    return data
  },
  update: async (id: number, request: UpdateLoyaltyRewardRequest): Promise<LoyaltyRewardResponse> => {
    const { data } = await api.patch<LoyaltyRewardResponse>(`/loyalty/rewards/${id}`, request)
    return data
  },
}

export type RewardCatalogEntry = components['schemas']['RewardCatalogEntryResponse']
export type LoyaltyAccountResponse = components['schemas']['LoyaltyAccountResponse']
export type LoyaltyVisitResponse = components['schemas']['LoyaltyVisitResponse']

export const loyaltyAccountService = {
  me: async (): Promise<LoyaltyAccountResponse> => {
    const { data } = await api.get<LoyaltyAccountResponse>('/loyalty/accounts/me')
    return data
  },
  visits: async (): Promise<LoyaltyVisitResponse[]> => {
    const { data } = await api.get<LoyaltyVisitResponse[]>('/loyalty/accounts/me/visits')
    return data
  },
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export type RestaurantResponse = components['schemas']['Restaurant']
export type UpdateRestaurantPlanRequest = components['schemas']['UpdateRestaurantPlanRequest']
export type PublicBranding = components['schemas']['PublicBrandingResponse']

export type AnalyticsSummaryResponse = components['schemas']['AnalyticsSummaryResponse']
export type AnalyticsSalesResponse = components['schemas']['AnalyticsSalesResponse']
export type SalesBucket = components['schemas']['SalesBucket']
export type AnalyticsProductsResponse = components['schemas']['AnalyticsProductsResponse']
export type ProductPerformance = components['schemas']['ProductPerformance']
export type CategoryPerformance = components['schemas']['CategoryPerformance']
export type AnalyticsTablesResponse = components['schemas']['AnalyticsTablesResponse']
export type TablePerformance = components['schemas']['TablePerformance']

// The 'granularity' request param has no dedicated schema (it's a plain string query param
// server-side), so it's derived from the response enum rather than hand-typed.
export type SalesGranularity = Lowercase<
  NonNullable<components['schemas']['AnalyticsSalesResponse']['granularity']>
>

declare global {
  interface Window {
    ENV: {
      EMBW_API_URL?: string
      EMBW_WS_URL?: string
    }
  }
}

export const api = axios.create({
  baseURL:
    window.ENV?.EMBW_API_URL ||
    import.meta.env.VITE_API_URL ||
    'http://localhost:8080/v1',
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use(
  (config) => {
    const token = useAuthStore.getState().token

    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Matches the exact ProblemDetail.detail strings written by SecurityConfig.jwtAuthFilter's
// writeSuspendedTenantResponse (backend). @PreAuthorize role denials also return a 403, but with
// detail "Access denied" (GlobalExceptionHandler.handleAccessDenied) — that path is left alone.
const isTenantSuspendedDetail = (detail: unknown): detail is string =>
  typeof detail === 'string' &&
  (detail.startsWith('This tenant account is') || detail === 'Tenant account not found.')

api.interceptors.response.use(
  (response) => {
    return response
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      useAuthStore.getState().logout()
    }
    if (error.response && error.response.status === 403) {
      const detail = error.response.data?.detail
      if (isTenantSuspendedDetail(detail)) {
        useUIStore.getState().openModal('TENANT_SUSPENDED', { detail })
      }
    }
    return Promise.reject(error)
  }
)

export const authService = {
  login: async (credentials: LoginRequest): Promise<LoginResponse> => {
    const { data } = await api.post<LoginResponse>('/auth/login', credentials)
    return data
  },

  register: async (
    details: components['schemas']['RegisterRequest']
  ): Promise<LoginResponse> => {
    const { data } = await api.post<LoginResponse>('/auth/register', details)
    return data
  },
}

export const categoryService = {
  getAll: async (page = 0, size = 6): Promise<Page<CategoryResponse>> => {
    const { data } = await api.get<Page<CategoryResponse>>(
      '/catalog/categories',
      { params: { page, size } }
    )
    return data
  },
  create: async (details: FormData): Promise<CategoryResponse> => {
    const { data } = await api.post<CategoryRequest>(
      '/catalog/categories',
      details,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      }
    )
    return data
  },
  delete: async (id: number): Promise<void> => {
    await api.delete<void>(`/catalog/categories/${id}`)
  },
  update: async (id: number, details: FormData): Promise<CategoryResponse> => {
    const { data } = await api.put<CategoryResponse>(
      `/catalog/categories/${id}`,
      details,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      }
    )

    return data
  },
}

export const menuItemService = {
  getAll: async (
    id: number,
    page = 0,
    size = 10
  ): Promise<Page<MenuItemResponse>> => {
    const { data } = await api.get<Page<MenuItemResponse>>('/catalog/items', {
      params: { id, page, size },
    })
    return data
  },
  create: async (details: FormData): Promise<MenuItemResponse> => {
    const { data } = await api.post<MenuItemRequest>(
      '/catalog/items',
      details,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      }
    )

    return data
  },
  toggleAvailability: async (id: number): Promise<MenuItemResponse> => {
    const { data } = await api.patch<MenuItemResponse>(
      `catalog/items/${id}/availability`
    )
    return data
  },
  update: async (id: number, details: FormData): Promise<MenuItemResponse> => {
    const { data } = await api.put<MenuItemResponse>(
      `/catalog/items/${id}`,
      details,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      }
    )
    return data
  },
  delete: async (id: number): Promise<void> => {
    await api.delete<void>(`/catalog/items/${id}`)
  },
}

export const modifierGroupService = {
  getAll: async (): Promise<ModifierGroupResponse[]> => {
    const { data } = await api.get<ModifierGroupResponse[]>('/catalog/modifier-groups')
    return data
  },
  create: async (details: ModifierGroupRequest): Promise<ModifierGroupResponse> => {
    const { data } = await api.post<ModifierGroupResponse>('/catalog/modifier-groups', details)
    return data
  },
  update: async (id: number, details: ModifierGroupRequest): Promise<ModifierGroupResponse> => {
    const { data } = await api.patch<ModifierGroupResponse>(`/catalog/modifier-groups/${id}`, details)
    return data
  },
  setActive: async (id: number, active: boolean): Promise<ModifierGroupResponse> => {
    const { data } = await api.patch<ModifierGroupResponse>(`/catalog/modifier-groups/${id}/active`, active)
    return data
  },
  addOption: async (id: number, details: ModifierOptionRequest): Promise<ModifierGroupResponse> => {
    const { data } = await api.post<ModifierGroupResponse>(`/catalog/modifier-groups/${id}/options`, details)
    return data
  },
  updateOption: async (id: number, optionId: number, details: ModifierOptionRequest): Promise<ModifierGroupResponse> => {
    const { data } = await api.patch<ModifierGroupResponse>(`/catalog/modifier-groups/${id}/options/${optionId}`, details)
    return data
  },
  deactivateOption: async (id: number, optionId: number): Promise<ModifierGroupResponse> => {
    const { data } = await api.delete<ModifierGroupResponse>(`/catalog/modifier-groups/${id}/options/${optionId}`)
    return data
  },
  assignToMenuItem: async (menuItemId: number, assignments: { groupId: number; displayOrder: number }[]): Promise<void> => {
    await api.patch(`/catalog/items/${menuItemId}/modifier-groups`, assignments)
  },
}

export type InventoryItemResponse = components['schemas']['InventoryItemResponse']
export type InventoryItemRequest = components['schemas']['InventoryItemRequest']
export type InventoryItemUpdateRequest = components['schemas']['InventoryItemUpdateRequest']

export const inventoryService = {
  getAll: async (): Promise<InventoryItemResponse[]> => {
    const { data } = await api.get<InventoryItemResponse[]>('/catalog/inventory')
    return data
  },
  create: async (details: InventoryItemRequest): Promise<InventoryItemResponse> => {
    const { data } = await api.post<InventoryItemResponse>('/catalog/inventory', details)
    return data
  },
  update: async (id: number, details: InventoryItemUpdateRequest): Promise<InventoryItemResponse> => {
    const { data } = await api.patch<InventoryItemResponse>(`/catalog/inventory/${id}`, details)
    return data
  },
  restock: async (id: number, delta: number): Promise<InventoryItemResponse> => {
    const { data } = await api.post<InventoryItemResponse>(`/catalog/inventory/${id}/restock`, { delta })
    return data
  },
  remove: async (id: number): Promise<void> => {
    await api.delete(`/catalog/inventory/${id}`)
  },
}

const listAllMenuItems = async (): Promise<MenuItemResponse[]> => {
  const { data } = await api.get<Page<MenuItemResponse>>('/catalog/items', { params: { size: 500 } })
  return data.content
}

export const inventoryMenuItemService = {
  listAll: listAllMenuItems,
}

export const SettingsService = {
  getSettings: async (): Promise<SettingsResponse> => {
    const { data } = await api.get<SettingsResponse>('/settings')
    return data
  },
  updateSettings: async (details: SettingsResponse): Promise<void> => {
    await api.put<SettingsResponse>('/settings', details)
  },
}

export const DashboardService = {
  getDashboardData: async (): Promise<DashboardResponse[]> => {
    const { data } = await api.get<DashboardResponse[]>('/dashboard/status')
    return data
  },
}

export const SessionTableService = {
  createSession: async (
    tableId: string,
    maxParticipants: number
  ): Promise<CreateSession> => {
    const { data } = await api.post<CreateSession>('/sessions', {
      tableId: tableId,
      maxParticipants: maxParticipants,
    })
    return data
  },

  getQrToken: async (sessionId: string): Promise<{ qrToken: string }> => {
    const { data } = await api.get<{ qrToken: string }>(
      `/sessions/${sessionId}/qr`
    )
    return data
  },

  sessionInformation: async (sessionId: string): Promise<infoSession> => {
    const { data } = await api.get<infoSession>(`/sessions/${sessionId}`)
    return data
  },

  sessionStatus: async(sessionId: string): Promise<tableStatus> => {
    const { data } = await api.get<tableStatus>(`/sessions/${sessionId}/status`)
    return data
  },

  closeEmptySession: async (sessionId: string): Promise<void> => {
    await api.delete<void>(`sessions/${sessionId}/cancel`)
  },

  calculateBill: async (
    sessionId: string,
    splitMethod: 'BY_CONSUMPTION' | 'EQUAL_PARTS'
  ): Promise<calculateBill> => {
    const { data } = await api.post<calculateBill>(
      `billing/sessions/${sessionId}/bill`,
      { splitMethod }
    )
    return data
  },

  joinSessionViaCode: async (joinCode: string): Promise<joinSessionResponse> => {
    const { data } = await api.post<joinSessionResponse>(`/sessions/join`, {
      joinCode,
    })
    return data
  },

  addItem: async (sessionId: string, itemId: number, selectedOptionIds: number[] = []): Promise<orderItemDTO> => {
    const { data } = await api.post<orderItemDTO>(
      `/sessions/${sessionId}/items`,
      { menuItemId: itemId, selectedOptionIds }
    )
    return data
  },

  confirmMyOrders: async(sessionId: string, userId: string): Promise<void> => {
     await api.post<void>(`/sessions/${sessionId}/participants/${userId}/confirm`)
  },

  deleteItem: async (sessionId: string, itemId: string): Promise<void> => {
    await api.delete<void>(`sessions/${sessionId}/items/${itemId}`)
  },
}

export const menuServices = {
  getMenu: async (): Promise<menuResponse[]> => {
    const { data } = await api.get<menuResponse[]>('/menu')
    return data
  }, 
}

export const kitchenServices = {
  getOrdersByTables: async (): Promise<kitchenOrdersDisplayByTables[]> => {
    const { data } = await api.get<kitchenOrdersDisplayByTables[]>('/kitchen/display')
    return data
  },
  getOrders: async(): Promise<Page<kitchenOrders>> => {
    const { data } = await api.get<Page<kitchenOrders>>('/kitchen/orders')
    return data
  },
  updateItemStatus: async (orderId: string, itemId: string, status: OrderItemStatus): Promise<kitchenOrders> => {
    const { data } = await api.patch<kitchenOrders>(
      `/kitchen/orders/${orderId}/items/${itemId}/status`,
      { status }
    )
    return data
  }

}

export const restaurantAdminService = {
  getPlan: async (): Promise<RestaurantResponse> => {
    const { data } = await api.get<RestaurantResponse>('/admin/restaurant')
    return data
  },
  updatePlan: async (plan: UpdateRestaurantPlanRequest['plan']): Promise<RestaurantResponse> => {
    const { data } = await api.patch<RestaurantResponse>('/admin/restaurant/plan', { plan })
    return data
  },
}

export const publicService = {
  getBranding: async (slug: string): Promise<PublicBranding> => {
    const { data } = await api.get<PublicBranding>(
      `/public/restaurants/${slug}/branding`
    )
    return data
  },
}

export const analyticsService = {
  getSummary: async (from?: string, to?: string): Promise<AnalyticsSummaryResponse> => {
    const { data } = await api.get<AnalyticsSummaryResponse>(
      '/admin/analytics/summary',
      { params: { from, to } }
    )
    return data
  },
  getSales: async (
    granularity?: SalesGranularity,
    from?: string,
    to?: string
  ): Promise<AnalyticsSalesResponse> => {
    const { data } = await api.get<AnalyticsSalesResponse>(
      '/admin/analytics/sales',
      { params: { granularity, from, to } }
    )
    return data
  },
  getProducts: async (
    from?: string,
    to?: string,
    limit?: number
  ): Promise<AnalyticsProductsResponse> => {
    const { data } = await api.get<AnalyticsProductsResponse>(
      '/admin/analytics/products',
      { params: { from, to, limit } }
    )
    return data
  },
  getTables: async (from?: string, to?: string): Promise<AnalyticsTablesResponse> => {
    const { data } = await api.get<AnalyticsTablesResponse>(
      '/admin/analytics/tables',
      { params: { from, to } }
    )
    return data
  },
}

export type StaffMemberResponse = components['schemas']['StaffMemberResponse']
export type CreateStaffRequest = components['schemas']['CreateStaffRequest']
export type UpdateStaffProfileRequest = components['schemas']['UpdateStaffProfileRequest']

export const staffService = {
  getAll: async (): Promise<StaffMemberResponse[]> => {
    const { data } = await api.get<StaffMemberResponse[]>('/admin/staff')
    return data
  },
  create: async (request: CreateStaffRequest): Promise<StaffMemberResponse> => {
    const { data } = await api.post<StaffMemberResponse>('/admin/staff', request)
    return data
  },
  updateProfile: async (
    userId: string,
    request: UpdateStaffProfileRequest
  ): Promise<StaffMemberResponse> => {
    const { data } = await api.patch<StaffMemberResponse>(`/admin/staff/${userId}`, request)
    return data
  },
}

export type CashMovementType = components['schemas']['RecordMovementRequest']['type']
export type CashShiftResponse = components['schemas']['CashShiftResponse']
export type CashMovementResponse = components['schemas']['CashMovementResponse']
export type CashShiftDetailResponse = components['schemas']['CashShiftDetailResponse']
export type DailyReportResponse = components['schemas']['DailyReportResponse']

export const cashShiftService = {
  open: async (openingFloat: number): Promise<CashShiftResponse> => {
    const { data } = await api.post<CashShiftResponse>('/cash-shifts/open', { openingFloat })
    return data
  },
  current: async (): Promise<CashShiftResponse | null> => {
    try {
      const { data } = await api.get<CashShiftResponse>('/cash-shifts/current')
      return data
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) return null
      throw error
    }
  },
  history: async (
    params: { from?: string; to?: string; page?: number; size?: number } = {}
  ): Promise<Page<CashShiftResponse>> => {
    const { data } = await api.get<Page<CashShiftResponse>>('/cash-shifts', { params })
    return data
  },
  detail: async (id: number): Promise<CashShiftDetailResponse> => {
    const { data } = await api.get<CashShiftDetailResponse>(`/cash-shifts/${id}`)
    return data
  },
  recordMovement: async (
    id: number,
    movement: components['schemas']['RecordMovementRequest']
  ): Promise<CashMovementResponse> => {
    const { data } = await api.post<CashMovementResponse>(`/cash-shifts/${id}/movements`, movement)
    return data
  },
  close: async (id: number, countedCash: number): Promise<CashShiftResponse> => {
    const { data } = await api.post<CashShiftResponse>(`/cash-shifts/${id}/close`, { countedCash })
    return data
  },
  dailyReport: async (date: string): Promise<DailyReportResponse> => {
    const { data } = await api.get<DailyReportResponse>('/cash-shifts/daily-report', { params: { date } })
    return data
  },
}

export type PrintAgentResponse = components['schemas']['PrintAgentResponse']
export type CreatedPrintAgentResponse = components['schemas']['CreatedPrintAgentResponse']
export type PrinterConfigResponse = components['schemas']['PrinterConfigResponse']
export type PrintJobResponse = components['schemas']['PrintJobResponse']

export const printingService = {
  listAgents: async (): Promise<PrintAgentResponse[]> => {
    const { data } = await api.get<PrintAgentResponse[]>('/printing/admin/agents')
    return data
  },
  createAgent: async (name: string): Promise<CreatedPrintAgentResponse> => {
    const { data } = await api.post<CreatedPrintAgentResponse>('/printing/admin/agents', { name })
    return data
  },
  renameAgent: async (id: string, name: string): Promise<PrintAgentResponse> => {
    const { data } = await api.patch<PrintAgentResponse>(`/printing/admin/agents/${id}`, { name })
    return data
  },
  regenerateKey: async (id: string): Promise<CreatedPrintAgentResponse> => {
    const { data } = await api.post<CreatedPrintAgentResponse>(`/printing/admin/agents/${id}/regenerate-key`)
    return data
  },
  revokeAgent: async (id: string): Promise<void> => {
    await api.delete(`/printing/admin/agents/${id}`)
  },
  listPrinters: async (agentId: string): Promise<PrinterConfigResponse[]> => {
    const { data } = await api.get<PrinterConfigResponse[]>(`/printing/admin/agents/${agentId}/printers`)
    return data
  },
  addPrinter: async (
    agentId: string,
    request: { role: string; connectionType: string; host?: string; port?: number; comPort?: string; label: string }
  ): Promise<PrinterConfigResponse> => {
    const { data } = await api.post<PrinterConfigResponse>(`/printing/admin/agents/${agentId}/printers`, request)
    return data
  },
  updatePrinter: async (
    printerId: string,
    request: Partial<{ host: string; port: number; comPort: string; label: string; active: boolean }>
  ): Promise<PrinterConfigResponse> => {
    const { data } = await api.patch<PrinterConfigResponse>(`/printing/admin/agents/printers/${printerId}`, request)
    return data
  },
  listJobs: async (status?: string): Promise<PrintJobResponse[]> => {
    const { data } = await api.get<{ content: PrintJobResponse[] }>('/printing/jobs', { params: { status } })
    return data.content
  },
  retryJob: async (jobId: string): Promise<void> => {
    await api.post(`/printing/jobs/${jobId}/retry`)
  },
}

export const billingService = {
  requestBilling: async (
    sessionId: string,
    splitMethod: SplitMethod,
    participantCount?: number
  ): Promise<void> => {
    const body: RequestBillingRequest = { splitMethod, participantCount }
    await api.post<void>(`/billing/sessions/${sessionId}/request`, body)
  },
  registerPhysicalPayment: async (
    billId: number,
    participantName: string,
    amount: number
  ): Promise<Payment> => {
    const { data } = await api.post<Payment>('/billing/payments/physical', {
      billId,
      participantName,
      amount,
    })
    return data
  },
  initiateDigitalPayment: async (
    billId: number,
    participantName: string,
    amount: number
  ): Promise<Payment> => {
    const { data } = await api.post<Payment>('/billing/payments/digital', {
      billId,
      participantName,
      amount,
    })
    return data
  },
  confirmDigitalPayment: async (paymentId: number): Promise<Payment> => {
    const { data } = await api.post<Payment>(`/billing/payments/${paymentId}/confirm`)
    return data
  },
  voidBill: async (billId: number, reason: string): Promise<Bill> => {
    const { data } = await api.post<Bill>(`/billing/bills/${billId}/void`, { reason })
    return data
  },
  listPayments: async (billId: number): Promise<PaymentResponse[]> => {
    const { data } = await api.get<PaymentResponse[]>(`/billing/bills/${billId}/payments`)
    return data
  },
  refundPayment: async (
    paymentId: number,
    amount: number | undefined,
    reason: string
  ): Promise<Refund> => {
    const { data } = await api.post<Refund>(`/billing/payments/${paymentId}/refund`, { amount, reason })
    return data
  },
  listRefunds: async (paymentId: number): Promise<RefundResponse[]> => {
    const { data } = await api.get<RefundResponse[]>(`/billing/payments/${paymentId}/refunds`)
    return data
  },
}
