export type ServicePhase = 'STOPPED' | 'STARTING' | 'RUNNING' | 'STOPPING' | 'ERROR';
export type LicenseStatus = 'OK' | 'SUSPENDED' | 'NONE';

export interface LicenseSnapshot {
  status: LicenseStatus;
  lastHeartbeatAt: string | null;
  suspendedSince: string | null;
}

export interface HubStatus {
  postgres: ServicePhase;
  postgresError: string | null;
  minio: ServicePhase;
  minioError: string | null;
  server: ServicePhase;
  serverError: string | null;
  license: LicenseSnapshot;
  serverPort: number;
}
