import { useEffect, useState } from 'react';
import { Database, HardDrive, Server, Router } from 'lucide-react';
import { getStatus, startServices, stopServices, installLicense } from '../lib/api';
import type { HubStatus } from '../lib/types';
import { cardShellClass, IconBadge } from './Card';
import Button from './Button';
import ServiceCard from './ServiceCard';
import LicenseCard from './LicenseCard';

export default function Dashboard() {
  const [status, setStatus] = useState<HubStatus | null>(null);
  const [busy, setBusy] = useState(false);

  async function refresh() {
    try {
      setStatus(await getStatus());
    } catch {
      // transient poll failure — retried on the next tick, not fatal (spec §4)
    }
  }

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, 1500);
    return () => clearInterval(id);
  }, []);

  const stopped = status?.server === 'STOPPED' && status?.postgres === 'STOPPED';
  const running = status?.server === 'RUNNING';

  async function onStart() {
    setBusy(true);
    try {
      setStatus(await startServices());
    } finally {
      setBusy(false);
    }
  }

  async function onStop() {
    setBusy(true);
    try {
      setStatus(await stopServices());
    } finally {
      setBusy(false);
    }
  }

  async function onSelectLicense() {
    const { open } = await import('@tauri-apps/plugin-dialog');
    const path = await open({ filters: [{ name: 'Licencia Ember', extensions: ['key'] }] });
    if (typeof path === 'string') {
      setStatus(await installLicense(path));
    }
  }

  async function onOpenBrowser() {
    if (!status) return;
    const { open } = await import('@tauri-apps/plugin-shell');
    await open(`http://localhost:${status.serverPort}/app/`);
  }

  async function onExit() {
    const { exit } = await import('@tauri-apps/plugin-process');
    await exit(0);
  }

  return (
    <main className="h-full p-4 flex flex-col gap-4 max-w-2xl mx-auto min-h-0 overflow-auto">
      <header className={`${cardShellClass} p-4 flex items-center justify-between gap-3 flex-wrap shrink-0`}>
        <div className="flex items-center gap-3 min-w-0">
          <IconBadge icon={Router} size="lg" />
          <div className="min-w-0">
            <h1 className="text-xl font-bold leading-tight">Ember Hub</h1>
            <p className="text-sm text-muted-foreground">Panel de control local</p>
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {stopped ? (
            <Button variant="primary" disabled={busy} onClick={onStart}>Iniciar servicios</Button>
          ) : (
            <Button variant="outline" disabled={busy || !running} onClick={onStop}>Detener</Button>
          )}
          <Button variant="outline" disabled={!running} onClick={onOpenBrowser}>Abrir en navegador</Button>
          <Button variant="outline" onClick={onExit}>Salir</Button>
        </div>
      </header>

      {status && (
        <>
          <ServiceCard id="postgres" icon={Database} title="PostgreSQL" phase={status.postgres} error={status.postgresError} />
          <ServiceCard id="minio" icon={HardDrive} title="MinIO" phase={status.minio} error={status.minioError} />
          <ServiceCard id="server" icon={Server} title="Servidor" phase={status.server} error={status.serverError} />
          <LicenseCard license={status.license} onSelectLicense={onSelectLicense} />
        </>
      )}
    </main>
  );
}
