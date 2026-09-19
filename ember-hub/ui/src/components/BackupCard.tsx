import { useEffect, useState } from 'react';
import { Archive } from 'lucide-react';
import Card from './Card';
import Badge from './Badge';
import Button from './Button';
import Modal from './Modal';
import {
  backupNow,
  getBackupStatus,
  inspectBackup,
  listBackups,
  restoreBackup,
  setBackupConfig
} from '../lib/api';
import type { BackupSnapshot, BackupStatus } from '../lib/types';

type ModalState =
  | null
  | { kind: 'destination' }
  | { kind: 'confirmRestore'; path: string; snapshot: BackupSnapshot }
  | { kind: 'safetyFailed'; path: string; message: string };

function formatDate(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('es') : 'fecha desconocida';
}

function formatSize(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export default function BackupCard({
  pickFolder,
  pickBackupFile,
  onBusyChange,
  onRestored
}: {
  pickFolder: () => Promise<string | null>;
  pickBackupFile: () => Promise<string | null>;
  onBusyChange?: (busy: boolean) => void;
  onRestored?: () => void;
}) {
  const [status, setStatus] = useState<BackupStatus | null>(null);
  const [snapshots, setSnapshots] = useState<BackupSnapshot[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [working, setWorking] = useState<'backup' | 'restore' | null>(null);
  const [modal, setModal] = useState<ModalState>(null);

  async function load() {
    try {
      const [s, l] = await Promise.all([getBackupStatus(), listBackups()]);
      setStatus(s);
      setSnapshots(l);
    } catch {
      // transient — the next action (or a remount) reloads it
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function runBackup(destDir: string) {
    setModal(null);
    setWorking('backup');
    setError(null);
    setNotice(null);
    try {
      const snap = await backupNow(destDir);
      if (snap.status === 'ERROR') {
        setError(snap.errorMessage ?? 'El respaldo falló.');
      } else if (destDir === status?.defaultDestDir) {
        setNotice(
          'Respaldo guardado en esta máquina. Copia el archivo a una USB: si el disco de esta PC falla, este respaldo se pierde con él.'
        );
      } else {
        setNotice('Respaldo guardado.');
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setWorking(null);
      await load();
    }
  }

  async function onChooseUsb() {
    const dir = await pickFolder();
    if (dir) {
      await runBackup(dir);
    }
  }

  async function onChangeAutoFolder() {
    const dir = await pickFolder();
    if (!dir) return;
    setError(null);
    try {
      await setBackupConfig(dir);
      await load();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  async function beginRestore(path: string) {
    setError(null);
    setNotice(null);
    try {
      setModal({ kind: 'confirmRestore', path, snapshot: await inspectBackup(path) });
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  async function onRestoreFromFile() {
    const path = await pickBackupFile();
    if (path) {
      await beginRestore(path);
    }
  }

  async function confirmRestore(path: string, skipSafety: boolean) {
    setModal(null);
    setWorking('restore');
    onBusyChange?.(true);
    try {
      await restoreBackup(path, skipSafety);
      setNotice('Restauración completada. Ember Hub se está reiniciando con los datos del respaldo.');
      onRestored?.();
    } catch (e) {
      if ((e as { code?: string }).code === 'BACKUP_SAFETY_FAILED') {
        setModal({ kind: 'safetyFailed', path, message: errorMessage(e) });
      } else {
        setError(errorMessage(e));
      }
    } finally {
      setWorking(null);
      onBusyChange?.(false);
      await load();
    }
  }

  const lastFailed = status?.lastRun?.status === 'ERROR';

  return (
    <Card
      icon={Archive}
      title="Respaldos"
      badge={
        status?.lastRun ? (
          <Badge variant={lastFailed ? 'danger' : 'success'}>{lastFailed ? 'Último falló' : 'Al día'}</Badge>
        ) : (
          <Badge>Sin respaldos</Badge>
        )
      }
    >
      {status && (
        <dl className="text-sm text-muted-foreground mb-3 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
          <dt>Carpeta automática</dt>
          <dd className="text-foreground break-all">{status.destDir}</dd>
          <dt>Próximo automático</dt>
          <dd className="text-foreground">{status.nextScheduledRun ? formatDate(status.nextScheduledRun) : 'pendiente'}</dd>
        </dl>
      )}

      {lastFailed && status?.lastRun?.errorMessage && (
        <p className="rounded-2xl bg-primary text-primary-foreground text-sm font-medium px-4 py-3 mb-3">
          {status.lastRun.errorMessage}
        </p>
      )}
      {error && (
        <p className="rounded-2xl bg-primary text-primary-foreground text-sm font-medium px-4 py-3 mb-3">{error}</p>
      )}
      {notice && <p className="text-sm text-emerald-700 mb-3">{notice}</p>}
      {working === 'restore' && (
        <p className="text-sm font-medium mb-3">Restaurando… no cierres Ember Hub hasta que termine.</p>
      )}

      <div className="flex flex-wrap gap-2">
        <Button variant="primary" className="w-fit" disabled={working !== null} onClick={() => setModal({ kind: 'destination' })}>
          Respaldar ahora
        </Button>
        <Button variant="outline" className="w-fit" disabled={working !== null} onClick={onRestoreFromFile}>
          Restaurar desde archivo…
        </Button>
        <Button variant="outline" className="w-fit" disabled={working !== null} onClick={onChangeAutoFolder}>
          Cambiar carpeta automática…
        </Button>
      </div>

      {snapshots.length > 0 && (
        <ul className="mt-4 flex flex-col divide-y divide-border">
          {snapshots.map((s) => (
            <li key={s.id ?? s.path} className="py-2 flex items-center gap-3 text-sm">
              <div className="min-w-0 flex-1">
                <p className="truncate">{formatDate(s.createdAt)}</p>
                <p className="text-xs text-muted-foreground truncate">
                  {formatSize(s.sizeBytes)}
                  {s.appVersion ? ` · v${s.appVersion}` : ''}
                </p>
              </div>
              {s.preRestoreSafety && <Badge variant="warning">Antes de restaurar</Badge>}
              {s.status === 'ERROR' ? (
                <Badge variant="danger">Dañado</Badge>
              ) : (
                <Button
                  variant="outline"
                  disabled={working !== null || !s.path}
                  onClick={() => s.path && beginRestore(s.path)}
                >
                  Restaurar
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      {modal?.kind === 'destination' && (
        <Modal title="¿Dónde guardar el respaldo?" footer={<Button onClick={() => setModal(null)}>Cancelar</Button>}>
          <button
            type="button"
            className="text-left rounded-2xl border border-primary p-3 hover:bg-muted cursor-pointer"
            onClick={onChooseUsb}
          >
            <span className="flex items-center gap-2 font-medium">
              USB o disco externo <Badge variant="success">Recomendado</Badge>
            </span>
            <span className="block text-muted-foreground">
              Si el disco de esta PC falla, tus datos siguen a salvo.
            </span>
          </button>
          <button
            type="button"
            className="text-left rounded-2xl border border-border p-3 hover:bg-muted cursor-pointer"
            onClick={() => status && runBackup(status.defaultDestDir)}
          >
            <span className="font-medium">Esta máquina</span>
            <span className="block text-muted-foreground break-all">{status?.defaultDestDir}</span>
          </button>
        </Modal>
      )}

      {modal?.kind === 'confirmRestore' && (
        <Modal
          title="Restaurar respaldo"
          footer={
            <>
              <Button onClick={() => setModal(null)}>Cancelar</Button>
              <Button variant="primary" onClick={() => confirmRestore(modal.path, false)}>
                Sí, restaurar
              </Button>
            </>
          }
        >
          <p>
            Respaldo del <strong>{formatDate(modal.snapshot.createdAt)}</strong>
            {modal.snapshot.appVersion ? ` (Ember ${modal.snapshot.appVersion})` : ''}.
          </p>
          <p>
            Esto reemplaza todos los datos actuales (pedidos, cuentas, configuración e imágenes) por los del
            respaldo. Antes se guardará una copia de los datos actuales.
          </p>
        </Modal>
      )}

      {modal?.kind === 'safetyFailed' && (
        <Modal
          title="No se pudo guardar la copia previa"
          footer={
            <>
              <Button onClick={() => setModal(null)}>Cancelar</Button>
              <Button variant="primary" onClick={() => confirmRestore(modal.path, true)}>
                Restaurar sin copia previa
              </Button>
            </>
          }
        >
          <p>{modal.message}</p>
          <p>Si continúas, los datos actuales se perderán y no podrás volver atrás.</p>
        </Modal>
      )}
    </Card>
  );
}
