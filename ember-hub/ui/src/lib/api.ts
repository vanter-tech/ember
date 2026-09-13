import { invoke } from '@tauri-apps/api/core';
import type { HubStatus } from './types';

let cachedPort: number | null = null;

async function port(): Promise<number> {
  if (cachedPort === null) {
    cachedPort = await invoke<number>('get_port');
  }
  return cachedPort;
}

async function base(): Promise<string> {
  return `http://127.0.0.1:${await port()}`;
}

async function asJson<T>(res: Response): Promise<T> {
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error ?? `request failed (${res.status})`);
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
