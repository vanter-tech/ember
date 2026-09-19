import { invoke } from '@tauri-apps/api/core';
import type { BackupConfig, BackupSnapshot, BackupStatus, HubStatus } from './types';

let cachedPort: number | null = null;

/** The sidecar's control-server port is OS-assigned (ephemeral) — a restart gets a new one, so the
 * cache must be dropped whenever the sidecar is restarted (see `Dashboard.tsx`'s onRestart). */
export function resetPortCache(): void {
  cachedPort = null;
}

async function port(): Promise<number> {
  if (cachedPort === null) {
    cachedPort = await invoke<number>('get_port');
  }
  return cachedPort;
}

async function base(): Promise<string> {
  return `http://127.0.0.1:${await port()}`;
}

/** Carries the backend's machine-readable `code` (e.g. `BACKUP_INCOMPATIBLE`) next to the message. */
export class ApiError extends Error {
  code?: string;

  constructor(message: string, code?: string) {
    super(message);
    this.code = code;
  }
}

async function asJson<T>(res: Response): Promise<T> {
  const data = await res.json();
  if (!res.ok) {
    throw new ApiError(data.error ?? `request failed (${res.status})`, data.code);
  }
  return data as T;
}

export async function getStatus(): Promise<HubStatus> {
  return asJson<HubStatus>(await fetch(`${await base()}/api/status`));
}

export async function startServices(): Promise<HubStatus> {
  return asJson<HubStatus>(await fetch(`${await base()}/api/start`, { method: 'POST' }));
}

export async function stopServices(): Promise<HubStatus> {
  return asJson<HubStatus>(await fetch(`${await base()}/api/stop`, { method: 'POST' }));
}

export async function installLicense(path: string): Promise<HubStatus> {
  return asJson<HubStatus>(await fetch(`${await base()}/api/license`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path })
  }));
}

export async function removeLicense(): Promise<HubStatus> {
  return asJson<HubStatus>(await fetch(`${await base()}/api/license`, { method: 'DELETE' }));
}

// --- backup / restore ---------------------------------------------------------------

function jsonPost(body: unknown): RequestInit {
  return { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}

export async function getBackupStatus(): Promise<BackupStatus> {
  return asJson<BackupStatus>(await fetch(`${await base()}/api/backup/status`));
}

export async function listBackups(): Promise<BackupSnapshot[]> {
  return asJson<BackupSnapshot[]>(await fetch(`${await base()}/api/backup/list`));
}

export async function setBackupConfig(destDir: string, retention?: number): Promise<BackupConfig> {
  return asJson<BackupConfig>(await fetch(`${await base()}/api/backup/config`, jsonPost({ destDir, retention })));
}

/** Resolves (HTTP 200) even when the backup failed — check `status === 'ERROR'` on the result. */
export async function backupNow(destDir?: string): Promise<BackupSnapshot> {
  return asJson<BackupSnapshot>(await fetch(`${await base()}/api/backup/now`, jsonPost({ destDir })));
}

export async function inspectBackup(path: string): Promise<BackupSnapshot> {
  return asJson<BackupSnapshot>(await fetch(`${await base()}/api/backup/inspect`, jsonPost({ path })));
}

/** Blocks until the restore finishes (can take minutes for big media folders). */
export async function restoreBackup(path: string, skipSafetySnapshot = false): Promise<HubStatus> {
  return asJson<HubStatus>(await fetch(`${await base()}/api/backup/restore`, jsonPost({ path, skipSafetySnapshot })));
}
