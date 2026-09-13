import { ScrollText } from 'lucide-react';
import type { JobRecord } from '../lib/types';
import Card from './Card';

function formatTime(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleTimeString('es-NI', { hour12: false });
}

function dash(s: string | null): string {
  return s && s.trim() !== '' ? s : '—';
}

export default function JobsTable({ jobs }: { jobs: JobRecord[] }) {
  return (
    <Card icon={ScrollText} title="Registro y cola de impresiones" className="flex-1 min-h-0">
      <div className="overflow-auto flex-1 min-h-0">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-muted-foreground">
              <th className="pb-1">Hora</th>
              <th className="pb-1">Rol</th>
              <th className="pb-1">Cola</th>
              <th className="pb-1">Estado</th>
              <th className="pb-1">Error</th>
            </tr>
          </thead>
          <tbody>
            {jobs.length === 0 && (
              <tr><td colSpan={5} className="text-muted-foreground py-2">Sin trabajos recientes</td></tr>
            )}
            {jobs.map((j, i) => (
              <tr key={i} className="border-t border-border">
                <td className="py-1">{formatTime(j.at)}</td>
                <td className="py-1">{dash(j.role)}</td>
                <td className="py-1">{dash(j.queue)}</td>
                <td className="py-1">{dash(j.result)}</td>
                <td className="py-1">{dash(j.error)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}
