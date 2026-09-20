import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';

vi.mock('../lib/api', () => ({
  getBackupStatus: vi.fn(),
  listBackups: vi.fn(),
  setBackupConfig: vi.fn(),
  backupNow: vi.fn(),
  inspectBackup: vi.fn(),
  restoreBackup: vi.fn()
}));

import * as api from '../lib/api';
import BackupCard from './BackupCard';
import type { LicenseStatus } from '../lib/types';

const mocked = vi.mocked(api);

const snap = {
  id: 'ember-backup-2026-09-19T10-00-00.zip',
  path: 'C:\\backups\\ember-backup-2026-09-19T10-00-00.zip',
  createdAt: '2026-09-19T10:00:00Z',
  sizeBytes: 2 * 1024 * 1024,
  status: 'OK' as const,
  errorMessage: null,
  appVersion: '0.2.6.1',
  preRestoreSafety: false
};

const status = {
  lastRun: snap,
  nextScheduledRun: '2026-09-20T10:00:00Z',
  destDir: 'C:\\backups',
  defaultDestDir: 'C:\\backups',
  retention: 7,
  progress: null
};

function apiError(message: string, code: string) {
  return Object.assign(new Error(message), { code });
}

function setup(over: { licenseStatus?: LicenseStatus; postgresRunning?: boolean } = {}) {
  const props = {
    pickFolder: vi.fn().mockResolvedValue('E:\\usb'),
    pickBackupFile: vi.fn().mockResolvedValue('D:\\subido.zip'),
    onBusyChange: vi.fn(),
    onRestored: vi.fn()
  };
  render(<BackupCard licenseStatus="OK" postgresRunning {...over} {...props} />);
  return props;
}

beforeEach(() => {
  vi.clearAllMocks();
  mocked.getBackupStatus.mockResolvedValue(status);
  mocked.listBackups.mockResolvedValue([snap]);
  mocked.backupNow.mockResolvedValue(snap);
  mocked.inspectBackup.mockResolvedValue(snap);
  mocked.restoreBackup.mockResolvedValue({} as never);
  mocked.setBackupConfig.mockResolvedValue({ destDir: 'E:\\usb', retention: 7 });
});

