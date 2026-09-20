import { invoke } from '@tauri-apps/api/core';
import type { DiscoveredPrinter, PairTarget, Status } from './types';

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

export async function getStatus(): Promise<Status> {
  return asJson<Status>(await fetch(`${await base()}/api/status`));
}

export async function getPrinters(): Promise<DiscoveredPrinter[]> {
  return asJson<DiscoveredPrinter[]>(await fetch(`${await base()}/api/printers`));
}

export async function pairWithCode(code: string, target: PairTarget): Promise<Status> {
  return asJson<Status>(await fetch(`${await base()}/api/pair`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code, target })
  }));
}

export async function pairWithApiKey(apiKey: string, target: PairTarget): Promise<Status> {
  return asJson<Status>(await fetch(`${await base()}/api/pair`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ apiKey, target })
  }));
}

export async function testPrint(queue: string): Promise<{ message: string }> {
  return asJson<{ message: string }>(await fetch(`${await base()}/api/test-print`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ queue })
  }));
}

export async function getDiagnostics(): Promise<string> {
  const res = await fetch(`${await base()}/api/diagnostics`);
  return res.text();
}

export async function getLogsDir(): Promise<string> {
  const data = await asJson<{ logsDir: string }>(await fetch(`${await base()}/api/paths`));
  return data.logsDir;
}
