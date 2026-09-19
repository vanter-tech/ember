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

export interface BackupSnapshot {
  id: string | null;
  path: string | null;
  createdAt: string | null;
  sizeBytes: number;
  status: 'OK' | 'ERROR';
  errorMessage: string | null;
  appVersion: string | null;
  preRestoreSafety: boolean;
}

export interface BackupConfig {
  destDir: string;
  retention: number;
}

export interface BackupStatus {
  lastRun: BackupSnapshot | null;
  nextScheduledRun: string | null;
  destDir: string;
  defaultDestDir: string;
  retention: number;
}