describe('BackupCard', () => {
  it('shows the automatic folder and lists existing backups', async () => {
    setup();

    expect(await screen.findByText('C:\\backups')).toBeTruthy();
    expect(await screen.findByRole('button', { name: 'Restaurar' })).toBeTruthy();
  });

  it('shows the last failed run as an error banner', async () => {
    mocked.getBackupStatus.mockResolvedValue({
      ...status,
      lastRun: { ...snap, status: 'ERROR', errorMessage: 'No se pudo escribir en la carpeta' }
    });
    setup();

    expect(await screen.findByText('No se pudo escribir en la carpeta')).toBeTruthy();
  });

  it('opens a modal recommending a USB drive over this machine', async () => {
    setup();
    fireEvent.click(await screen.findByRole('button', { name: 'Respaldar ahora' }));

    expect(await screen.findByText('Recomendado')).toBeTruthy();
    expect(screen.getByRole('button', { name: /USB o disco externo/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Esta máquina/ })).toBeTruthy();
  });

  it('backs up to this machine and reminds the owner to copy it to a USB', async () => {
    setup();
    await screen.findByText('C:\\backups'); // status (incl. defaultDestDir) must be loaded first
    fireEvent.click(await screen.findByRole('button', { name: 'Respaldar ahora' }));
    fireEvent.click(await screen.findByRole('button', { name: /Esta máquina/ }));

    await waitFor(() => expect(mocked.backupNow).toHaveBeenCalledWith('C:\\backups'));
    expect(await screen.findByText(/Copia el archivo a una USB/)).toBeTruthy();
  });

  it('backs up to the folder picked for the USB option', async () => {
    const props = setup();
    fireEvent.click(await screen.findByRole('button', { name: 'Respaldar ahora' }));
    fireEvent.click(await screen.findByRole('button', { name: /USB o disco externo/ }));

    await waitFor(() => expect(mocked.backupNow).toHaveBeenCalledWith('E:\\usb'));
    expect(props.pickFolder).toHaveBeenCalled();
  });

  it('does nothing when the folder picker is cancelled', async () => {
    const props = setup();
    props.pickFolder.mockResolvedValue(null);
    fireEvent.click(await screen.findByRole('button', { name: 'Respaldar ahora' }));
    fireEvent.click(await screen.findByRole('button', { name: /USB o disco externo/ }));

    await waitFor(() => expect(props.pickFolder).toHaveBeenCalled());
    expect(mocked.backupNow).not.toHaveBeenCalled();
  });

  it('shows the backend error when a backup fails', async () => {
    mocked.backupNow.mockResolvedValue({ ...snap, status: 'ERROR', errorMessage: 'USB desconectada' });
    setup();
    await screen.findByText('C:\\backups');
    fireEvent.click(await screen.findByRole('button', { name: 'Respaldar ahora' }));
    fireEvent.click(await screen.findByRole('button', { name: /Esta máquina/ }));

    expect(await screen.findByText('USB desconectada')).toBeTruthy();
  });

  it('restores an uploaded file only after an explicit confirmation', async () => {
    const props = setup();
    fireEvent.click(await screen.findByRole('button', { name: 'Restaurar desde archivo…' }));

    expect(await screen.findByText(/reemplaza todos los datos actuales/i)).toBeTruthy();
    expect(mocked.inspectBackup).toHaveBeenCalledWith('D:\\subido.zip');
    expect(mocked.restoreBackup).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Sí, restaurar' }));

    await waitFor(() => expect(mocked.restoreBackup).toHaveBeenCalledWith('D:\\subido.zip', false));
    await waitFor(() => expect(props.onRestored).toHaveBeenCalled());
    expect(props.onBusyChange).toHaveBeenCalledWith(true);
    expect(props.onBusyChange).toHaveBeenLastCalledWith(false);
  });

  it('restores from a row of the list', async () => {
    setup();
    fireEvent.click(await screen.findByRole('button', { name: 'Restaurar' }));

    await waitFor(() => expect(mocked.inspectBackup).toHaveBeenCalledWith(snap.path));
    expect(await screen.findByRole('button', { name: 'Sí, restaurar' })).toBeTruthy();
  });

  it('shows the incompatibility message and never opens the confirmation', async () => {
    mocked.inspectBackup.mockRejectedValue(
      apiError('Este respaldo es de una versión más reciente de Ember (9.0.0).', 'BACKUP_INCOMPATIBLE')
    );
    setup();
    fireEvent.click(await screen.findByRole('button', { name: 'Restaurar desde archivo…' }));

    expect(await screen.findByText(/versión más reciente/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Sí, restaurar' })).toBeNull();
  });

  it('offers to continue without a safety copy when it cannot be made', async () => {
    mocked.restoreBackup.mockRejectedValueOnce(
      apiError('No se pudo crear la copia de seguridad previa', 'BACKUP_SAFETY_FAILED')
    );
    setup();
    fireEvent.click(await screen.findByRole('button', { name: 'Restaurar desde archivo…' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Sí, restaurar' }));

    fireEvent.click(await screen.findByRole('button', { name: 'Restaurar sin copia previa' }));

    await waitFor(() => expect(mocked.restoreBackup).toHaveBeenLastCalledWith('D:\\subido.zip', true));
  });

  it('changes the automatic backup folder', async () => {
    setup();
    fireEvent.click(await screen.findByRole('button', { name: 'Cambiar carpeta automática…' }));

    await waitFor(() => expect(mocked.setBackupConfig).toHaveBeenCalledWith('E:\\usb'));
  });

  it('disables backing up and restoring until a license is installed, and says why', async () => {
    setup({ licenseStatus: 'NONE', postgresRunning: false });

    expect(await screen.findByText(/Instala la licencia/)).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Respaldar ahora' }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: 'Restaurar desde archivo…' }) as HTMLButtonElement).disabled).toBe(true);
    expect((await screen.findByRole('button', { name: 'Restaurar' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('with a license but the services stopped, only backing up is blocked', async () => {
    setup({ licenseStatus: 'OK', postgresRunning: false });

    expect(await screen.findByText(/Inicia los servicios/)).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Respaldar ahora' }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: 'Restaurar desde archivo…' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('shows a filled progress bar with the current phase while a backup runs', async () => {
    mocked.getBackupStatus.mockResolvedValueOnce(status);
    mocked.getBackupStatus.mockResolvedValue({
      ...status,
      progress: { operation: 'BACKUP', phase: 'Comprimiendo archivos…', percent: 40 }
    });
    mocked.backupNow.mockReturnValue(new Promise(() => {})); // never finishes: the run is "in progress"
    setup();
    await screen.findByText('C:\\backups');
    fireEvent.click(await screen.findByRole('button', { name: 'Respaldar ahora' }));
    fireEvent.click(await screen.findByRole('button', { name: /Esta máquina/ }));

    const bar = await screen.findByRole('progressbar');
    expect(bar.getAttribute('aria-valuenow')).toBe('40');
    expect(screen.getByText(/Comprimiendo archivos…/)).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Respaldar ahora' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('shows an indeterminate bar (no value) for a phase with unknown length', async () => {
    mocked.getBackupStatus.mockResolvedValueOnce(status);
    mocked.getBackupStatus.mockResolvedValue({
      ...status,
      progress: { operation: 'BACKUP', phase: 'Exportando la base de datos…', percent: null }
    });
    mocked.backupNow.mockReturnValue(new Promise(() => {}));
    setup();
    await screen.findByText('C:\\backups');
    fireEvent.click(await screen.findByRole('button', { name: 'Respaldar ahora' }));
    fireEvent.click(await screen.findByRole('button', { name: /Esta máquina/ }));

    const bar = await screen.findByRole('progressbar');
    expect(bar.hasAttribute('aria-valuenow')).toBe(false);
    expect(screen.getByText(/Exportando la base de datos…/)).toBeTruthy();
  });
});
