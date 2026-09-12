import type { Status } from '../lib/types';

const DOT_COLOR: Record<Status['phase'], string> = {
  CONNECTED: 'bg-emerald-500',
  CONNECTING: 'bg-amber-500',
  RETRYING: 'bg-amber-500',
  UNPAIRED: 'bg-red-700'
};

function humanizeSince(iso: string | null): string {
  if (!iso) return 'nunca';
  const ms = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(ms / 60000);
  if (minutes < 1) return 'hace un momento';
  if (minutes < 60) return `hace ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 48) return `hace ${hours} h`;
  return `hace ${Math.floor(hours / 24)} d`;
}

export default function StatusSection({ status }: { status: Status | null }) {
  if (!status) {
    return <div className="rounded-lg border border-border p-4 text-muted-foreground">Cargando…</div>;
  }
  return (
    <section className="rounded-lg border border-border p-4">
      <div className="flex items-center gap-2 mb-3">
        <span className={`inline-block h-3 w-3 rounded-full ${DOT_COLOR[status.phase]}`} />
        <h2 className="font-semibold text-lg">Conexión</h2>
      </div>
      <dl className="grid grid-cols-2 gap-y-1 text-sm">
        <dt className="text-muted-foreground">Estado</dt>
        <dd>{status.detail ?? status.phase}</dd>
        <dt className="text-muted-foreground">Última vez visto</dt>
        <dd>{humanizeSince(status.lastSeen)}</dd>
        <dt className="text-muted-foreground">Impresoras</dt>
        <dd>{status.printerCount}</dd>
      </dl>
    </section>
  );
}
