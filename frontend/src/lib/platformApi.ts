import axios from 'axios'
import { usePlatformAuthStore } from '@/store/platformAuthStore'

// Hand-typed: backend-types.ts is generated from `/v3/api-docs` and has never been regenerated
// since the platform/** endpoints (EMB-PC-01+) were added, so no components['schemas'] exist for
// them yet. Mirrors PlatformLoginRequest/PlatformAuthResponse/PlatformPasswordChangeRequest
// (backend/src/main/java/com/vanter/ember/platform/model/dto).
export interface PlatformLoginRequest {
  email: string
  password: string
}

export interface PlatformAuthResponse {
  token?: string
  operatorId?: string
  name?: string
  email?: string
}

export interface PlatformPasswordChangeRequest {
  currentPassword: string
  newPassword: string
}

export type HubStatus = 'NEVER' | 'ONLINE' | 'STALE' | 'OFFLINE'

export type PlatformRestaurantStatus = 'ACTIVE' | 'SUSPENDED' | 'INACTIVE' | 'DELETED'

// Mirrors PlatformRestaurantSummaryResponse (platform/model/dto).
export interface PlatformRestaurantSummary {
  id: string
  name: string
  slug: string
  plan: 'FREE' | 'STARTER' | 'PRO' | 'ENTERPRISE'
  status: PlatformRestaurantStatus
  hubStatus: HubStatus
  createdAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

// Mirrors PlatformRestaurantAdminResponse (platform/model/dto).
export interface PlatformRestaurantAdmin {
  id: string
  name: string
  email: string
}

// Mirrors PlatformRestaurantDetailResponse (platform/model/dto).
export interface PlatformRestaurantDetail {
  id: string
  name: string
  slug: string
  plan: 'FREE' | 'STARTER' | 'PRO' | 'ENTERPRISE'
  status: PlatformRestaurantStatus
  createdAt: string
  admins: PlatformRestaurantAdmin[]
  hubStatus: HubStatus
  hubActivatedAt: string | null
  lastHeartbeatAt: string | null
  lastHeartbeatIp: string | null
}

// Mirrors PlatformRestaurantCreateRequest (platform/model/dto).
export interface PlatformRestaurantCreateRequest {
  name: string
  slug: string
  adminName: string
  adminEmail: string
  adminPassword: string
}

// Mirrors PlatformAuditLogResponse (platform/model/dto).
export interface PlatformAuditLogEntry {
  id: string
  operatorId: string
  operatorEmail: string
  restaurantId: string | null
  action: string
  oldValue: string | null
  newValue: string | null
  createdAt: string
}

export const platformApi = axios.create({
  baseURL:
    window.ENV?.EMBW_API_URL ||
    import.meta.env.VITE_API_URL ||
    'http://localhost:8080/v1',
  headers: {
    'Content-Type': 'application/json',
  },
})

platformApi.interceptors.request.use(
  (config) => {
    const token = usePlatformAuthStore.getState().token

    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

platformApi.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      usePlatformAuthStore.getState().logout()
    }
    return Promise.reject(error)
  }
)

export const platformAuthService = {
  login: async (credentials: PlatformLoginRequest): Promise<PlatformAuthResponse> => {
    const { data } = await platformApi.post<PlatformAuthResponse>(
      '/platform/auth/login',
      credentials
    )
    return data
  },

  changePassword: async (details: PlatformPasswordChangeRequest): Promise<void> => {
    await platformApi.patch<void>('/platform/auth/password', details)
  },
}

export const platformRestaurantService = {
  create: async (
    request: PlatformRestaurantCreateRequest
  ): Promise<PlatformRestaurantSummary> => {
    const { data } = await platformApi.post<PlatformRestaurantSummary>(
      '/platform/restaurants',
      request
    )
    return data
  },

  getAll: async (
    page = 0,
    size = 10,
    includeDeleted = false
  ): Promise<Page<PlatformRestaurantSummary>> => {
    const { data } = await platformApi.get<Page<PlatformRestaurantSummary>>(
      '/platform/restaurants',
      { params: { page, size, includeDeleted } }
    )
    return data
  },

  deleteRestaurant: async (id: string): Promise<void> => {
    await platformApi.delete<void>(`/platform/restaurants/${id}`)
  },

  restoreRestaurant: async (id: string): Promise<PlatformRestaurantSummary> => {
    const { data } = await platformApi.post<PlatformRestaurantSummary>(
      `/platform/restaurants/${id}/restore`
    )
    return data
  },

  getById: async (id: string): Promise<PlatformRestaurantDetail> => {
    const { data } = await platformApi.get<PlatformRestaurantDetail>(
      `/platform/restaurants/${id}`
    )
    return data
  },

  updateStatus: async (
    id: string,
    status: PlatformRestaurantDetail['status']
  ): Promise<PlatformRestaurantSummary> => {
    const { data } = await platformApi.patch<PlatformRestaurantSummary>(
      `/platform/restaurants/${id}/status`,
      { status }
    )
    return data
  },

  issueHubLicense: async (id: string): Promise<string> => {
    const { data } = await platformApi.post<string>(`/platform/restaurants/${id}/hub-license`)
    return data
  },
}

export const platformAuditLogService = {
  getByRestaurant: async (
    restaurantId: string,
    page = 0,
    size = 10
  ): Promise<Page<PlatformAuditLogEntry>> => {
    const { data } = await platformApi.get<Page<PlatformAuditLogEntry>>(
      '/platform/audit-log',
      { params: { restaurantId, page, size } }
    )
    return data
  },
}
