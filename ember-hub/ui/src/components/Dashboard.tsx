import { useEffect, useState } from 'react';
import { Database, HardDrive, Server, Router } from 'lucide-react';
import {
  getStatus,
  startServices,
  stopServices,
  installLicense,
  removeLicense,
  resetPortCache,
  getFirstRunCredentials,
  ackFirstRunCredentials
} from '../lib/api';
import type { FirstRunCredentials, HubStatus } from '../lib/types';
import { watchAgentShell, type AgentShellState } from '../lib/agent-events';
import { cardShellClass, IconBadge } from './Card';
import Button from './Button';
import ServiceCard from './ServiceCard';
import LicenseCard from './LicenseCard';
import BackupCard from './BackupCard';
import FirstRunCredentialsModal from './FirstRunCredentialsModal';

export default function Dashboard() {
  const [shellState, setShellState] = useState<AgentShellState>('starting');
  const [status, setStatus] = useState<HubStatus | null>(null);
  const [firstRun, setFirstRun] = useState<FirstRunCredentials | null>(null);
  const [busy, setBusy] = useState(false);
  const [restartNonce, setRestartNonce] = useState(0);

  // Re-runs (dropping the old poller/listener and starting a fresh one) whenever restartNonce
  // changes, i.e. after onRestart forces a full sidecar respawn.
  useEffect(() => watchAgentShell(setShellState), [restartNonce]);

  async function refresh() {
    try {
      setStatus(await getStatus());
    } catch {
      // transient poll failure — retried on the next tick, not fatal (spec §4)
    }
    // Only worth asking once nothing is already showing it — no point re-fetching on every 400ms
    // tick while the operator is reading/copying it (a poll landing mid-read wouldn't change
    // anything anyway: the backend only clears it on an explicit acknowledgement).
    if (!firstRun?.password) {
      try {
        const credentials = await getFirstRunCredentials();
        if (credentials.password) setFirstRun(credentials);
      } catch {
        // same as above — retried next tick
      }
    }
  }

  async function onAcknowledgeFirstRun() {
    await ackFirstRunCredentials();
    setFirstRun(null);
  }

  useEffect(() => {
    if (shellState !== 'ready') return;
    refresh();
    // 400ms rather than the original 1500ms: MinIO starts/stops fast enough that a 1.5s poll can
    // land entirely after a STARTING/STOPPING window closes, so its simulated log never gets a
    // chance to render even though the transition genuinely happened.
    const id = setInterval(refresh, 400);
    return () => clearInterval(id);
  }, [shellState]);

  if (shellState === 'starting') {
    return <main className="p-4 text-muted-foreground">Iniciando Ember Hub…</main>;
  }
  if (shellState === 'timeout' || shellState === 'crashed') {
    return (
      <main className="p-4 flex flex-col gap-3">
        <p className="text-red-700">Ember Hub no pudo iniciar.</p>
        <Button variant="primary" className="w-fit" onClick={async () => (await import('@tauri-apps/api/core')).invoke('restart_agent')}>
          Reintentar
        </Button>
      </main>
    );
  }

  const stopped = status?.server === 'STOPPED' && status?.postgres === 'STOPPED';
  const running = status?.server === 'RUNNING';

  // An invalid/missing license blocks everything before Postgres itself ever gets to run — the
  // backend still reports it as `postgres.ERROR` (it's the first real step in the boot sequence),
  // but showing "no license.key found" inside the PostgreSQL card is misleading. Move it next to
  // the License card instead whenever postgres errored out with no license on file.
  const licenseBlockedStartup = status?.postgres === 'ERROR' && status?.license.status === 'NONE';

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

  /**
   * Deleting the license alone doesn't let you try a new one cleanly: license validity is only
   * checked once, at Spring Boot startup — if services are already RUNNING (or stuck in ERROR)
   * from the old key, nothing re-validates until the process actually restarts. So this forces the
   * same full restart `onRestart` does, right after removing the files, landing on a clean "Sin
   * licencia" state ready for "Seleccionar license.key…" to install+auto-start the new one.
   */
  async function onRemoveLicense() {
    await removeLicense();
    await onRestart();
  }

  async function pickBackupFolder(): Promise<string | null> {
    const { open } = await import('@tauri-apps/plugin-dialog');
    const dir = await open({ directory: true, title: 'Elige la carpeta de respaldo' });
    return typeof dir === 'string' ? dir : null;
  }

  async function pickBackupFile(): Promise<string | null> {
    const { open } = await import('@tauri-apps/plugin-dialog');
    const file = await open({ filters: [{ name: 'Respaldo de Ember', extensions: ['zip'] }] });
    return typeof file === 'string' ? file : null;
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

  /**
   * Full manual restart of the Java sidecar. Tries a graceful `/api/stop` first — Postgres is
   * started via `pg_ctl start`, which launches the real `postgres.exe` and then exits itself, so
   * by the time we'd kill the JVM's process tree, Postgres is no longer actually a descendant of
   * it (this is why `restart_agent`'s `taskkill /T` alone can kill MinIO but not Postgres — see
   * `main.rs`'s `kill_process_tree` comment). `pg_ctl stop` is the only reliable way to stop it.
   * The graceful stop is best-effort (swallowed on failure — e.g. nothing was running to stop) —
   * `restart_agent`'s tree-kill still runs unconditionally afterward as the hard fallback for the
   * JVM itself and anything else. Resetting the cached port and re-arming `watchAgentShell` (via
   * `restartNonce`) is what lets the UI actually recover afterward instead of being stuck polling
   * a now-dead port (the control-server port is OS-assigned and changes on every respawn).
   */
  async function onRestart() {
    setBusy(true);
    try {
      await stopServices();
      // stop() runs on a background thread and returns immediately with phase=STOPPING — poll
      // until pg_ctl stop actually finishes (or give up after ~5s) instead of racing the hard
      // kill below against a Postgres shutdown still in flight.
      const deadline = Date.now() + 5000;
      while (Date.now() < deadline) {
        const s = await getStatus().catch(() => null);
        if (!s || (s.postgres !== 'STOPPING' && s.server !== 'STOPPING')) break;
        await new Promise((r) => setTimeout(r, 300));
      }
    } catch {
      // nothing was running to stop — fine, proceed to the hard restart regardless.
    }
    setStatus(null);
    setShellState('starting');
    resetPortCache();
    const { invoke } = await import('@tauri-apps/api/core');
    await invoke('restart_agent');
    setRestartNonce((n) => n + 1);
    setBusy(false);
  }

  return (
    <>
      {firstRun?.password && firstRun.email && (
        <FirstRunCredentialsModal
          email={firstRun.email}
          password={firstRun.password}
          onAcknowledge={onAcknowledgeFirstRun}
        />
      )}
      <main className="h-full p-4 flex flex-col gap-4 max-w-5xl w-full mx-auto min-h-0 overflow-auto">
      <header className={`${cardShellClass} p-4 flex items-center justify-between gap-3 flex-wrap shrink-0`}>
        <div className="flex items-center gap-3 min-w-0">
          <IconBadge icon={Router} size="lg" />
          <div className="min-w-0">
            <h1 className="text-xl font-bold leading-tight">Ember Hub</h1>
            <p className="text-sm text-muted-foreground">Panel de control local</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {stopped ? (
            <Button variant="primary" disabled={busy} onClick={onStart}>Iniciar servicios</Button>
          ) : (
            <Button variant="primary" disabled={busy || !running} onClick={onStop}>Detener</Button>
          )}
          <Button variant="primary" disabled={!running} onClick={onOpenBrowser}>Abrir en navegador</Button>
          <Button variant="primary" disabled={busy} onClick={onRestart}>Reiniciar</Button>
          <Button variant="primary" onClick={onExit}>Salir</Button>
        </div>
      </header>

      {status && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 items-start">
          <ServiceCard
            id="postgres"
            icon={Database}
            title="PostgreSQL"
            phase={status.postgres}
            error={licenseBlockedStartup ? null : status.postgresError}
          />
          <ServiceCard id="minio" icon={HardDrive} title="MinIO" phase={status.minio} error={status.minioError} />
          <ServiceCard id="server" icon={Server} title="Servidor" phase={status.server} error={status.serverError} />
          <LicenseCard license={status.license} onSelectLicense={onSelectLicense} onRemoveLicense={onRemoveLicense} />
          <div className="md:col-span-2 min-w-0">
            <BackupCard
              licenseStatus={status.license.status}
              postgresRunning={status.postgres === 'RUNNING'}
              pickFolder={pickBackupFolder}
              pickBackupFile={pickBackupFile}
              onBusyChange={setBusy}
              onRestored={refresh}
            />
          </div>
          {licenseBlockedStartup && status.postgresError && (
            <p className="md:col-span-2 rounded-2xl bg-primary text-primary-foreground text-sm font-medium px-4 py-3">
              {status.postgresError}
            </p>
          )}
        </div>
      )}
      </main>
    </>
  );
}
