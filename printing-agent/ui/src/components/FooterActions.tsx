import { invoke } from '@tauri-apps/api/core';
import { getDiagnostics, getLogsDir } from '../lib/api';
import Button from './Button';

export default function FooterActions() {
  async function openLogs() {
    const dir = await getLogsDir();
    await invoke('open_folder', { path: dir });
  }

  async function copyDiagnostics() {
    const text = await getDiagnostics();
    await navigator.clipboard.writeText(text);
  }

  return (
    <div className="flex gap-2 shrink-0">
      <Button variant="primary" onClick={openLogs}>
        Abrir carpeta de logs
      </Button>
      <Button variant="primary" onClick={copyDiagnostics}>
        Copiar diagnóstico
      </Button>
    </div>
  );
}
