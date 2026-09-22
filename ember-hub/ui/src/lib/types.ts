export type ServicePhase = 'STOPPED' | 'STARTING' | 'RUNNING' | 'STOPPING' | 'ERROR';
export type LicenseStatus = 'OK' | 'SUSPENDED' | 'MIGRATED' | 'NONE';

export interface LicenseSnapshot {
  status: LicenseStatus;
  lastHeartbeatAt: string | null;
  suspendedSince: string | null;
  migratedSince?: string | null;
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
  /** non-null only while a backup or restore is running */
  progress: BackupProgress | null;
}

export interface BackupProgress {
  operation: 'BACKUP' | 'RESTORE';
  phase: string;
  /** null = the phase has no measurable length (shown as an animated bar) */
  percent: number | null;
}

/** F-15: the Hub's own generated first-run admin password, held in memory only (never written to
 *  disk) — both fields are null once there's nothing pending (never activated yet, or already
 *  acknowledged via `ackFirstRunCredentials`). */
export interface FirstRunCredentials {
  email: string | null;
  password: string | null;
}
