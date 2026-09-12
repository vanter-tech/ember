import { invoke } from '@tauri-apps/api/core';
import { getDiagnostics, getLogsDir } from '../lib/api';

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
    <div className="flex gap-2">
      <button className="border border-border rounded-md px-3 py-1.5 text-sm" onClick={openLogs}>
        Abrir carpeta de logs
      </button>
      <button className="border border-border rounded-md px-3 py-1.5 text-sm" onClick={copyDiagnostics}>
        Copiar diagnóstico
      </button>
    </div>
  );
}
